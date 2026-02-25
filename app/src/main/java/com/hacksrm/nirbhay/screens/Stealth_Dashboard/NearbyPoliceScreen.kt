package com.hacksrm.nirbhay.screens.Stealth_Dashboard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

// ── Data classes for Places API response ──
data class PlaceResult(val name: String, val vicinity: String, val geometry: PlaceGeometry)
data class PlaceGeometry(val location: PlaceLocation)
data class PlaceLocation(val lat: Double, val lng: Double)

private const val TAG = "NearbyPolice"

/**
 * Composable that shows a Google Map with user's location (blue marker)
 * and nearby police stations (red markers) fetched from Google Places API.
 *
 * Height is fixed at 340dp so it slots into the dashboard layout.
 */
@Composable
fun NearbyPoliceMap(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var policeStations by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val cameraPositionState = rememberCameraPositionState {
        // Default to India center until real location arrives
        position = CameraPosition.fromLatLngZoom(LatLng(20.5937, 78.9629), 5f)
    }

    // Read API key from manifest meta-data
    val apiKey = remember {
        try {
            val ai = context.packageManager.getApplicationInfo(
                context.packageName, PackageManager.GET_META_DATA
            )
            ai.metaData?.getString("com.google.android.geo.API_KEY") ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    // On launch: get current location, then fetch nearby police stations
    LaunchedEffect(Unit) {
        getUserLocationLatLng(context) { latLng ->
            userLocation = latLng
            cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 14f)
            Log.d(TAG, "User location: ${latLng.latitude}, ${latLng.longitude}")

            if (apiKey.isNotBlank()) {
                scope.launch {
                    try {
                        policeStations = fetchNearbyPoliceStations(
                            lat = latLng.latitude,
                            lng = latLng.longitude,
                            apiKey = apiKey
                        )
                        Log.d(TAG, "Found ${policeStations.size} police stations nearby")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to fetch police stations", e)
                        errorMsg = "Could not load police stations"
                    }
                }
            } else {
                errorMsg = "Maps API key not configured"
                Log.e(TAG, "MAPS_API_KEY is empty")
            }
        }
    }

    // ── UI ──
    Box(modifier = modifier
        .fillMaxWidth()
        .height(340.dp)
        .clip(RoundedCornerShape(16.dp))
    ) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                mapToolbarEnabled = false
            ),
            properties = MapProperties(
                isMyLocationEnabled = hasLocationPermission(context)
            )
        ) {
            // Blue marker for user
            userLocation?.let { loc ->
                Marker(
                    state = MarkerState(position = loc),
                    title = "You are here",
                    icon = BitmapDescriptorFactory.defaultMarker(
                        BitmapDescriptorFactory.HUE_BLUE
                    )
                )
            }

            // Red markers for police stations
            policeStations.forEach { station ->
                Marker(
                    state = MarkerState(
                        position = LatLng(
                            station.geometry.location.lat,
                            station.geometry.location.lng
                        )
                    ),
                    title = station.name,
                    snippet = station.vicinity
                )
            }
        }

        // Station count badge (top-right)
        if (policeStations.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(Color(0xCC1A0F0F), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "📍 ${policeStations.size} Police Stations",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Error overlay
        errorMsg?.let { msg ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xCC1A0F0F), RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Text(msg, color = Color(0xFFEF4444), fontSize = 13.sp)
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun hasLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

/**
 * Gets user's last known location using FusedLocationProviderClient.
 */
private fun getUserLocationLatLng(context: Context, callback: (LatLng) -> Unit) {
    try {
        if (!hasLocationPermission(context)) {
            Log.w(TAG, "Location permission not granted")
            return
        }
        val client = LocationServices.getFusedLocationProviderClient(context)
        client.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                callback(LatLng(location.latitude, location.longitude))
            } else {
                Log.w(TAG, "Last known location is null — using fallback")
                // Fallback: use LocationHelper if available
                try {
                    val helperLatLng = com.hacksrm.nirbhay.LocationHelper.getLatLng()
                    if (helperLatLng != null) {
                        callback(LatLng(helperLatLng.first, helperLatLng.second))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "LocationHelper fallback failed", e)
                }
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to get location", e)
        }
    } catch (e: SecurityException) {
        Log.e(TAG, "SecurityException getting location", e)
    }
}

/**
 * Fetches nearby police stations from Google Places Nearby Search API.
 * Runs on IO dispatcher. Returns a list of PlaceResult.
 */
suspend fun fetchNearbyPoliceStations(
    lat: Double,
    lng: Double,
    apiKey: String
): List<PlaceResult> {
    return withContext(Dispatchers.IO) {
        try {
            val url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json" +
                    "?location=$lat,$lng" +
                    "&radius=3000" +        // 3 km radius
                    "&type=police" +         // filter: police stations only
                    "&key=$apiKey"

            Log.d(TAG, "Fetching police stations: $url")
            val response = URL(url).readText()
            val json = JSONObject(response)

            val status = json.optString("status", "UNKNOWN")
            if (status != "OK") {
                Log.w(TAG, "Places API status: $status")
                return@withContext emptyList()
            }

            val results = json.getJSONArray("results")
            (0 until results.length()).map { i ->
                val place = results.getJSONObject(i)
                PlaceResult(
                    name = place.optString("name", "Police Station"),
                    vicinity = place.optString("vicinity", ""),
                    geometry = PlaceGeometry(
                        location = PlaceLocation(
                            lat = place.getJSONObject("geometry")
                                .getJSONObject("location")
                                .getDouble("lat"),
                            lng = place.getJSONObject("geometry")
                                .getJSONObject("location")
                                .getDouble("lng")
                        )
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching police stations", e)
            emptyList()
        }
    }
}

