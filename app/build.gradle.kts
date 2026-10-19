plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val behaviorChecks = listOf("MusicIdlePolicyCheck", "AppSupportCheck", "SpringCheck")
behaviorChecks.forEach { checkName ->
    tasks.register<JavaExec>(checkName.replaceFirstChar { it.lowercase() }) {
        group = "verification"
        dependsOn("compileDebugUnitTestKotlin")
        classpath = files(layout.buildDirectory.dir("tmp/kotlin-classes/debugUnitTest"),
            layout.buildDirectory.dir("tmp/kotlin-classes/debug")) + configurations.getByName("debugUnitTestRuntimeClasspath")
        mainClass.set("dev.telum.launcher.${checkName}Kt")
    }
}
tasks.register("behaviorChecks") {
    group = "verification"
    dependsOn(behaviorChecks.map { it.replaceFirstChar { char -> char.lowercase() } })
}

android {
    namespace = "dev.telum.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.telum.launcher"
        // Android TV 10+. RenderNode offscreen rendering needs API 29.
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    // Supply all four variables locally or through CI secrets to sign a public release.
    val releaseSigning = listOf("TELUM_KEYSTORE", "TELUM_STORE_PASSWORD", "TELUM_KEY_ALIAS", "TELUM_KEY_PASSWORD")
        .map { System.getenv(it) }
    require(releaseSigning.all { it.isNullOrBlank() } || releaseSigning.all { !it.isNullOrBlank() }) {
        "Set all four TELUM signing variables, or leave all unset for an unsigned release."
    }
    if (releaseSigning.all { !it.isNullOrBlank() }) {
        signingConfigs.create("publicRelease") {
            storeFile = file(releaseSigning[0]!!)
            storePassword = releaseSigning[1]
            keyAlias = releaseSigning[2]
            keyPassword = releaseSigning[3]
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("publicRelease")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
    }
    androidResources {
        // Stored uncompressed so Typeface can map the bundled fonts instead of copying them.
        noCompress += "ttf"
    }
}
