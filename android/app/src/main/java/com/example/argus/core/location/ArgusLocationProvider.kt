package com.example.argus.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import com.example.argus.core.permission.PermissionManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class LocationResult(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val mapUrl: String,
    val displayText: String
)

object ArgusLocationProvider {
    private const val TAG = "ArgusLocationProvider"

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): LocationResult? {
        if (!PermissionManager.hasLocationPermission(context)) {
            Log.w(TAG, "Location permission not granted")
            return null
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        // 1. Try last known location from providers
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        var bestLocation: Location? = null
        for (provider in providers) {
            try {
                if (locationManager.isProviderEnabled(provider)) {
                    val loc = locationManager.getLastKnownLocation(provider)
                    if (loc != null) {
                        if (bestLocation == null || loc.accuracy < bestLocation.accuracy) {
                            bestLocation = loc
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error querying provider $provider", e)
            }
        }

        // If last known location is very recent (less than 60 seconds old), return immediately
        if (bestLocation != null && System.currentTimeMillis() - bestLocation.time < 60_000) {
            return toLocationResult(bestLocation)
        }

        // 2. Request single fresh location fix with 5-second timeout
        val freshLocation = withTimeoutOrNull(5000L) {
            suspendCancellableCoroutine<Location?> { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        try {
                            locationManager.removeUpdates(this)
                        } catch (e: Exception) {
                            // Ignore
                        }
                        if (cont.isActive) {
                            cont.resume(location)
                        }
                    }

                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }

                try {
                    val enabledProvider = when {
                        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                        else -> null
                    }

                    if (enabledProvider != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val cancellationSignal = android.os.CancellationSignal()
                            cont.invokeOnCancellation { cancellationSignal.cancel() }
                            locationManager.getCurrentLocation(
                                enabledProvider,
                                cancellationSignal,
                                context.mainExecutor
                            ) { loc ->
                                if (cont.isActive) {
                                    cont.resume(loc ?: bestLocation)
                                }
                            }
                        } else {
                            locationManager.requestLocationUpdates(
                                enabledProvider,
                                0L,
                                0f,
                                listener,
                                Looper.getMainLooper()
                            )
                        }
                    } else {
                        cont.resume(bestLocation)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to request single location update", e)
                    cont.resume(bestLocation)
                }

                cont.invokeOnCancellation {
                    try {
                        locationManager.removeUpdates(listener)
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
        }

        val finalLoc = freshLocation ?: bestLocation
        return finalLoc?.let { toLocationResult(it) }
    }

    private fun toLocationResult(loc: Location): LocationResult {
        val lat = loc.latitude
        val lng = loc.longitude
        val latFormatted = "%.5f".format(lat)
        val lngFormatted = "%.5f".format(lng)
        val mapUrl = "https://maps.google.com/?q=$lat,$lng"
        val text = "📍 Live Location Pin ($latFormatted, $lngFormatted)\n$mapUrl"

        return LocationResult(
            latitude = lat,
            longitude = lng,
            accuracy = loc.accuracy,
            mapUrl = mapUrl,
            displayText = text
        )
    }
}
