# Beeline - an opinionated fediverse metaclient. 

## readme

this is beeline - a metaclient for the fediverse, built in kotlin and compose for android. made from the start with modern tooling and interfaces, meant to look and feel even better to use than the big guys' apps. 

![screenshots](https://files.catbox.moe/fggzs7.webp)

why call it a metaclient? what even is that? 
it was made from the start to work with multiple applications, that work in different ways, to make them feel like a cohesive part of a social ecosystem. 

to abstract away the instances and the software they run to make the fediverse feel like one thing you can interact with and observe, whichever way you want to. 

this is nowhere near the end. i would still call this app an unfinished prototype, but basic reading and posting work. i plan to add pixelfed support soon. the ui has quirks which i still have to document (mostly by design, although i think i squashed the bugs...)

this app supports 
- the mastodon api, and compatible servers 
- which apparently includes pixelfed! 
- ^ which has not yet been tested. 
- support for glitch extensions/pleroma/akkoma is also not yet tested
- but i'd love to have them working too (:
- the misskey api, and compatible servers.
- i personally use (and tested this app with) sharkey.
- if there are bugs with vanilla misskey, open an issue! 

native misskey chats are not implemented and are currently not planned - private message posts from mastodon are reimplemented here for misskey. native misskey chats are bound only to the local instance, and are not globally interoperable. private messages are NOT secure or encrypted at this point in time, in any way. you should only use them for sharing memes. they are called 'direct messages' in the app, and there is a ui disclaimer that they are not secure. (need to make that more prominent too)

this project is open source and licenced under the gpl v3. see license.md. please take inspiration from it. the fediverse is already something beautiful. it feels like the last safe space away from corporate social media. reddit, twitter, instagram and tiktok, facebook, using them anymore just feels like rotting. i think we as a society deserve better than that. 

## manifesto - readme starts below

my fedi pet peeve for a long time has been how disjointed and disconnected it feels to use different services across an interconnected network 

i dont necessarily think my approach is the correct one either. at least for myself, what i want out of this is an app i can use to both browse and post my photos, and an app i can use to keep up with the text post side of things. different apps can, and should, do different things - but on a network like this i think they work better together. 

using mastodon for this purpose feels awkward, using pixelfed feels too clunky and inflexible, and while misskey checks all the boxes, i don't like the UX of the other mobile apps. i have strong opinions about interfaces, and especially in an era where we are used to being trapped in algorithmic hell, i want to build a gate out. one that feels familiar and less overwhelming, without the crazy map-your-whole-life-to-sell-you-Algorithm. 

i might talk shit but i truly mean love to the mastodon and pixelfed projects - of which i have philosophical differences with, but have been built by hand over the course of years. i made this shit with codex in under a week. i would also like to shoutout the maintainer of moshidon and the developers of pixelix - the aforementioned projects are all why i'm making this right now. 

## so what does it do?

as of now?

- load your misskey and mastodon timelines & profiles & notifications
- folds hashtags and links into nice little bubbles
- federated direct messaging
- load a grid of photos from your timeline, your lists, your followed hashtags, or your misskey antennae
- post text, photos, and videos, with content warnings, polls, replies, quotes, and threads of posts
- edit, delete, and schedule your posts, and post local-only, where your server supports each one
- like, repost, react with emoji (including custom server emoji on misskey), and bookmark
- search posts, people, and hashtags, and see trends
- see mastodon lists and followed hashtags, and misskey lists and antennae, as chips on home and photo grid
- group several hashtags into one chip
- translate posts, automatically or by hand, where your server offers translation (off by default)
- block, mute, and report, with moderation lists for accounts and hashtags
- use the interface in english, arabic (right-to-left), chinese, hindi, japanese, korean, and more, with a choice of fonts and accent palettes


## todo

the photo grid works by lists, hashtags, and antennae, but not yet by users. 

misskey drive: you can upload photos and videos to it, but you cannot browse it yet. 

uploads to mastodon servers are not yet verified on a live server. 

video uploads as webm need an arm64 phone. other phones upload h.264 mp4. 

content warnings are not fully implemented. 

search for posts on mastodon needs a search backend on the server. 

possibly a hell of a lot more. i haven't fully thoroughly tested this.
