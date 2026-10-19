# Apps and backgrounds

Telum discovers enabled Android TV launch activities on the device. It also lists user-installed apps with an ordinary Android launch activity. The package profiles below add descriptions and local background colours; they are not an installation allowlist. Games, utilities and new services can appear without a profile. A phone app may still need a mouse or touch input after it opens.

With the dynamic wallpaper selected, every app has an ambient background. For a known package, Telum uses its local colour profile, including before artwork finishes loading. For other packages, it samples the installed banner or icon. If Android cannot read the resources, Telum draws a named tile and uses a deterministic colour derived from the package name. Fixed wallpapers use the selected theme instead.

The tile uses the banner or icon supplied by the installed app. Telum does not bundle streaming-service logos, film posters or screenshots. The registry's gradients are generated locally and need no subscription, API key, TV-listings permission or network request. A profile does not claim affiliation with the app's publisher.

## Netflix without a subscription

The TV package `com.netflix.ninja` and the mobile alias `com.netflix.mediaclient` both receive the Netflix description and a red ambient palette. This works without signing in or subscribing because Telum reads no Netflix account information to draw it. Opening Netflix passes control to the installed app. Netflix decides whether sign-in or a membership is required to watch its content. [Netflix TV listing](https://play.google.com/store/apps/details?id=com.netflix.ninja), [mobile package listing](https://play.google.com/store/apps/details?id=com.netflix.mediaclient).

Telum does not supply Netflix recommendations or programme art when Netflix publishes no readable rows. A background colour is local presentation; a film poster or a continue-watching card is separate content supplied by an app. Netflix's recommendations inside Netflix are not evidence of recommendations being available to another launcher.

## Package profiles

The following is a representative service list, not a Play Store ranking. The registry contains 33 profiles and 39 exact package aliases. Sources were checked on 2026-10-18. Package identity does not prove Play Store availability for a particular TV, country or account. Some aliases are mobile apps and are useful only if installed and launchable. A similarly named package does not inherit another app's branding.

| App | Exact packages | Local palette | Package source |
| --- | --- | --- | --- |
| Netflix | `com.netflix.ninja`, `com.netflix.mediaclient` | Red | [TV](https://play.google.com/store/apps/details?id=com.netflix.ninja), [mobile](https://play.google.com/store/apps/details?id=com.netflix.mediaclient) |
| Prime Video | `com.amazon.amazonvideo.livingroom`, `com.amazon.avod.thirdpartyclient` | Blue | [TV](https://play.google.com/store/apps/details?id=com.amazon.amazonvideo.livingroom), [mobile](https://play.google.com/store/apps/details?id=com.amazon.avod.thirdpartyclient) |
| Disney+ | `com.disney.disneyplus` | Teal and blue | [Play listing](https://play.google.com/store/apps/details?id=com.disney.disneyplus) |
| Apple TV | `com.apple.atve.androidtv.appletv` | Graphite | [TV listing](https://play.google.com/store/apps/details?id=com.apple.atve.androidtv.appletv) |
| Paramount+ | `com.cbs.ca`, `com.cbs.app` | Blue | [Canada package](https://play.google.com/store/apps/details?id=com.cbs.ca), [other package](https://play.google.com/store/apps/details?id=com.cbs.app) |
| Crave | `ca.bellmedia.cravetv` | Blue and cyan | [Play listing](https://play.google.com/store/apps/details?id=ca.bellmedia.cravetv) |
| CBC Gem | `ca.cbc.android.cbctv` | Red and pink | [Play listing](https://play.google.com/store/apps/details?id=ca.cbc.android.cbctv) |
| Global TV | `com.shawmedia.smglobal` | Red and orange | [Play listing](https://play.google.com/store/apps/details?id=com.shawmedia.smglobal) |
| Tubi | `com.tubitv` | Purple and pink | [Play listing](https://play.google.com/store/apps/details?id=com.tubitv) |
| Pluto TV | `tv.pluto.android` | Gold and orange | [Play listing](https://play.google.com/store/apps/details?id=tv.pluto.android) |
| Plex | `com.plexapp.android` | Amber | [Play listing](https://play.google.com/store/apps/details?id=com.plexapp.android) |
| Stremio | `com.stremio.one` | Violet and blue | [Play listing](https://play.google.com/store/apps/details?id=com.stremio.one) |
| YouTube | `com.google.android.youtube.tv` | Red and pink | [TV listing](https://play.google.com/store/apps/details?id=com.google.android.youtube.tv) |
| SmartTube | `org.smarttube.beta`, `org.smarttube.stable`, `app.smarttube.fdroid` | Red and orange | [Publisher's build flavours](https://github.com/yuliskov/SmartTube/blob/master/smarttubetv/build.gradle). SmartTube is distributed outside Google Play. |
| Spotify | `com.spotify.tv.android`, `com.spotify.music` | Green and teal | [TV](https://play.google.com/store/apps/details?id=com.spotify.tv.android), [mobile](https://play.google.com/store/apps/details?id=com.spotify.music) |
| YouTube Music | `com.google.android.youtube.tvmusic` | Red and pink | [TV listing](https://play.google.com/store/apps/details?id=com.google.android.youtube.tvmusic) |
| Amazon Music | `com.amazon.music.tv` | Cyan and violet | [TV listing](https://play.google.com/store/apps/details?id=com.amazon.music.tv) |
| TuneIn | `tunein.player` | Teal and blue | [Play listing](https://play.google.com/store/apps/details?id=tunein.player) |
| Crunchyroll | `com.crunchyroll.crunchyroid` | Orange and red | [Play listing](https://play.google.com/store/apps/details?id=com.crunchyroll.crunchyroid) |
| Twitch | `tv.twitch.android.app` | Violet | [Play listing](https://play.google.com/store/apps/details?id=tv.twitch.android.app) |
| DAZN | `com.dazn` | Yellow and amber | [Play listing](https://play.google.com/store/apps/details?id=com.dazn) |
| Sportsnet | `com.rogers.sportsnet.sportsnet` | Blue and red | [Play listing](https://play.google.com/store/apps/details?id=com.rogers.sportsnet.sportsnet) |
| HBO Max | `com.wbd.stream` | Indigo and violet | [Play listing](https://play.google.com/store/apps/details?id=com.wbd.stream) |
| Hulu | `com.hulu.livingroomplus` | Green and teal | [TV listing](https://play.google.com/store/apps/details?id=com.hulu.livingroomplus) |
| Peacock | `com.peacocktv.peacockandroid` | Blue and violet | [Play listing](https://play.google.com/store/apps/details?id=com.peacocktv.peacockandroid) |
| Kodi | `org.xbmc.kodi` | Cyan and blue | [Play listing](https://play.google.com/store/apps/details?id=org.xbmc.kodi) |
| VLC | `org.videolan.vlc` | Orange and red | [Play listing](https://play.google.com/store/apps/details?id=org.videolan.vlc) |
| Downloader | `com.esaba.downloader` | Orange | [Play listing](https://play.google.com/store/apps/details?id=com.esaba.downloader) |
| Plex Media Server | `com.plexapp.mediaserver.smb` | Amber | Existing SHIELD package integration; device-specific |
| Google Play Store | `com.android.vending` | Green and blue | Existing Android system integration |
| Live Channels | `com.google.android.tv` | Blue and teal | Existing Android TV system integration |
| Google TV | `com.google.android.videos` | Blue and violet | Existing SHIELD package integration |
| Settings | `com.android.tv.settings` | Slate | Existing Android TV system integration; a built-in Settings shortcut covers devices without a matching tile |

Canadian services are represented by CBC Gem, Crave, Global TV, Sportsnet and the Paramount+ Canada package. Crave explicitly lists NVIDIA SHIELD and Android/Google TV support. CBC Gem describes Canada-only streaming, a free account tier and an optional Premium tier. Global TV describes both free content and content requiring a participating TV-provider login. These are the publishers' service conditions, not launcher entitlements. [Crave supported devices](https://plus.crave.ca/en/faq/supported-devices/crave-device-compatibility/), [CBC Gem listing](https://play.google.com/store/apps/details?id=ca.cbc.android.cbctv), [Global TV listing](https://play.google.com/store/apps/details?id=com.shawmedia.smglobal).

Profiles for Hulu, Peacock, HBO Max and other services outside a user's region still provide local styling if those packages are installed. Their inclusion does not promise Canadian distribution, a usable account or playback. Confirm regional installation availability on the TV itself.

## Recommendations and continue watching

"Show what you're watching" is off by default. When enabled and granted `android.permission.READ_TV_LISTINGS`, Telum reads Android TV's Watch Next and preview-channel tables. Apps choose whether to publish rows and what each row contains. Google's documentation describes these publication mechanisms. An app's store page or its own in-app continue-watching list does not establish that it publishes them. [Watch Next publication](https://developer.android.com/training/tv/discovery/watch-next-add-programs), [home-screen channels](https://developer.android.com/training/tv/discovery/recommendations-channel).

The current implementation groups rows by their publishing package and keeps provider channel names. Automatic selection chooses a nonempty row with a positive playback position and duration. A user can instead choose another published row, such as recommendations, or turn off that app's row. Watch Next includes continue and next-episode entries engaged within the last 30 days. A recommendation without progress is not described as proof of viewing history. This behavior is implemented in `Content.kt`; no per-service catalogue API or account credentials are used for these rows.

Posters come from the artwork URI in the published row. HTTPS artwork uses the network and Telum's local cache; readable `content:` and `android.resource:` URIs can use local resources. Unsupported URI schemes, unavailable content providers and failed downloads can leave a card without artwork. Selecting a card uses the app's published intent. The destination app checks sign-in, subscriptions, region and title availability.

Future app versions, settings, accounts and regions may publish different rows. An absent row does not prove a broken launcher or prove that subscribing would make a row appear. Granting TV-listings access enables reads of available rows; it cannot make a streaming app publish missing content. Notification access for music controls is a separate setting and does not unlock streaming recommendations. Optional artist photos are also a separate feature and are not part of the local app-background profiles.

## Verification boundary

The JVM check executes the production profile lookup and verifies all 39 aliases are unique, Netflix and regional aliases resolve correctly, and unknown packages retain a deterministic fallback. Run `./gradlew :app:behaviorChecks` or `gradlew.bat :app:behaviorChecks`.

That check does not render Android backgrounds or prove launches, subscriptions, provider publication, remote navigation or playback on a device. Those require an installed build and a separate device matrix. No app installation, login, subscription purchase or SHIELD package change was performed for this compatibility update.
