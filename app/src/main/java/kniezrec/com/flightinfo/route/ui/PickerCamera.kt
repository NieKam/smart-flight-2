package kniezrec.com.flightinfo.route.ui

import kniezrec.com.flightinfo.nearby.NearbyCoordinate

/** Where the picker map moves to show a chosen city. */
internal data class PickerCamera(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
)

/** Zoom of the picker map before any city is shown: the whole world. */
internal const val PICKER_WORLD_ZOOM = 3.0

/** Zoom that shows the region around a city, used when the map is still at the world zoom. */
internal const val PICKER_CITY_ZOOM = 5.0

/**
 * Camera that centers the picker map on [city]. Like the original app it keeps the zoom the user
 * chose ([currentZoom]); at the world zoom or further out it zooms in to [PICKER_CITY_ZOOM] so the
 * region around the city is visible.
 */
internal fun pickerCamera(
    city: NearbyCoordinate,
    currentZoom: Double,
): PickerCamera =
    PickerCamera(
        latitude = city.latitude,
        longitude = city.longitude,
        zoom = if (currentZoom <= PICKER_WORLD_ZOOM) PICKER_CITY_ZOOM else currentZoom,
    )
