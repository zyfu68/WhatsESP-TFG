package com.whatesp

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun EmergencyMapScreen(
    context: Context,
    latitude: Double,
    longitude: Double,
    eventId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val emergencyPoint = GeoPoint(latitude, longitude)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Mapa de emergencia",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Evento: $eventId",
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            text = "Latitud: $latitude | Longitud: $longitude",
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Volver")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 2.dp
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    Configuration.getInstance().userAgentValue = context.packageName

                    MapView(context).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)

                        controller.setZoom(18.0)
                        controller.setCenter(emergencyPoint)

                        val marker = Marker(this)
                        marker.position = emergencyPoint
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        marker.title = "Emergencia $eventId"
                        marker.snippet = "Ubicación enviada por el dispositivo"

                        overlays.add(marker)
                    }
                },
                update = { mapView ->
                    mapView.controller.setZoom(18.0)
                    val adjustedPoint = GeoPoint(latitude + 0.0003, longitude)
                    mapView.controller.setCenter(adjustedPoint)

                    mapView.overlays.clear()

                    val marker = Marker(mapView)
                    marker.position = emergencyPoint
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    marker.title = "Emergencia $eventId"
                    marker.snippet = "Ubicación enviada por el dispositivo"

                    mapView.overlays.add(marker)
                    mapView.invalidate()
                }
            )
        }
    }
}