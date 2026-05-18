package com.ktvincco.advancedpedometer.ui

import android.graphics.Color
import android.view.MotionEvent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ktvincco.advancedpedometer.data.PathPoint
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun PedometerMap(
    modifier: Modifier = Modifier,
    points: List<PathPoint>,
    onMapClick: (GeoPoint) -> Unit = {},
    showMyLocation: Boolean = true,
    isEditor: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Initialize MapView and manage its lifecycle
    val mapView = remember {
        MapView(context).apply {
            // Osmdroid zoom is much smoother with hardware acceleration.
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
            
            // Performance optimizations for smoother zooming
            isTilesScaledToDpi = true
            setTilesScaledToDpi(true)
            setFlingEnabled(true)

            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setUseDataConnection(true)
            
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(17.0)
            
            // Fix rough movement: Prevent parent scrollable components (like the Editor's Column)
            // from stealing touch events during interactions like zooming or panning.
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        v.parent.requestDisallowInterceptTouchEvent(true)
                    }
                }
                false // Allow the MapView to continue processing the event normally
            }

            val mapEventsOverlay = MapEventsOverlay(object : org.osmdroid.events.MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                    onMapClick(p)
                    return true
                }
                override fun longPressHelper(p: GeoPoint): Boolean = false
            })
            overlays.add(mapEventsOverlay)

            if (showMyLocation) {
                val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), this)
                locationOverlay.enableMyLocation()
                // Automatically center on the user's location as soon as it's available
                locationOverlay.runOnFirstFix {
                    val myLoc = locationOverlay.myLocation
                    if (myLoc != null) {
                        post {
                            controller.setCenter(myLoc)
                        }
                    }
                }
                overlays.add(locationOverlay)
            }
        }
    }

    // Lifecycle management is critical for osmdroid to start downloading tiles
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        
        // Manually trigger onResume if the lifecycle is already resumed when this enters composition
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mapView.onResume()
        }
        
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
        }
    }

    Box(modifier = modifier.clipToBounds()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { mapView },
            update = { view ->
                // Refresh polylines
                view.overlays.removeAll { it is Polyline }
                
                if (points.isNotEmpty()) {
                    var currentPolyline = Polyline(view)
                    setupPolyline(currentPolyline)
                    
                    points.forEach { point ->
                        if (point.isBreak) {
                            if (currentPolyline.actualPoints.isNotEmpty()) {
                                view.overlays.add(currentPolyline)
                            }
                            currentPolyline = Polyline(view)
                            setupPolyline(currentPolyline)
                        } else if (point.latitude != 0.0 || point.longitude != 0.0) {
                            currentPolyline.addPoint(GeoPoint(point.latitude, point.longitude))
                        }
                    }
                    
                    if (currentPolyline.actualPoints.isNotEmpty()) {
                        view.overlays.add(currentPolyline)
                    }
                    
                    // Center on first valid point if the map is fresh (at 0,0)
                    if (view.mapCenter.latitude == 0.0 && view.mapCenter.longitude == 0.0) {
                        points.firstOrNull { !it.isBreak && (it.latitude != 0.0 || it.longitude != 0.0) }?.let {
                            view.controller.setCenter(GeoPoint(it.latitude, it.longitude))
                        }
                    }
                }
                view.invalidate()
            }
        )

        // Centering button
        if (showMyLocation || points.any { !it.isBreak }) {
            SmallFloatingActionButton(
                onClick = {
                    val locationOverlay = mapView.overlays.filterIsInstance<MyLocationNewOverlay>().firstOrNull()
                    val myLoc = locationOverlay?.myLocation
                    
                    if (isEditor) {
                        // In Editor: Always prioritize real-time GPS location
                        if (myLoc != null) {
                            mapView.controller.animateTo(myLoc)
                        } else {
                            Toast.makeText(context, "Waiting for GPS fix...", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // In Viewer: Prioritize real-time GPS, fallback to last waypoint
                        if (myLoc != null) {
                            mapView.controller.animateTo(myLoc)
                        } else {
                            points.lastOrNull { !it.isBreak && (it.latitude != 0.0 || it.longitude != 0.0) }?.let {
                                mapView.controller.animateTo(GeoPoint(it.latitude, it.longitude))
                            }
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(40.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Center Map")
            }
        }
    }
}

private fun setupPolyline(polyline: Polyline) {
    polyline.outlinePaint.color = Color.BLUE
    polyline.outlinePaint.strokeWidth = 10f
    // Disable default info window and consume clicks to prevent "varks"
    polyline.infoWindow = null
    polyline.setOnClickListener { _, _, _ -> true }
}
