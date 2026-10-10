// JNI bridge from me.foxtails.palustris.data.media.FfmpegNative to the LGPL FFmpeg build in tools/ffmpeg.
//
// Responsibility: probe a file, transcode it to VP9 + Opus in WebM, and time a short VP9 encode.
// Lifetime: every call owns its FFmpeg state and frees it before returning; nothing is kept between calls.
// On ABIs without the FFmpeg libraries (BEELINE_FFMPEG undefined) every entry point reports "unavailable".
#include <jni.h>

#include <algorithm>
#include <chrono>
#include <cmath>
#include <stdexcept>
#include <string>
#include <thread>

#ifdef BEELINE_FFMPEG
extern "C" {
#include <libavcodec/avcodec.h>
#include <libavfilter/avfilter.h>
#include <libavfilter/buffersink.h>
#include <libavfilter/buffersrc.h>
#include <libavformat/avformat.h>
#include <libavutil/display.h>
#include <libavutil/opt.h>
#include <libavutil/channel_layout.h>
#include <libavutil/imgutils.h>
}
#endif

namespace {

constexpr const char *kRuntimeException = "java/lang/RuntimeException";

void throwJava(JNIEnv *env, const std::string &message) {
    jclass cls = env->FindClass(kRuntimeException);
    if (cls != nullptr) env->ThrowNew(cls, message.c_str());
}

#ifdef BEELINE_FFMPEG

constexpr int kAudioSampleRate = 48000;
constexpr int kVideoGop = 150;
constexpr int kMaxFps = 30;
// libvpx realtime speed. Higher is faster and lower quality; 5 to 9 are the useful realtime values.
constexpr int kVp9CpuUsed = 7;

std::string errorText(int code) {
    char buffer[AV_ERROR_MAX_STRING_SIZE] = {0};
    av_strerror(code, buffer, sizeof(buffer));
    return buffer;
}

void check(int code, const char *what) {
    if (code < 0) throw std::runtime_error(std::string(what) + ": " + errorText(code));
}

/** Reads the cancel flag and reports progress through the Kotlin objects that the caller passed. */
class JobCallbacks {
public:
    JobCallbacks(JNIEnv *env, jobject progress, jobject cancel) : env_(env), progress_(progress), cancel_(cancel) {
        if (progress_ != nullptr) {
            jclass cls = env_->GetObjectClass(progress_);
            progressId_ = env_->GetMethodID(cls, "onProgress", "(F)V");
        }
        if (cancel_ != nullptr) {
            jclass cls = env_->GetObjectClass(cancel_);
            getId_ = env_->GetMethodID(cls, "get", "()Z");
        }
    }

    bool cancelled() const {
        return cancel_ != nullptr && getId_ != nullptr && env_->CallBooleanMethod(cancel_, getId_) == JNI_TRUE;
    }

    void report(float fraction) const {
        if (progress_ == nullptr || progressId_ == nullptr) return;
        env_->CallVoidMethod(progress_, progressId_, fraction);
        // A callback that throws must not leave an exception pending while native code keeps calling JNI.
        if (env_->ExceptionCheck()) env_->ExceptionClear();
    }

private:
    JNIEnv *env_;
    jobject progress_;
    jobject cancel_;
    jmethodID progressId_ = nullptr;
    jmethodID getId_ = nullptr;
};

/** Frees every FFmpeg object a transcode creates, in the right order, on success, failure, or cancel. */
struct Pipeline {
    AVFormatContext *input = nullptr;
    AVFormatContext *output = nullptr;
    AVCodecContext *videoDec = nullptr;
    AVCodecContext *audioDec = nullptr;
    AVCodecContext *videoEnc = nullptr;
    AVCodecContext *audioEnc = nullptr;
    AVFilterGraph *videoGraph = nullptr;
    AVFilterGraph *audioGraph = nullptr;
    AVFilterContext *videoSrc = nullptr;
    AVFilterContext *videoSink = nullptr;
    AVFilterContext *audioSrc = nullptr;
    AVFilterContext *audioSink = nullptr;
    AVStream *videoOut = nullptr;
    AVStream *audioOut = nullptr;
    AVPacket *packet = nullptr;
    AVFrame *decoded = nullptr;
    AVFrame *filtered = nullptr;
    AVPacket *encoded = nullptr;
    bool headerWritten = false;
    // Wall time spent inside the video decoder. The benchmark subtracts it so it times scaling and VP9 encoding only.
    double videoDecodeSeconds = 0;

    ~Pipeline() {
        av_packet_free(&packet);
        av_packet_free(&encoded);
        av_frame_free(&decoded);
        av_frame_free(&filtered);
        avfilter_graph_free(&videoGraph);
        avfilter_graph_free(&audioGraph);
        avcodec_free_context(&videoDec);
        avcodec_free_context(&audioDec);
        avcodec_free_context(&videoEnc);
        avcodec_free_context(&audioEnc);
        if (output != nullptr) {
            if (!(output->oformat->flags & AVFMT_NOFILE) && output->pb != nullptr) avio_closep(&output->pb);
            avformat_free_context(output);
        }
        avformat_close_input(&input);
    }
};

AVRational outputFrameRate(const AVStream *stream) {
    AVRational rate = stream->avg_frame_rate;
    if (rate.num <= 0 || rate.den <= 0) rate = stream->r_frame_rate;
    if (rate.num <= 0 || rate.den <= 0) rate = {kMaxFps, 1};
    if (av_cmp_q(rate, AVRational{kMaxFps, 1}) > 0) rate = {kMaxFps, 1};
    return rate;
}

/** Degrees, clockwise, that the picture must be turned to display upright: 0, 90, 180, or 270. */
int displayRotation(const AVStream *stream) {
    const AVPacketSideData *side = av_packet_side_data_get(
        stream->codecpar->coded_side_data, stream->codecpar->nb_coded_side_data, AV_PKT_DATA_DISPLAYMATRIX);
    if (side == nullptr || side->size < 9 * sizeof(int32_t)) return 0;
    double theta = -std::round(av_display_rotation_get(reinterpret_cast<const int32_t *>(side->data)));
    theta -= 360 * std::floor(theta / 360 + 0.9 / 360);
    if (std::fabs(theta - 90) < 1) return 90;
    if (std::fabs(theta - 180) < 1) return 180;
    if (std::fabs(theta - 270) < 1) return 270;
    return 0;
}

void parseGraph(AVFilterGraph *graph, AVFilterContext *src, AVFilterContext *sink, const std::string &description) {
    AVFilterInOut *outputs = avfilter_inout_alloc();
    AVFilterInOut *inputs = avfilter_inout_alloc();
    if (outputs == nullptr || inputs == nullptr) {
        avfilter_inout_free(&outputs);
        avfilter_inout_free(&inputs);
        throw std::runtime_error("out of memory");
    }
    outputs->name = av_strdup("in");
    outputs->filter_ctx = src;
    outputs->pad_idx = 0;
    outputs->next = nullptr;
    inputs->name = av_strdup("out");
    inputs->filter_ctx = sink;
    inputs->pad_idx = 0;
    inputs->next = nullptr;
    int code = avfilter_graph_parse_ptr(graph, description.c_str(), &inputs, &outputs, nullptr);
    avfilter_inout_free(&inputs);
    avfilter_inout_free(&outputs);
    check(code, "filter graph");
    check(avfilter_graph_config(graph, nullptr), "filter config");
}

AVCodecContext *openDecoder(const AVStream *stream) {
    const AVCodec *codec = avcodec_find_decoder(stream->codecpar->codec_id);
    if (codec == nullptr) throw std::runtime_error("no decoder for this stream");
    AVCodecContext *ctx = avcodec_alloc_context3(codec);
    if (ctx == nullptr) throw std::runtime_error("out of memory");
    int code = avcodec_parameters_to_context(ctx, stream->codecpar);
    if (code >= 0) {
        ctx->pkt_timebase = stream->time_base;
        ctx->thread_count = 0;
        code = avcodec_open2(ctx, codec, nullptr);
    }
    if (code < 0) {
        avcodec_free_context(&ctx);
        check(code, "open decoder");
    }
    return ctx;
}

/**
 * [exactShortSide] scales the shorter edge to exactly [maxHeight], up or down. The benchmark uses it so every device
 * runs the same pixel count whatever clip it received. Otherwise [maxHeight] only caps the output height.
 */
void setUpVideo(Pipeline &p, const AVStream *in, int maxHeight, int bitrate, bool exactShortSide) {
    p.videoDec = openDecoder(in);
    const AVCodecParameters *par = in->codecpar;
    if (par->width <= 0 || par->height <= 0 || par->format < 0) throw std::runtime_error("unknown video format");

    char args[256];
    AVRational sar = par->sample_aspect_ratio.num > 0 ? par->sample_aspect_ratio : AVRational{1, 1};
    snprintf(args, sizeof(args), "video_size=%dx%d:pix_fmt=%d:time_base=%d/%d:pixel_aspect=%d/%d", par->width,
             par->height, par->format, in->time_base.num, in->time_base.den, sar.num, sar.den);
    p.videoGraph = avfilter_graph_alloc();
    if (p.videoGraph == nullptr) throw std::runtime_error("out of memory");
    check(avfilter_graph_create_filter(&p.videoSrc, avfilter_get_by_name("buffer"), "in", args, nullptr, p.videoGraph),
          "video source");
    check(avfilter_graph_create_filter(&p.videoSink, avfilter_get_by_name("buffersink"), "out", nullptr, nullptr,
                                       p.videoGraph),
          "video sink");

    AVRational fps = outputFrameRate(in);
    std::string chain;
    switch (displayRotation(in)) {
        case 90: chain = "transpose=clock,"; break;
        case 180: chain = "hflip,vflip,"; break;
        case 270: chain = "transpose=cclock,"; break;
        default: break;
    }
    // Width follows the aspect ratio and stays even; the height never grows past the source or maxHeight.
    const std::string limit = std::to_string(maxHeight);
    if (exactShortSide) {
        chain += "scale=w='if(lt(iw,ih)," + limit + ",-2)':h='if(lt(iw,ih),-2," + limit + ")':flags=bicubic";
    } else {
        chain += "scale=w=-2:h='trunc(min(ih," + limit + ")/2)*2':flags=bicubic";
    }
    chain += ",fps=" + std::to_string(fps.num) + "/" + std::to_string(fps.den) + ",format=yuv420p";
    parseGraph(p.videoGraph, p.videoSrc, p.videoSink, chain);

    const AVCodec *codec = avcodec_find_encoder_by_name("libvpx-vp9");
    if (codec == nullptr) throw std::runtime_error("VP9 encoder missing");
    p.videoEnc = avcodec_alloc_context3(codec);
    if (p.videoEnc == nullptr) throw std::runtime_error("out of memory");
    AVCodecContext *enc = p.videoEnc;
    enc->width = av_buffersink_get_w(p.videoSink);
    enc->height = av_buffersink_get_h(p.videoSink);
    enc->pix_fmt = AV_PIX_FMT_YUV420P;
    enc->time_base = av_buffersink_get_time_base(p.videoSink);
    enc->framerate = fps;
    enc->gop_size = kVideoGop;
    enc->thread_count = std::clamp(static_cast<int>(std::thread::hardware_concurrency()), 2, 8);
    int64_t target = bitrate > 0 ? bitrate
                                 : static_cast<int64_t>(static_cast<double>(enc->width) * enc->height *
                                                        av_q2d(fps) * 0.05);
    enc->bit_rate = std::clamp<int64_t>(target, 250000, 8000000);
    av_opt_set(enc->priv_data, "deadline", "realtime", 0);
    av_opt_set_int(enc->priv_data, "cpu-used", kVp9CpuUsed, 0);
    av_opt_set_int(enc->priv_data, "row-mt", 1, 0);
    av_opt_set_int(enc->priv_data, "tile-columns", 2, 0);
    av_opt_set_int(enc->priv_data, "lag-in-frames", 0, 0);
    av_opt_set_int(enc->priv_data, "error-resilient", 0, 0);
}

void setUpAudio(Pipeline &p, const AVStream *in) {
    p.audioDec = openDecoder(in);
    const AVCodecParameters *par = in->codecpar;
    if (par->sample_rate <= 0 || par->format < 0) throw std::runtime_error("unknown audio format");

    AVChannelLayout layout{};
    if (par->ch_layout.nb_channels > 0 && par->ch_layout.order != AV_CHANNEL_ORDER_UNSPEC) {
        av_channel_layout_copy(&layout, &par->ch_layout);
    } else {
        av_channel_layout_default(&layout, std::max(1, par->ch_layout.nb_channels));
    }
    char layoutText[64] = {0};
    av_channel_layout_describe(&layout, layoutText, sizeof(layoutText));
    av_channel_layout_uninit(&layout);

    char args[256];
    // Decoded frames carry timestamps in the stream's time base, which is not always 1/sample_rate.
    snprintf(args, sizeof(args), "time_base=%d/%d:sample_rate=%d:sample_fmt=%s:channel_layout=%s", in->time_base.num,
             in->time_base.den, par->sample_rate, av_get_sample_fmt_name(static_cast<AVSampleFormat>(par->format)),
             layoutText);
    p.audioGraph = avfilter_graph_alloc();
    if (p.audioGraph == nullptr) throw std::runtime_error("out of memory");
    check(avfilter_graph_create_filter(&p.audioSrc, avfilter_get_by_name("abuffer"), "in", args, nullptr, p.audioGraph),
          "audio source");
    check(avfilter_graph_create_filter(&p.audioSink, avfilter_get_by_name("abuffersink"), "out", nullptr, nullptr,
                                       p.audioGraph),
          "audio sink");
    std::string chain = "aresample=" + std::to_string(kAudioSampleRate) +
                        ",aformat=sample_fmts=s16:channel_layouts=stereo|mono";
    parseGraph(p.audioGraph, p.audioSrc, p.audioSink, chain);

    const AVCodec *codec = avcodec_find_encoder_by_name("libopus");
    if (codec == nullptr) throw std::runtime_error("Opus encoder missing");
    p.audioEnc = avcodec_alloc_context3(codec);
    if (p.audioEnc == nullptr) throw std::runtime_error("out of memory");
    AVCodecContext *enc = p.audioEnc;
    enc->sample_rate = kAudioSampleRate;
    enc->sample_fmt = AV_SAMPLE_FMT_S16;
    check(av_buffersink_get_ch_layout(p.audioSink, &enc->ch_layout), "audio layout");
    enc->time_base = av_buffersink_get_time_base(p.audioSink);
    enc->bit_rate = enc->ch_layout.nb_channels > 1 ? 96000 : 64000;
}

/** Sends a frame (or null to flush) to an encoder and writes every packet it returns. */
void encodeAndWrite(Pipeline &p, AVCodecContext *enc, AVStream *out, AVFrame *frame) {
    check(avcodec_send_frame(enc, frame), "send frame");
    for (;;) {
        int code = avcodec_receive_packet(enc, p.encoded);
        if (code == AVERROR(EAGAIN) || code == AVERROR_EOF) return;
        check(code, "receive packet");
        av_packet_rescale_ts(p.encoded, enc->time_base, out->time_base);
        p.encoded->stream_index = out->index;
        check(av_interleaved_write_frame(p.output, p.encoded), "write packet");
    }
}

/** Moves every frame the sink has ready through its encoder. Returns the last video time in seconds. */
double drainSink(Pipeline &p, AVFilterContext *sink, AVCodecContext *enc, AVStream *out, bool video, double last) {
    for (;;) {
        int code = av_buffersink_get_frame(sink, p.filtered);
        if (code == AVERROR(EAGAIN) || code == AVERROR_EOF) return last;
        check(code, "filter output");
        if (video) {
            p.filtered->pict_type = AV_PICTURE_TYPE_NONE;
            if (p.filtered->pts != AV_NOPTS_VALUE) last = p.filtered->pts * av_q2d(enc->time_base);
        }
        encodeAndWrite(p, enc, out, p.filtered);
        av_frame_unref(p.filtered);
    }
}

/** Feeds one packet (or a flush when packet is null) through a decoder and on to its encoder. */
double decodePacket(Pipeline &p, AVCodecContext *dec, AVFilterContext *src, AVFilterContext *sink,
                    AVCodecContext *enc, AVStream *out, bool video, const AVPacket *packet, double last) {
    const auto sendStart = std::chrono::steady_clock::now();
    int code = avcodec_send_packet(dec, packet);
    if (video) p.videoDecodeSeconds += std::chrono::duration<double>(std::chrono::steady_clock::now() - sendStart).count();
    if (code < 0 && code != AVERROR_EOF) {
        // A damaged packet is skipped; a decoder that rejects everything fails at the end with no output.
        return last;
    }
    for (;;) {
        const auto receiveStart = std::chrono::steady_clock::now();
        code = avcodec_receive_frame(dec, p.decoded);
        if (video) p.videoDecodeSeconds += std::chrono::duration<double>(std::chrono::steady_clock::now() - receiveStart).count();
        if (code == AVERROR(EAGAIN) || code == AVERROR_EOF) return last;
        check(code, "decode");
        p.decoded->pts = p.decoded->best_effort_timestamp;
        check(av_buffersrc_add_frame_flags(src, p.decoded, AV_BUFFERSRC_FLAG_KEEP_REF), "filter input");
        av_frame_unref(p.decoded);
        last = drainSink(p, sink, enc, out, video, last);
    }
}

struct RunResult {
    bool completed;
    double mediaSeconds;
    double decodeSeconds;
};

/**
 * Runs one transcode. [outputPath] null discards the output (the benchmark). [limitSeconds] above zero stops
 * after that much input. [bitrate] at or below zero picks a bitrate from the picture size.
 */
RunResult run(const JobCallbacks &job, const char *inputPath, const char *outputPath, int maxHeight, int bitrate,
              double limitSeconds) {
    Pipeline p;
    check(avformat_open_input(&p.input, inputPath, nullptr, nullptr), "open input");
    check(avformat_find_stream_info(p.input, nullptr), "stream info");
    int videoIndex = av_find_best_stream(p.input, AVMEDIA_TYPE_VIDEO, -1, -1, nullptr, 0);
    if (videoIndex < 0) throw std::runtime_error("no video stream");
    int audioIndex = av_find_best_stream(p.input, AVMEDIA_TYPE_AUDIO, -1, -1, nullptr, 0);
    AVStream *videoIn = p.input->streams[videoIndex];
    AVStream *audioIn = audioIndex >= 0 ? p.input->streams[audioIndex] : nullptr;

    setUpVideo(p, videoIn, maxHeight, bitrate, /* exactShortSide = */ outputPath == nullptr);
    bool withAudio = false;
    if (audioIn != nullptr) {
        try {
            setUpAudio(p, audioIn);
            withAudio = true;
        } catch (const std::exception &) {
            // A track that cannot be decoded or re-encoded is dropped; the video still uploads.
            avfilter_graph_free(&p.audioGraph);
            avcodec_free_context(&p.audioDec);
            avcodec_free_context(&p.audioEnc);
            p.audioSrc = p.audioSink = nullptr;
        }
    }

    check(avformat_alloc_output_context2(&p.output, nullptr, outputPath != nullptr ? "webm" : "null", outputPath),
          "output");
    p.output->flags |= AVFMT_FLAG_BITEXACT;
    p.output->avoid_negative_ts = AVFMT_AVOID_NEG_TS_MAKE_ZERO;
    if (p.output->oformat->flags & AVFMT_GLOBALHEADER) {
        p.videoEnc->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;
        if (withAudio) p.audioEnc->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;
    }
    check(avcodec_open2(p.videoEnc, p.videoEnc->codec, nullptr), "open VP9 encoder");
    p.videoOut = avformat_new_stream(p.output, nullptr);
    if (p.videoOut == nullptr) throw std::runtime_error("out of memory");
    check(avcodec_parameters_from_context(p.videoOut->codecpar, p.videoEnc), "video parameters");
    p.videoOut->time_base = p.videoEnc->time_base;
    if (withAudio) {
        check(avcodec_open2(p.audioEnc, p.audioEnc->codec, nullptr), "open Opus encoder");
        av_buffersink_set_frame_size(p.audioSink, p.audioEnc->frame_size);
        p.audioOut = avformat_new_stream(p.output, nullptr);
        if (p.audioOut == nullptr) throw std::runtime_error("out of memory");
        check(avcodec_parameters_from_context(p.audioOut->codecpar, p.audioEnc), "audio parameters");
        p.audioOut->time_base = p.audioEnc->time_base;
    }
    if (outputPath != nullptr) check(avio_open(&p.output->pb, outputPath, AVIO_FLAG_WRITE), "open output file");
    check(avformat_write_header(p.output, nullptr), "write header");
    p.headerWritten = true;

    p.packet = av_packet_alloc();
    p.encoded = av_packet_alloc();
    p.decoded = av_frame_alloc();
    p.filtered = av_frame_alloc();
    if (p.packet == nullptr || p.encoded == nullptr || p.decoded == nullptr || p.filtered == nullptr) {
        throw std::runtime_error("out of memory");
    }

    double durationSeconds = p.input->duration > 0 ? static_cast<double>(p.input->duration) / AV_TIME_BASE : 0;
    double startSeconds = p.input->start_time != AV_NOPTS_VALUE ? static_cast<double>(p.input->start_time) / AV_TIME_BASE : 0;
    double lastVideo = 0;
    float lastReported = -1;
    bool completed = true;
    while (true) {
        if (job.cancelled()) {
            completed = false;
            break;
        }
        int code = av_read_frame(p.input, p.packet);
        if (code == AVERROR_EOF) break;
        check(code, "read");
        int index = p.packet->stream_index;
        if (index == videoIndex) {
            if (limitSeconds > 0 && p.packet->pts != AV_NOPTS_VALUE &&
                p.packet->pts * av_q2d(videoIn->time_base) - startSeconds >= limitSeconds) {
                av_packet_unref(p.packet);
                break;
            }
            lastVideo = decodePacket(p, p.videoDec, p.videoSrc, p.videoSink, p.videoEnc, p.videoOut, true, p.packet,
                                     lastVideo);
            float fraction = durationSeconds > 0 ? static_cast<float>(std::min(1.0, lastVideo / durationSeconds)) : 0;
            if (fraction - lastReported >= 0.01f) {
                job.report(fraction);
                lastReported = fraction;
            }
        } else if (withAudio && index == audioIndex) {
            decodePacket(p, p.audioDec, p.audioSrc, p.audioSink, p.audioEnc, p.audioOut, false, p.packet, 0);
        }
        av_packet_unref(p.packet);
    }

    if (completed) {
        lastVideo = decodePacket(p, p.videoDec, p.videoSrc, p.videoSink, p.videoEnc, p.videoOut, true, nullptr, lastVideo);
        check(av_buffersrc_add_frame_flags(p.videoSrc, nullptr, 0), "flush video filter");
        lastVideo = drainSink(p, p.videoSink, p.videoEnc, p.videoOut, true, lastVideo);
        encodeAndWrite(p, p.videoEnc, p.videoOut, nullptr);
        if (withAudio) {
            decodePacket(p, p.audioDec, p.audioSrc, p.audioSink, p.audioEnc, p.audioOut, false, nullptr, 0);
            check(av_buffersrc_add_frame_flags(p.audioSrc, nullptr, 0), "flush audio filter");
            drainSink(p, p.audioSink, p.audioEnc, p.audioOut, false, 0);
            encodeAndWrite(p, p.audioEnc, p.audioOut, nullptr);
        }
        check(av_write_trailer(p.output), "write trailer");
        job.report(1.0f);
    }
    return {completed, lastVideo, p.videoDecodeSeconds};
}

#endif  // BEELINE_FFMPEG

}  // namespace

extern "C" {

JNIEXPORT jstring JNICALL Java_me_foxtails_palustris_data_media_FfmpegNative_buildId(JNIEnv *env, jclass) {
#ifdef BEELINE_FFMPEG
    return env->NewStringUTF(BEELINE_FFMPEG_BUILD_ID);
#else
    return nullptr;
#endif
}

/** Returns [durationMs, width, height] or null when the file has no readable video stream. */
JNIEXPORT jlongArray JNICALL Java_me_foxtails_palustris_data_media_FfmpegNative_probe(JNIEnv *env, jclass,
                                                                                      jstring path) {
#ifdef BEELINE_FFMPEG
    const char *chars = env->GetStringUTFChars(path, nullptr);
    AVFormatContext *ic = nullptr;
    jlongArray result = nullptr;
    if (chars != nullptr && avformat_open_input(&ic, chars, nullptr, nullptr) >= 0) {
        if (avformat_find_stream_info(ic, nullptr) >= 0) {
            int index = av_find_best_stream(ic, AVMEDIA_TYPE_VIDEO, -1, -1, nullptr, 0);
            if (index >= 0) {
                jlong values[3] = {ic->duration > 0 ? ic->duration / 1000 : -1, ic->streams[index]->codecpar->width,
                                   ic->streams[index]->codecpar->height};
                result = env->NewLongArray(3);
                if (result != nullptr) env->SetLongArrayRegion(result, 0, 3, values);
            }
        }
        avformat_close_input(&ic);
    }
    if (chars != nullptr) env->ReleaseStringUTFChars(path, chars);
    return result;
#else
    return nullptr;
#endif
}

/** Returns true when the file was written, false when the cancel flag stopped it. Throws on failure. */
JNIEXPORT jboolean JNICALL Java_me_foxtails_palustris_data_media_FfmpegNative_transcodeWebm(
    JNIEnv *env, jclass, jstring input, jstring output, jint maxHeight, jint videoBitrate, jobject progress,
    jobject cancel) {
#ifdef BEELINE_FFMPEG
    const char *in = env->GetStringUTFChars(input, nullptr);
    const char *out = env->GetStringUTFChars(output, nullptr);
    jboolean completed = JNI_FALSE;
    try {
        JobCallbacks job(env, progress, cancel);
        completed = run(job, in, out, maxHeight, videoBitrate, 0).completed ? JNI_TRUE : JNI_FALSE;
    } catch (const std::exception &error) {
        throwJava(env, error.what());
    }
    env->ReleaseStringUTFChars(input, in);
    env->ReleaseStringUTFChars(output, out);
    return completed;
#else
    throwJava(env, "FFmpeg is not available on this ABI");
    return JNI_FALSE;
#endif
}

/** Encodes up to 3 seconds of the file to nowhere and returns media seconds divided by wall seconds. */
JNIEXPORT jdouble JNICALL Java_me_foxtails_palustris_data_media_FfmpegNative_benchmarkVp9(JNIEnv *env, jclass,
                                                                                          jstring fixture,
                                                                                          jint maxHeight) {
#ifdef BEELINE_FFMPEG
    const char *in = env->GetStringUTFChars(fixture, nullptr);
    double factor = 0;
    try {
        JobCallbacks job(env, nullptr, nullptr);
        auto started = std::chrono::steady_clock::now();
        RunResult result = run(job, in, nullptr, maxHeight, 0, 3.0);
        double wall = std::chrono::duration<double>(std::chrono::steady_clock::now() - started).count();
        // Decode time is not part of the VP9 speed: the policy keeps heavy sources out of the WebM path instead.
        const double encodeWall = wall - result.decodeSeconds;
        if (encodeWall > 0) factor = result.mediaSeconds / encodeWall;
    } catch (const std::exception &error) {
        throwJava(env, error.what());
    }
    env->ReleaseStringUTFChars(fixture, in);
    return factor;
#else
    throwJava(env, "FFmpeg is not available on this ABI");
    return 0;
#endif
}

}  // extern "C"
