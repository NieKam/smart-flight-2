package kniezrec.com.flightinfo.about

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

data class AppVersion(
    val name: String?,
    val code: Long?,
)

fun formatAppVersion(version: AppVersion): String {
    val name = version.name?.trim()?.takeIf { it.isNotEmpty() }
    val code = version.code?.takeIf { it > 0 }
    return when {
        name != null && code != null -> "$name ($code)"
        name != null -> name
        code != null -> code.toString()
        else -> ""
    }
}

/** Version of the installed app, read from the package manager. */
@Singleton
class AppVersionProvider
    @Inject
    constructor(
        private val packageManager: PackageManager,
        @ApplicationContext context: Context,
    ) {
        private val packageName = context.packageName

        fun read(): AppVersion = appVersion(runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull())
    }

/** [AppVersion] of [info]; a missing package or a non-positive version code reads as absent. */
fun appVersion(info: PackageInfo?): AppVersion {
    if (info == null) return AppVersion(null, null)
    val code = runCatching { info.longVersionCode }.getOrNull()?.takeIf { it > 0 }
    return AppVersion(info.versionName, code)
}

object AboutIntentFactory {
    fun feedback(address: String) = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${URLEncoder.encode(address, "UTF-8")}"))

    fun market(packageName: String) = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))

    fun web(packageName: String) = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
}

class AndroidExternalIntentLauncher(
    private val resolves: (Intent) -> Boolean,
    private val start: (Intent) -> Unit,
) {
    constructor(context: Context) : this(
        resolves = { intent -> intent.resolveActivity(context.packageManager) != null },
        start = { intent -> context.startActivity(intent) },
    )

    fun launch(intent: Intent): Boolean {
        if (!runCatching { resolves(intent) }.getOrDefault(false)) return false
        return runCatching { start(intent) }.isSuccess
    }

    fun launchRate(packageName: String): Boolean =
        launch(AboutIntentFactory.market(packageName)) || launch(AboutIntentFactory.web(packageName))
}
