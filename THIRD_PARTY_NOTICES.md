# Third-party notices

## Fonts

Inter: Copyright 2020 The Inter Project Authors. Manrope: Copyright 2018 The Manrope Project Authors. Both fonts use the SIL Open Font License 1.1. The full notices are next to the fonts in `app/src/main/assets/fonts/`.

## Icons

The tick icons in setup are Google [Material Symbols](https://fonts.google.com/icons), Outlined `check_circle` and `circle`. They use the [Apache License 2.0](licenses/Apache-2.0.txt). They are the vector drawables `app/src/main/res/drawable/symbol_*.xml`. Each file gives its source.

## Gradle wrapper

The Gradle wrapper scripts and the wrapper JAR use the Apache License 2.0. See `gradle/LICENSE` and the [Gradle source](https://github.com/gradle/gradle). The wrapper downloads the Gradle distribution, which contains its own notices.

## Star data in the intro

The positions, distances, and brightness of the stars in the intro come from the Hipparcos catalogue of the European Space Agency (ESA, 1997). The distances use the new reduction of the Hipparcos data by F. van Leeuwen (2007). `app/src/main/assets/intro/common.glsl` contains the values.

## Intro sound

The intro sound is original to Telum. `tools/make-intro-hum.py` generates it. It uses the same license as the Telum source.

## Weather

Weather data by [Open-Meteo](https://open-meteo.com/), under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Telum shows selected fields and rounds the temperatures. The usage terms of the provider apply separately.

## Artwork and services

Optional artist photos and data come from [TheAudioDB](https://www.theaudiodb.com/). App icons and banners come from the installed apps. Programme artwork comes from the addresses that the apps publish. Telum does not contain streaming posters or a music catalogue.

The GPL license of Telum does not apply to third-party artwork and trademarks. Their availability depends on the terms, the data, and the service of each provider.
