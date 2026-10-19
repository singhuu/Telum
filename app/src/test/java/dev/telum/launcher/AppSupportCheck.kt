package dev.telum.launcher

/** Run with AppSupport.kt on a regular Kotlin/JVM compiler. No Android runtime or accounts. */
fun main() {
    check(AppSupport.isMusic("com.spotify.tv.android"))
    check(AppSupport.isMusic("com.spotify.music"))
    check(AppSupport.isMusic("com.amazon.music.tv"))
    check(!AppSupport.isMusic("com.plexapp.android"))
    check(!AppSupport.isMusic("org.smarttube.stable"))
    check(!AppSupport.isMusic("com.stremio.one"))
    check(!AppSupport.isMusic("com.example.player"))
    val packages = AppSupport.profiles.flatMap { it.packages }
    check(packages.size == packages.toSet().size) { "An exact package alias belongs to more than one app" }
    check(AppSupport.profile("com.netflix.ninja")?.name == "Netflix")
    check(AppSupport.profile("com.netflix.mediaclient") == AppSupport.profile("com.netflix.ninja"))
    check(AppSupport.profile("com.netflix.ninja")?.primaryHue == 355f)
    check(AppSupport.profile("com.cbs.ca")?.name == "Paramount+")
    check(AppSupport.profile("com.cbs.ca") == AppSupport.profile("com.cbs.app"))
    check(AppSupport.profile("org.smarttube.stable") == AppSupport.profile("org.smarttube.beta"))
    check(AppSupport.profile("app.smarttube.fdroid") == AppSupport.profile("org.smarttube.beta"))
    check(AppSupport.profile("ca.cbc.android.cbctv")?.category == "Live and on demand")
    check(AppSupport.profile("ca.bellmedia.cravetv")?.name == "Crave")

    // Package-looking substrings must never borrow another app's branding.
    for (unknown in listOf("com.netflix.ninja.unrelated", "example.netflix", "com.example.newtvapp", "")) {
        check(AppSupport.profile(unknown) == null)
        check(AppSupport.tagline(unknown, "My TV app") == "Open My TV app")
        check(AppSupport.fallbackHue(unknown) in 0f..359f)
    }
    check(AppSupport.fallbackHue("com.example.newtvapp") == 208f)
    check(AppSupport.fallbackHue("polygenelubricants") in 0f..359f) // Int.MIN_VALUE hash.
    for (profile in AppSupport.profiles) {
        check(profile.name.isNotBlank() && profile.category.isNotBlank() && profile.tagline.isNotBlank())
        check(profile.packages.isNotEmpty() && profile.packages.all { it.isNotBlank() })
        check(profile.primaryHue in 0f..<360f && profile.secondaryHue in 0f..<360f)
        check(profile.saturation in 0f..1f)
    }
    println("App support checks passed: ${AppSupport.profiles.size} profiles, ${packages.size} exact package aliases; unknown apps retain a fallback.")
}
