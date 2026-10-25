# Privacy

Telum has no account, no analytics, and no telemetry. Telum keeps its settings in the local storage of the app. Android backup is off for Telum.

## What Telum reads on the device

- **Installed apps.** Setup reads the list of apps that can start on the device. You select the apps that Home shows, and their order. Setup does not install or remove apps.
- **Home setting.** Telum reads which app the Home button opens. It uses this information only to decide if it asks **Make Telum your Home?**.
- **TV listings**, only with your permission. Telum reads the rows that other apps share with the TV home screen.
- **Media sessions**, only with notification access. Telum reads the music that plays, to show the controls and the album art.

The intro, the intro sound, and the Home question do not use the network.

## Setup drafts

Setup offers **Recommended**, with the optional integrations on, and **Light**, with them off. You can change each setting. Your apps, their order, the features, the Live Activities rows, and the city stay in a local draft until you finish setup.

When you do the setup again from **Control centre**, **Exit setup** deletes the draft. Some actions take effect immediately and stay:

- A city search sends the search text immediately.
- A change to an Android permission takes effect immediately.

Permissions and the city are optional. Setup never selects a location for you.

## Optional network features

**Weather** is off until you turn it on. A city search sends the search text to the geocoding service of Open-Meteo. Forecast requests send the coordinates of the city that you selected. Telum does not ask for the location permission of the device. Open-Meteo also gets your IP address.

**Artist photos** are off until you turn them on. Telum sends the name of the current artist to TheAudioDB. Then it downloads the image that TheAudioDB gives. The provider and the image host get music information and your IP address. When no photo is found, Telum shows the album art.

**Programme posters and media artwork** can use HTTPS addresses that other apps supply. When Telum downloads such an image, the image host gets the request and your IP address. Telum also uses artwork that an installed app supplies locally. Telum keeps downloaded images in its private storage.

## Android permissions

| Permission | Use |
| --- | --- |
| Internet | The optional services and the remote artwork. |
| Read TV listings | Reads the Watch Next rows and the preview rows that other apps share. It does not give access to streaming accounts. |
| Notification access | Finds and controls the active media sessions. Telum does not process or keep the contents of notifications. Android gives broad access with this permission. Turn it on only if you want music controls. |

To stop a feature, turn it off in **Control centre** › **Telum**, or remove its permission in Android settings. To delete the settings and the images of Telum, clear its storage or remove it.

## Diagnostic log

Telum writes a short log when drawing is slow, at `Android/media/dev.telum.launcher/watchdog.log`. The log contains times, state names, and stack traces. It does not contain track titles. ADB can read the file. Examine a log before you share it.
