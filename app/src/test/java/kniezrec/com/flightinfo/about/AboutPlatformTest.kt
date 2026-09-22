package kniezrec.com.flightinfo.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
