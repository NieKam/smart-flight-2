package kniezrec.com.flightinfo.about

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AboutPlatformTest {
    @Test
    fun formatsCompleteVersionMetadata() {
        assertEquals("2.4 (17)", formatAppVersion(AppVersion("2.4", 17)))
    }

    @Test
    fun formatsPartialAndInvalidVersionMetadata() {
        assertEquals("2.4", formatAppVersion(AppVersion(" 2.4 ", null)))
        assertEquals("17", formatAppVersion(AppVersion(null, 17)))
        assertEquals("", formatAppVersion(AppVersion(" ", 0)))
    }

    @Test
    fun buildsExternalUris() {
        assertEquals(
            "mailto:kamil.niezrecki%40gmail.com",
            AboutIntentFactory.feedback("kamil.niezrecki@gmail.com").dataString,
        )
        assertEquals(
            "market://details?id=kniezrec.com.flightinfo",
            AboutIntentFactory.market("kniezrec.com.flightinfo").dataString,
        )
        assertTrue(AboutIntentFactory.web("kniezrec.com.flightinfo").data?.scheme == "https")
    }

    @Test
    fun versionIsReadFromPackageMetadataAndFallsBackWhenUnavailable() {
        val packageInfo =
            android.content.pm.PackageInfo().apply {
                versionName = "3.1"
                longVersionCode = 42
            }

        assertEquals(AppVersion("3.1", 42), appVersion(packageInfo))
        assertEquals(AppVersion(null, null), appVersion(null))
        assertEquals(AppVersion("3.1", null), appVersion(packageInfo.apply { longVersionCode = 0 }))
    }

    @Test
    fun providerReadsTheInstalledPackageAndFallsBackWhenItIsMissing() {
        val application = ApplicationProvider.getApplicationContext<Context>()
        val installed = "kniezrec.com.flightinfo.version.test"
        shadowOf(application.packageManager).installPackage(
            android.content.pm.PackageInfo().apply {
                packageName = installed
                versionName = "3.1"
                longVersionCode = 42
                applicationInfo = ApplicationInfo().apply { packageName = installed }
            },
        )

        assertEquals(AppVersion("3.1", 42), AppVersionProvider(application.packageManager, packageContext(application, installed)).read())
        assertEquals(
            AppVersion(null, null),
            AppVersionProvider(application.packageManager, packageContext(application, "missing.package")).read(),
        )
    }

    private fun packageContext(
        base: Context,
        packageName: String,
    ): Context =
        object : ContextWrapper(base) {
            override fun getPackageName(): String = packageName
        }

    @Test
    fun launcherReportsUnavailableHandlerAndStartFailure() {
        val intent = AboutIntentFactory.feedback("pilot@example.com")
        assertEquals(false, AndroidExternalIntentLauncher(resolves = { false }, start = {}).launch(intent))
        assertEquals(false, AndroidExternalIntentLauncher(resolves = { true }, start = { error("boom") }).launch(intent))
    }

    @Test
    fun launcherFallsBackFromMarketToWeb() {
        val launched = mutableListOf<android.content.Intent>()
        val result =
            AndroidExternalIntentLauncher(
                resolves = { it.data?.scheme == "https" },
                start = { launched += it },
            ).launchRate("kniezrec.com.flightinfo")

        assertEquals(true, result)
        assertEquals("https", launched.single().data?.scheme)
    }
}
