package com.hacksrm.nirbhay.screens.Stealth_Dashboard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.*

// ── Data classes ──
data class PlaceResult(
    val name: String,
    val vicinity: String,
    val geometry: PlaceGeometry,
    val distanceKm: Double = 0.0
)
data class PlaceGeometry(val location: PlaceLocation)
data class PlaceLocation(val lat: Double, val lng: Double)

data class RouteInfo(
    val polylinePoints: List<LatLng>,
    val distanceText: String,
    val durationText: String
)

private const val TAG = "NearbyPolice"

private val httpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()

private fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val results = FloatArray(1)
    Location.distanceBetween(lat1, lng1, lat2, lng2, results)
    return (results[0] / 1000.0 * 10.0).roundToInt() / 10.0
}

@Composable
fun NearbyPoliceMap(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var policeStations by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var routeInfo by remember { mutableStateOf<RouteInfo?>(null) }
    var selectedStation by remember { mutableStateOf<PlaceResult?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(20.5937, 78.9629), 5f)
    }

    val apiKey = remember {
        try {
            val ai = context.packageManager.getApplicationInfo(
                context.packageName, PackageManager.GET_META_DATA
            )
            val key = ai.metaData?.getString("com.google.android.geo.API_KEY") ?: ""
            // NEVER log the key — not even partially
            Log.d(TAG, "Maps API key loaded: ${if (key.isNotBlank()) "[OK]" else "[MISSING]"}")
            key
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read API key from manifest", e)
            ""
        }
    }

    fun fetchRouteToStation(origin: LatLng, station: PlaceResult) {
        selectedStation = station
        scope.launch {
            val info = fetchDirectionsRoute(
                originLat = origin.latitude, originLng = origin.longitude,
                destLat = station.geometry.location.lat,
                destLng = station.geometry.location.lng,
                apiKey = apiKey
            )
            if (info != null) {
                routeInfo = info
                routePoints = info.polylinePoints
                Log.d(TAG, "Route fetched: ${info.polylinePoints.size} points, ${info.distanceText}, ${info.durationText}")
            } else {
                routePoints = listOf(origin, LatLng(station.geometry.location.lat, station.geometry.location.lng))
                routeInfo = RouteInfo(routePoints, "?", "?")
                Log.w(TAG, "Directions failed — drawing straight line")
            }
        }
    }

    LaunchedEffect(Unit) {
        isLoading = true
        getUserLocationLatLng(context) { latLng ->
            userLocation = latLng
            cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 14f)
            // Safe to log coordinates — not sensitive like an API key
            Log.d(TAG, "User location: ${latLng.latitude}, ${latLng.longitude}")

            if (apiKey.isNotBlank()) {
                scope.launch {
                    try {
                        val raw = fetchNearbyPoliceStations(
                            lat = latLng.latitude, lng = latLng.longitude, apiKey = apiKey
                        )
                        val withDist = raw.map { ps ->
                            ps.copy(
                                distanceKm = haversineKm(
                                    latLng.latitude, latLng.longitude,
                                    ps.geometry.location.lat, ps.geometry.location.lng
                                )
                            )
                        }.sortedBy { it.distanceKm }

                        policeStations = withDist
                        Log.d(TAG, "Found ${withDist.size} police stations nearby")

                        if (withDist.isNotEmpty()) {
                            fetchRouteToStation(latLng, withDist.first())
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to fetch police stations", e)
                        errorMsg = "Could not load police stations"
                    } finally {
                        isLoading = false
                    }
                }
            } else {
                errorMsg = "Maps API key not configured"
                isLoading = false
            }
        }
    }

    // ── UI ──
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
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
                properties = MapProperties(isMyLocationEnabled = hasLocationPermission(context))
            ) {
                userLocation?.let { loc ->
                    Marker(
                        state = MarkerState(position = loc),
                        title = "You are here",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)
                    )
                }

                policeStations.forEachIndexed { index, station ->
                    val pos = LatLng(station.geometry.location.lat, station.geometry.location.lng)
                    val isSelected = station == selectedStation
                    Marker(
                        state = MarkerState(position = pos),
                        title = station.name,
                        snippet = "${String.format("%.1f", station.distanceKm)} km • ${station.vicinity}",
                        icon = BitmapDescriptorFactory.defaultMarker(
                            if (isSelected) BitmapDescriptorFactory.HUE_GREEN
                            else BitmapDescriptorFactory.HUE_RED
                        ),
                        onClick = {
                            userLocation?.let { ul -> fetchRouteToStation(ul, station) }
                            false
                        }
                    )
                }

                if (routePoints.isNotEmpty()) {
                    Polyline(points = routePoints, color = Color(0xFF4285F4), width = 10f)
                }
            }

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
                        color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium
                    )
                }
            }

            routeInfo?.let { info ->
                Card(
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xE61B5E20)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("🚶 ${info.durationText}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("📍 ${info.distanceText}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFFEC1313)
                )
            }

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

        if (policeStations.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Nearest Police Stations",
                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(policeStations.take(5)) { index, station ->
                    val isSelected = station == selectedStation
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Color(0x33EC1313) else Color(0x662D0A0A))
                            .clickable { userLocation?.let { ul -> fetchRouteToStation(ul, station) } }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(
                                    if (isSelected) Color(0xFFEC1313) else Color(0xFF475569),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${index + 1}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = station.name,
                                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = station.vicinity,
                                color = Color(0xFF94A3B8), fontSize = 11.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${String.format("%.1f", station.distanceKm)} km",
                            color = if (isSelected) Color(0xFFEC1313) else Color(0xFF94A3B8),
                            fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
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

private fun getUserLocationLatLng(context: Context, callback: (LatLng) -> Unit) {
    try {
        if (!hasLocationPermission(context)) {
            Log.w(TAG, "Location permission not granted"); return
        }
        val client = LocationServices.getFusedLocationProviderClient(context)
        client.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                callback(LatLng(location.latitude, location.longitude))
            } else {
                Log.w(TAG, "Last known location is null — trying LocationHelper fallback")
                try {
                    val helperLatLng = com.hacksrm.nirbhay.LocationHelper.getLatLng()
                    if (helperLatLng != null) {
                        callback(LatLng(helperLatLng.first, helperLatLng.second))
                    }
                } catch (e: Exception) { Log.e(TAG, "LocationHelper fallback failed", e) }
            }
        }.addOnFailureListener { e -> Log.e(TAG, "Failed to get location", e) }
    } catch (e: SecurityException) { Log.e(TAG, "SecurityException getting location", e) }
}

/**
 * Fetches nearby police stations using keyword search instead of type=police.
 * keyword=police+station works much more reliably in India than type=police.
 * Falls back to a second attempt with wider radius if first returns nothing.
 */
suspend fun fetchNearbyPoliceStations(
    lat: Double, lng: Double, apiKey: String
): List<PlaceResult> {
    return withContext(Dispatchers.IO) {
        // Try progressively: 5km → 10km → 15km, keyword search
        val radii = listOf(5000, 10000, 15000)
        for (radius in radii) {
            val results = fetchPlaces(lat, lng, radius, apiKey)
            if (results.isNotEmpty()) {
                Log.d(TAG, "Found ${results.size} stations at radius ${radius}m")
                return@withContext results
            }
            Log.d(TAG, "No results at ${radius}m, trying wider radius...")
        }
        Log.w(TAG, "No police stations found within 15km")
        emptyList()
    }
}

private suspend fun fetchPlaces(
    lat: Double, lng: Double, radius: Int, apiKey: String
): List<PlaceResult> {
    return withContext(Dispatchers.IO) {
        try {
            // Use keyword=police+station — much more reliable in India than type=police
            val url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json" +
                    "?location=$lat,$lng" +
                    "&radius=$radius" +
                    "&keyword=police+station" +
                    "&key=$apiKey"

            // DO NOT log the full URL — it contains the API key
            Log.d(TAG, "Fetching police stations (radius=${radius}m)")

            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()

            val json = JSONObject(body)
            val status = json.optString("status", "UNKNOWN")

            if (status == "OK") {
                val results = json.getJSONArray("results")
                Log.d(TAG, "Places API returned ${results.length()} results")
                (0 until results.length()).map { i ->
                    val place = results.getJSONObject(i)
                    PlaceResult(
                        name = place.optString("name", "Police Station"),
                        vicinity = place.optString("vicinity", ""),
                        geometry = PlaceGeometry(
                            location = PlaceLocation(
                                lat = place.getJSONObject("geometry").getJSONObject("location").getDouble("lat"),
                                lng = place.getJSONObject("geometry").getJSONObject("location").getDouble("lng")
                            )
                        )
                    )
                }
            } else {
                Log.w(TAG, "Places API status: $status — ${json.optString("error_message", "no error_message")}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching police stations", e)
            emptyList()
        }
    }
}

suspend fun fetchDirectionsRoute(
    originLat: Double, originLng: Double,
    destLat: Double, destLng: Double,
    apiKey: String
): RouteInfo? {
    return withContext(Dispatchers.IO) {
        try {
            val url = "https://maps.googleapis.com/maps/api/directions/json" +
                    "?origin=$originLat,$originLng" +
                    "&destination=$destLat,$destLng" +
                    "&mode=walking" +
                    "&key=$apiKey"

            // DO NOT log the full URL — it contains the API key
            Log.d(TAG, "Fetching walking directions")

            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null

            val json = JSONObject(body)
            val status = json.optString("status", "UNKNOWN")
            if (status != "OK") {
                Log.w(TAG, "Directions API status: $status")
                return@withContext null
            }

            val routes = json.getJSONArray("routes")
            if (routes.length() == 0) return@withContext null

            val route = routes.getJSONObject(0)
            val leg = route.getJSONArray("legs").getJSONObject(0)
            val overviewPolyline = route.getJSONObject("overview_polyline").getString("points")
            val points = decodePolyline(overviewPolyline)
            val distanceText = leg.getJSONObject("distance").getString("text")
            val durationText = leg.getJSONObject("duration").getString("text")

            Log.d(TAG, "Route: ${points.size} points, $distanceText, $durationText")
            RouteInfo(polylinePoints = points, distanceText = distanceText, durationText = durationText)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching directions", e)
            null
        }
    }
}

private fun decodePolyline(encoded: String): List<LatLng> {
    val poly = mutableListOf<LatLng>()
    var index = 0
    val len = encoded.length
    var lat = 0
    var lng = 0

    while (index < len) {
        var b: Int
        var shift = 0
        var result = 0
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        lat += dlat

        shift = 0; result = 0
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        lng += dlng

        poly.add(LatLng(lat / 1E5, lng / 1E5))
    }
    return poly
}