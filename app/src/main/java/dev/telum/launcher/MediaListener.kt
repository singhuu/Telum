package dev.telum.launcher

import android.service.notification.NotificationListenerService

/**
 * Exists only so the user can grant Telum notification access, which is what Android requires
 * before an app may see other apps' media sessions (what is playing, and its controls). It reads
 * no notifications. It runs in its own small process (":media"), so keeping it bound never keeps
 * the home screen's own memory alive during playback.
 */
class MediaListener : NotificationListenerService()
