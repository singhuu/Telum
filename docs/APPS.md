# Apps and backgrounds

Telum shows the enabled Android TV apps on the device. It also shows installed apps that have a normal Android launch activity. The profiles below add descriptions and local background colors. They do not limit which apps Telum shows. Games, tools, and new services can show without a profile. A phone app can need a mouse or touch input after it opens.

With **App colours** as the background, each app has its own background color. For a known package, Telum uses its local color profile, also before the artwork loads. For other packages, Telum takes colors from the installed banner or icon. If Android cannot read them, Telum draws a tile with the app name. Its color comes from the package name and is always the same. Fixed backgrounds use the selected theme.

The tile uses the banner or icon of the installed app. Telum does not contain logos of streaming services, film posters, or screenshots. Telum makes the profile colors locally. They need no subscription, no API key, no TV listings permission, and no network request. A profile does not show a relation with the publisher of the app.

## Netflix without a subscription

The TV package `com.netflix.ninja` and the mobile package `com.netflix.mediaclient` get the Netflix description and a red background. This operates without sign-in or a subscription, because Telum reads no Netflix account data to draw it. When you open Netflix, the installed app takes control. Netflix decides if you must sign in or have a membership to watch its content. [Netflix TV listing](https://play.google.com/store/apps/details?id=com.netflix.ninja), [mobile package listing](https://play.google.com/store/apps/details?id=com.netflix.mediaclient).

When Netflix shares no readable rows, Telum shows no Netflix recommendations or programme art. A background color is a local display. A film poster or a continue-watching card is content that an app supplies. The recommendations inside Netflix do not show that Netflix shares them with other home screens.

## Package profiles

This list is a selection of services. It is not a ranking. The registry contains 33 profiles and 39 exact package names. The sources were examined on 2026-10-18. A package name does not prove that the Play Store offers the app for a given TV, country, or account. Some packages are mobile apps. They are useful only if they are installed and can start. A package with a similar name does not get the profile of a different app.

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

The Canadian services are CBC Gem, Crave, Global TV, Sportsnet, and the Canadian package of Paramount+. Crave lists NVIDIA SHIELD and Android TV or Google TV as supported devices. CBC Gem streams only in Canada, with a free account level and an optional Premium level. Global TV has free content, and content that needs a sign-in from a TV provider. These are the conditions of the publishers. Telum does not change them. [Crave supported devices](https://plus.crave.ca/en/faq/supported-devices/crave-device-compatibility/), [CBC Gem listing](https://play.google.com/store/apps/details?id=ca.cbc.android.cbctv), [Global TV listing](https://play.google.com/store/apps/details?id=com.shawmedia.smglobal).

Hulu, Peacock, HBO Max, and other services outside your region also have profiles. Their colors show if the package is installed. A profile does not promise that the service is available in your region, or that playback operates. Look in the Play Store on your TV device to find which apps you can install.

## Music apps

The music controls operate with each app that has an Android media session. The dim music display and the artist photos show only for these music profiles: Spotify, YouTube Music, Amazon Music, and TuneIn. Video apps and unknown apps do not start the dim display.

## Recommendations and continue watching

**Live Activities** is off until you turn it on. With **Live Activities** on and the `android.permission.READ_TV_LISTINGS` permission, Telum reads the Watch Next rows and the preview channels of Android TV. Each app decides if it shares rows, and what each row contains. The documentation from Google describes how apps share rows. The store page of an app, or its own continue-watching list, does not show that it shares rows. [Watch Next publication](https://developer.android.com/training/tv/discovery/watch-next-add-programs), [home-screen channels](https://developer.android.com/training/tv/discovery/recommendations-channel).

Telum puts the rows into groups by the app that shares them. It keeps the channel names of the app. **Automatic** selects a row that is not empty and has a playback position and a duration. You can select a different row of the app, for example recommendations. You can also turn off the row of the app. Watch Next contains the titles to continue and the next episodes from the last 30 days. Telum does not show a recommendation without progress as viewing history. `Content.kt` contains this behavior. These rows use no catalogue API of a service and no account data.

The posters come from the artwork address in the shared row. HTTPS artwork uses the network and the local cache of Telum. Readable `content:` and `android.resource:` addresses use local resources. Other address types, unavailable content providers, and failed downloads can give a card without artwork. When you select a card, Telum uses the action that the app shared. The app then examines the sign-in, the subscription, the region, and the title.

New app versions, settings, accounts, and regions can share different rows. A missing row does not prove an error in Telum. It also does not prove that a subscription makes a row show. TV listings access lets Telum read the shared rows. It cannot make an app share more content. Notification access for music controls is a different setting. It does not give access to streaming recommendations. Artist photos are also a different feature. They are not part of the color profiles.

## What the checks prove

The behavior check uses the same profile lookup as the app. It makes sure that the 39 package names are unique. It makes sure that the Netflix and regional packages give the correct profile. It also makes sure that unknown packages always get the same fallback. Run `./gradlew :app:behaviorChecks`, or `gradlew.bat :app:behaviorChecks` on Windows.

That check does not draw Android backgrounds. It does not prove that apps start, that subscriptions operate, that apps share rows, or that the remote and playback operate on a device. These need an installed build and tests on devices.
