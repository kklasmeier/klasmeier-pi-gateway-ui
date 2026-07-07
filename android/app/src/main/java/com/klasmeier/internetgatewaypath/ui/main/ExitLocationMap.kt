package com.klasmeier.internetgatewaypath.ui.main

import android.location.Geocoder
import android.view.MotionEvent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.klasmeier.internetgatewaypath.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.Locale

@Composable
fun ExitLocationMap(
    latitude: Double?,
    longitude: Double?,
    locationLabel: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var resolvedLat by remember(latitude, longitude, locationLabel) { mutableStateOf(latitude) }
    var resolvedLng by remember(latitude, longitude, locationLabel) { mutableStateOf(longitude) }

    LaunchedEffect(latitude, longitude, locationLabel) {
        if (latitude != null && longitude != null) {
            resolvedLat = latitude
            resolvedLng = longitude
            return@LaunchedEffect
        }
        resolvedLat = null
        resolvedLng = null
        if (locationLabel.isNullOrBlank() || !Geocoder.isPresent()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("DEPRECATION")
                Geocoder(context, Locale.getDefault())
                    .getFromLocationName(locationLabel, 1)
                    ?.firstOrNull()
            }.getOrNull()?.let { address ->
                resolvedLat = address.latitude
                resolvedLng = address.longitude
            }
        }
    }

    val lat = resolvedLat
    val lng = resolvedLng
    if (lat == null || lng == null) return

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                        view.parent.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        view.parent.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    LaunchedEffect(lat, lng, locationLabel) {
        mapView.overlays.clear()
        val point = GeoPoint(lat, lng)
        mapView.controller.setZoom(CITY_ZOOM)
        mapView.controller.setCenter(point)
        mapView.overlays.add(
            Marker(mapView).apply {
                position = point
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = locationLabel
            },
        )
        mapView.invalidate()
    }

    Column(modifier = modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
        Text(
            text = stringResource(R.string.map_attribution),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val CITY_ZOOM = 10.0
