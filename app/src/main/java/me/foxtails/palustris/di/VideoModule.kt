package me.foxtails.palustris.di

import android.content.Context
import android.os.Build
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import me.foxtails.palustris.data.media.DeviceVideoCapabilities
import me.foxtails.palustris.data.media.DeviceVideoIdentity
import me.foxtails.palustris.data.media.Media3Mp4Transcoder
import me.foxtails.palustris.data.media.MediaMetadataVideoProbe
import me.foxtails.palustris.data.media.PreferencesVideoBenchmarkStore
import me.foxtails.palustris.data.media.UnavailableWebmEncoder
import me.foxtails.palustris.data.media.VideoPreparer
import me.foxtails.palustris.data.media.WebmRejections

/** Wires video upload preparation. Slice 7 replaces the WebM encoder with the FFmpeg bridge. */
@Module
@InstallIn(SingletonComponent::class)
object VideoModule {
    @Provides
    @Singleton
    fun provideWebmRejections(): WebmRejections = WebmRejections()

    @Provides
    @Singleton
    fun provideVideoPreparer(@ApplicationContext context: Context, rejections: WebmRejections): VideoPreparer {
        val encoder = UnavailableWebmEncoder
        val device = DeviceVideoCapabilities(
            identity = DeviceVideoIdentity(Build.MODEL, Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS.firstOrNull()),
            encoder = encoder,
            store = PreferencesVideoBenchmarkStore(context),
        )
        return VideoPreparer(
            workDirectory = File(context.cacheDir, "video-prep"),
            probe = MediaMetadataVideoProbe(),
            mp4 = Media3Mp4Transcoder(context),
            webm = encoder,
            device = device,
            rejections = rejections,
        )
    }
}
