package dev.telum.launcher

/** Local presentation only. A profile does not promise playback or published recommendations. */
data class AppProfile(
    val name: String,
    val packages: List<String>,
    val category: String,
    val tagline: String,
    val primaryHue: Float,
    val secondaryHue: Float,
    val saturation: Float = 0.8f,
)

/** Exact package aliases, independent of sign-in, subscriptions and network access. */
object AppSupport {
    fun isMusic(pkg: String): Boolean = profile(pkg)?.category in setOf("Music", "Music and radio")

    val profiles: List<AppProfile> = listOf(
        AppProfile("Netflix", listOf("com.netflix.ninja", "com.netflix.mediaclient"), "Streaming", "Films, series and documentaries", 355f, 12f),
        AppProfile("Prime Video", listOf("com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient"), "Streaming", "Movies, series and live sport", 202f, 225f),
        AppProfile("Disney+", listOf("com.disney.disneyplus"), "Streaming", "Movies, series and Disney originals", 184f, 224f),
        AppProfile("Apple TV", listOf("com.apple.atve.androidtv.appletv"), "Streaming", "Apple originals, movies and series", 220f, 250f, 0.12f),
        AppProfile("Paramount+", listOf("com.cbs.ca", "com.cbs.app"), "Streaming", "Movies, series and live entertainment", 219f, 240f),
        AppProfile("Crave", listOf("ca.bellmedia.cravetv"), "Streaming", "Movies, series and Canadian entertainment", 215f, 190f),
        AppProfile("CBC Gem", listOf("ca.cbc.android.cbctv"), "Live and on demand", "Canadian shows, news and live TV", 351f, 324f),
        AppProfile("Global TV", listOf("com.shawmedia.smglobal"), "Live and on demand", "Shows, news and live television", 355f, 20f),
        AppProfile("Tubi", listOf("com.tubitv"), "Streaming", "Free movies and television", 282f, 322f),
        AppProfile("Pluto TV", listOf("tv.pluto.android"), "Live and on demand", "Free channels, movies and shows", 42f, 24f),
        AppProfile("Plex", listOf("com.plexapp.android"), "Media library", "Your library, movies and television", 37f, 18f),
        AppProfile("Stremio", listOf("com.stremio.one"), "Streaming", "Movies and series from your add-ons", 265f, 226f),
        AppProfile("YouTube", listOf("com.google.android.youtube.tv"), "Video", "Videos, music and live streams", 358f, 332f),
        AppProfile("SmartTube", listOf("org.smarttube.beta", "org.smarttube.stable", "app.smarttube.fdroid"), "Video", "Videos and music on your TV", 356f, 20f),
        AppProfile("Spotify", listOf("com.spotify.tv.android", "com.spotify.music"), "Music", "Music and podcasts", 140f, 170f),
        AppProfile("YouTube Music", listOf("com.google.android.youtube.tvmusic"), "Music", "Songs, performances and playlists", 353f, 320f),
        AppProfile("Amazon Music", listOf("com.amazon.music.tv"), "Music", "Music, podcasts and playlists", 184f, 266f),
        AppProfile("TuneIn", listOf("tunein.player"), "Music and radio", "Radio, news, music and sport", 184f, 211f),
        AppProfile("Crunchyroll", listOf("com.crunchyroll.crunchyroid"), "Streaming", "Anime series and films", 26f, 5f),
        AppProfile("Twitch", listOf("tv.twitch.android.app"), "Live video", "Live streams and communities", 263f, 294f),
        AppProfile("DAZN", listOf("com.dazn"), "Sports", "Live sport and highlights", 56f, 37f),
        AppProfile("Sportsnet", listOf("com.rogers.sportsnet.sportsnet"), "Sports", "Sports, live coverage and highlights", 215f, 354f),
        AppProfile("HBO Max", listOf("com.wbd.stream"), "Streaming", "Movies, series and HBO originals", 243f, 273f),
        AppProfile("Hulu", listOf("com.hulu.livingroomplus"), "Streaming", "Series, movies and television", 146f, 174f),
        AppProfile("Peacock", listOf("com.peacocktv.peacockandroid"), "Streaming", "Movies, series and live entertainment", 202f, 275f),
        AppProfile("Kodi", listOf("org.xbmc.kodi"), "Media player", "Your media and add-ons", 198f, 227f),
        AppProfile("VLC", listOf("org.videolan.vlc"), "Media player", "Videos, audio and network streams", 27f, 8f),
        AppProfile("Downloader", listOf("com.esaba.downloader"), "Utility", "Download files and install apps", 28f, 10f),
        AppProfile("Plex Media Server", listOf("com.plexapp.mediaserver.smb"), "Server", "The media server running on this SHIELD", 37f, 20f),
        AppProfile("Google Play Store", listOf("com.android.vending"), "Store", "Install and update apps", 150f, 205f),
        AppProfile("Live Channels", listOf("com.google.android.tv"), "Live TV", "Channels from your antenna and apps", 205f, 175f),
        AppProfile("Google TV", listOf("com.google.android.videos"), "Streaming", "Your movies and television", 205f, 262f),
        AppProfile("Settings", listOf("com.android.tv.settings"), "System", "Display, sound, network and remotes", 220f, 238f, 0.25f),
    )

    private val byPackage = profiles.flatMap { profile -> profile.packages.map { it to profile } }.toMap()

    fun profile(packageName: String): AppProfile? = byPackage[packageName]

    fun tagline(packageName: String, label: String): String = profile(packageName)?.tagline ?: "Open $label"

    /** Stable across runs and devices, including Int.MIN_VALUE hashes. */
    fun fallbackHue(packageName: String): Float = ((packageName.hashCode().toLong() and 0xFFFFFFFFL) % 360).toFloat()
}
