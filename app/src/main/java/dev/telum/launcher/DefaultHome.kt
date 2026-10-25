package dev.telum.launcher

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Where the remote's Home button goes, and the screens Android offers for changing it.
 *
 * Holding Android's Home role is not enough: Google's TV launcher declares a priority-2 HOME
 * filter that Android 11 prefers over the role holder (measured on the emulator, 2026-10-17).
 * So only what the Home intent resolves to says whether the button opens Telum.
 */
class DefaultHome(private val context: Context) {

    enum class State {
        /** The Home button opens Telum. */
        TELUM,
        /** Telum holds the Home role, but another app's Home still opens first. */
        OUTRANKED,
        /** Another app, or Android's chooser. */
        OTHER,
    }

    fun state(): State = when {
        isTelum() -> State.TELUM
        roleHeld() -> State.OUTRANKED
        else -> State.OTHER
    }

    fun isTelum() = resolved()?.activityInfo?.packageName == context.packageName

    /** The name of the app the Home button opens now, for example "Android TV Home". */
    fun currentName(): String? = resolved()?.takeIf { it.activityInfo.packageName != "android" }
        ?.loadLabel(context.packageManager)?.toString()

    /**
     * The name of [pkg]'s Home screen, the same one [currentName] gives while that app opens
     * first, and read even while the app is turned off. Null if Android can't give a name.
     */
    fun homeName(pkg: String): String? {
        val pm = context.packageManager
        val label = pm.queryIntentActivities(intent().setPackage(pkg), PackageManager.MATCH_DISABLED_COMPONENTS)
            .firstOrNull()?.loadLabel(pm)
            ?: try { pm.getApplicationInfo(pkg, 0).loadLabel(pm) } catch (_: PackageManager.NameNotFoundException) { null }
        // Android falls back to the package name when an app has no label.
        return label?.toString()?.takeIf { it.isNotBlank() && it != pkg }
    }

    private fun resolved() = context.packageManager.resolveActivity(intent(), PackageManager.MATCH_DEFAULT_ONLY)

    private fun roles(): RoleManager? = context.getSystemService(RoleManager::class.java)
        ?.takeIf { it.isRoleAvailable(RoleManager.ROLE_HOME) }

    private fun roleHeld() = roles()?.isRoleHeld(RoleManager.ROLE_HOME) == true

    /** Android's own "Set Telum as your default home app?" dialog, where the device offers it. */
    fun requestIntent(): Intent? = roles()?.createRequestRoleIntent(RoleManager.ROLE_HOME)?.takeIf(::resolves)

    /** Android's list of Home apps, where the device has one. */
    fun settingsIntent(): Intent? = Intent(Settings.ACTION_HOME_SETTINGS).takeIf(::resolves)

    private fun resolves(i: Intent) = context.packageManager.resolveActivity(i, 0) != null

    companion object {
        fun intent(): Intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    }
}
