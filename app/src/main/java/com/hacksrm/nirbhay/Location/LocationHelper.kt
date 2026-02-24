package com.hacksrm.nirbhay

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

object LocationHelper {

    private lateinit var fusedClient: FusedLocationProviderClient
    private var lastLocation: Location? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let {
                lastLocation = it
                Log.d("LocationHelper", "Location updated: ${it.latitude}, ${it.longitude}")
            }
        }
    }

    fun init(context: Context) {
        fusedClient = LocationServices.getFusedLocationProviderClient(context)
    }

    @SuppressLint("MissingPermission")
    fun startTracking() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateDistanceMeters(5f)
            .build()

        fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        Log.d("LocationHelper", "Started location tracking")
    }

    fun stopTracking() {
        fusedClient.removeLocationUpdates(locationCallback)
        Log.d("LocationHelper", "Stopped location tracking")
    }

    fun getLatLng(): Pair<Double, Double>? {
        return lastLocation?.let { Pair(it.latitude, it.longitude) }
    }
}