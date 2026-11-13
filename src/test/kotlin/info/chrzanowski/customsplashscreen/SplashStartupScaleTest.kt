package info.chrzanowski.customsplashscreen

import org.junit.Assert.assertEquals
import org.junit.Test

class SplashStartupScaleTest {

    private fun properties(vararg values: Pair<String, String>): (String) -> String? = mapOf(*values)::get

    @Test
    fun `JRE-managed HiDPI uses a user scale of one regardless of the system scale`() {
        for (sysScale in listOf(1f, 1.5f, 2f)) {
            assertEquals(1f, startupUserScale(jreHiDpi = true, sysScale = sysScale, property = properties()))
        }
    }

    @Test
    fun `IDE-managed HiDPI rounds the system scale to quarter steps`() {
        assertEquals(1.5f, startupUserScale(jreHiDpi = false, sysScale = 1.4f, isLinux = false, property = properties()))
        assertEquals(2f, startupUserScale(jreHiDpi = false, sysScale = 2.1f, isLinux = false, property = properties()))
    }

    @Test
    fun `IDE-managed HiDPI on Linux ignores a 125 percent scale with the default font size`() {
        assertEquals(1f, startupUserScale(false, 1.25f, isLinux = true, defaultSystemFontSize = 12f, property = properties()))
        assertEquals(1.25f, startupUserScale(false, 1.25f, isLinux = true, defaultSystemFontSize = 13f, property = properties()))
    }

    @Test
    fun `the ide ui scale property overrides the computed scale`() {
        assertEquals(1.75f, startupUserScale(true, 1f, property = properties("ide.ui.scale" to "1.75")))
        assertEquals(1f, startupUserScale(false, 2f, property = properties("ide.ui.scale.override" to "true")))
        assertEquals(1f, startupUserScale(true, 2f, property = properties("ide.ui.scale" to "invalid")))
    }

    @Test
    fun `disabling HiDPI forces a user scale of one`() {
        assertEquals(1f, startupUserScale(false, 2f, isLinux = false, property = properties("hidpi" to "false")))
    }
}
