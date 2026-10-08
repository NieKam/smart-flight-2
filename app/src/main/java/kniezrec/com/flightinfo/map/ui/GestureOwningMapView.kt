package kniezrec.com.flightinfo.map.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import org.osmdroid.tileprovider.MapTileProviderBase
import org.osmdroid.views.MapView

/**
 * A [MapView] that keeps every gesture starting on it, as the original app's `BaseMapView`: on the
 * first touch it asks its parent not to intercept, until the last finger lifts or the gesture is
 * cancelled. Inside the dashboard's scrolling card list the map then pans and pinch-zooms instead
 * of the list scrolling (Compose's `AndroidView` honors the request: the view gets the events
 * before the list and consumes them).
 */
@SuppressLint("ViewConstructor")
internal class GestureOwningMapView : MapView {
    constructor(context: Context, tileProvider: MapTileProviderBase) : super(context, tileProvider)

    constructor(context: Context) : super(context)

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return super.dispatchTouchEvent(event)
    }
}
