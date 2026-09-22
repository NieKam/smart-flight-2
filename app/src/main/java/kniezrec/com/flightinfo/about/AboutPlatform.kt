package kniezrec.com.flightinfo.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

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

class AndroidAppVersionProvider(
    private val context: Context,
) {
    fun read(): AppVersion {
        val info =
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
                ?: return AppVersion(null, null)
        val code =
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    info.versionCode.toLong()
                }
            }.getOrNull()?.takeIf { it > 0 }
        return AppVersion(info.versionName, code)
    }
}

object AboutIntentFactory {
    fun feedback(address: String) = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Uri.encode(address)}"))

    fun market(packageName: String) = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))

    fun web(packageName: String) = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
}

class AndroidExternalIntentLauncher(
    private val context: Context,
) {
    fun launch(intent: Intent): Boolean {
        if (intent.resolveActivity(context.packageManager) == null) return false
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    fun launchRate(packageName: String): Boolean =
        launch(AboutIntentFactory.market(packageName)) || launch(AboutIntentFactory.web(packageName))
}
