# Beeline - an opinionated fediverse metaclient. 

archival notice

i likely won't continue working on this - beeline was a fun experiment, and i'd like to leave the code up if anyone can learn from it

i do not think a fully native client is worth the extra time and effort to maintain - not just that web is easier, but i genuinely think the end result is consistently better and actually matches my expectations. i'm currently rewriting keystone one last time into react and expo, so i can maintain one client that targets both mobile and web. i'm basically just doing what bluesky did, because their client is decent, it's a sensible decision and i'm fine with the "trade-offs". keystone won't be 1:1 with beeline, but i'm in the middle of bringing over most of the niceties. 

go check out [keystone](https://github.com/estyseesghosts/keystone) 

## readme

this is beeline - a metaclient for the fediverse, built in kotlin and compose for android. made from the start with modern tooling and interfaces, meant to look and feel even better to use than the big guys' apps. 

![screenshots](https://files.catbox.moe/fggzs7.webp)

why call it a metaclient? what even is that? 
it was made from the start to work with multiple applications, that work in different ways, to make them feel like a cohesive part of a social ecosystem. 

to abstract away the instances and the software they run to make the fediverse feel like one thing you can interact with and observe, whichever way you want to. 

this is nowhere near the end. i would still call this app an unfinished prototype. however, at least for my own personal usage on mastodon and misskey, i now no longer open web clients on my phone and only use beeline. YMMV! 

this app supports 
- most mastodon api compatible servers 
- (primarily tested on mastodon!)
- (pleroma and pixelfed in testing as of v0.3.2! file issues!)
- most misskey api compatible servers 
- (tested primarily on sharkey, catodon in testing as of v0.3.2, vanilla misskey untested - YMMV. please file issues!)
- writing threaded posts
- uploading images and videos
- write alt text 
- increase font size
- change your font 
- (OpenDyslexic and Inter are bundled, can force device serif or device sans)
- a couple of colour theme palettes i picked out 
- (the app defaults to using your wallpaper colours, material style)
- scroll home, local, social, bubble and federated timelines 
- scroll the recommended timeline on [dvd's sharkey fork](https://github.com/ddvd233/sharkey)
- show any of your feeds/an antenna/a hashtag/a group of hashtags as a staggered grid of photos 
- edit, delete, and schedule your posts, and post local-only, where your server supports each one
- leave an emoji reaction, including custom server emoji 
- choose to make all of your replies unlisted
- choose to translate every post by default 
- RTL layout 
- (text editing works a bit funny, and i'm not sure if it's bugged! if you write in an RTL language, feedback would be appreciated!)
- very basic and barebones translations that have not been native-speaker-checked
- for german, spanish, portuguese, french, hindi, japanese, korean, mandarin, cantonese, russian, indonesian and (modern standard) arabic. 
- so it might not be every string, but hopefully most of the important ones!

this app does not support 
- internal misskey chatting api (it's local only anyways)
- pixelfed threaded replies or DMs (though i might be interested if their implementations are good)

native misskey chats are not implemented and are currently not planned - private message posts from mastodon are reimplemented here for misskey. native misskey chats are bound only to the local instance, and are not globally interoperable. private messages are NOT secure or encrypted at this point in time, in any way. you should only use them for sharing memes. they are called 'direct messages' in the app, and there is a ui disclaimer that they are not secure. (need to make that more prominent too)

this project is open source and licenced under the gpl v3. see license.md. please take inspiration from it. the fediverse is already something beautiful. it feels like the last safe space away from corporate social media. reddit, twitter, instagram and tiktok, facebook, using them anymore just feels like rotting. i think we as a society deserve better than that. 

## manifesto - readme starts below

my fedi pet peeve for a long time has been how disjointed and disconnected it feels to use different services across an interconnected network 

i dont necessarily think my approach is the correct one either. at least for myself, what i want out of this is an app i can use to both browse and post my photos, and an app i can use to keep up with the text post side of things. different apps can, and should, do different things - but on a network like this i think they work better together. 

using mastodon for this purpose feels awkward, using pixelfed feels too clunky and inflexible, and while misskey checks all the boxes, i don't like the UX of the other mobile apps. i have strong opinions about interfaces, and especially in an era where we are used to being trapped in algorithmic hell, i want to build a gate out. one that feels familiar and less overwhelming, without the crazy map-your-whole-life-to-sell-you-Algorithm. 

i might talk shit but i truly mean love to the mastodon and pixelfed projects - of which i have philosophical differences with, but have been built by hand over the course of years. i made this shit with codex in under a week. i would also like to shoutout the maintainer of moshidon and the developers of pixelix - the aforementioned projects are all why i'm making this right now.
