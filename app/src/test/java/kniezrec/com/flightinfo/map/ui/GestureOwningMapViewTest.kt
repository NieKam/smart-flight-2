package kniezrec.com.flightinfo.map.ui

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.tileprovider.modules.OfflineTileProvider
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The dashboard map keeps the gestures that start on it (TASK-049, as the original `BaseMapView`):
 * dragging or pinching the map pans/zooms it instead of scrolling the card list around it.
 */
@RunWith(AndroidJUnit4::class)
class GestureOwningMapViewTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var archive: File

    @Before fun createArchive() {
        archive = File(composeRule.activity.cacheDir, "gesture-map-test.zip")
        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("tile.jpg"))
            zip.write(byteArrayOf(0))
            zip.closeEntry()
        }
    }

    @Test fun asksTheParentNotToInterceptUntilTheGestureEnds() {
        val parent = RecordingParent(composeRule.activity)
        val map = mapView(composeRule.activity)
        composeRule.runOnUiThread {
            parent.addView(map)

            // The latest request counts (osmdroid may add its own while handling the event).
            map.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN))
            assertEquals(true, parent.requests.last())

            map.dispatchTouchEvent(event(MotionEvent.ACTION_UP))
            assertEquals(false, parent.requests.last())

            map.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN))
            assertEquals(true, parent.requests.last())
            map.dispatchTouchEvent(event(MotionEvent.ACTION_CANCEL))
            assertEquals(false, parent.requests.last())
        }
    }

    @Test fun draggingOrPinchingTheMapDoesNotScrollTheList() {
        lateinit var scroll: ScrollState
        composeRule.setContent {
            scroll = rememberScrollState()
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                Box(Modifier.fillMaxWidth().height(200.dp).testTag(ABOVE_TAG))
                AndroidView(
                    factory = { mapView(it) },
                    modifier = Modifier.fillMaxWidth().height(300.dp).testTag(MAP_TAG),
                )
                Box(Modifier.fillMaxWidth().height(2000.dp))
            }
        }

        composeRule.onNodeWithTag(MAP_TAG).performTouchInput { swipeUp() }
        composeRule.runOnIdle { assertEquals(0, scroll.value) }

        composeRule.onNodeWithTag(MAP_TAG).performTouchInput {
            pinch(
                start0 = center - Offset(20f, 20f),
                end0 = center - Offset(120f, 160f),
                start1 = center + Offset(20f, 20f),
                end1 = center + Offset(120f, 160f),
            )
        }
        composeRule.runOnIdle { assertEquals(0, scroll.value) }

        // Control: the same drag outside the map scrolls the list.
        composeRule.onNodeWithTag(ABOVE_TAG).performTouchInput { swipeUp() }
        composeRule.runOnIdle { assertTrue("the list did not scroll", scroll.value > 0) }
    }

    private fun mapView(context: Context) =
        GestureOwningMapView(context, OfflineTileProvider(SimpleRegisterReceiver(context), arrayOf(archive)))

    private fun event(action: Int): MotionEvent {
        val now = SystemClock.uptimeMillis()
        return MotionEvent.obtain(now, now, action, 10f, 10f, 0)
    }

    /** Records every [requestDisallowInterceptTouchEvent] of its children. */
    private class RecordingParent(
        context: Context,
    ) : FrameLayout(context) {
        val requests = mutableListOf<Boolean>()

        override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
            requests += disallowIntercept
            super.requestDisallowInterceptTouchEvent(disallowIntercept)
        }
    }

    private companion object {
        const val MAP_TAG = "map"
        const val ABOVE_TAG = "above"
    }
}
