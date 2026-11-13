package com.retrocam.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat

/** Last known location from the platform LocationManager (no Play Services). Never throws. */
class LocationProvider(private val context: Context) {

    fun hasPermission(): Boolean =
        granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    /** Freshest last-known fix across providers, or null (no permission, no fix, or any error). */
    fun lastKnown(): Location? {
        if (!hasPermission()) return null
        return try {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
            manager.getProviders(true)
                .mapNotNull { provider ->
                    try {
                        @Suppress("MissingPermission") // checked by hasPermission() above
                        manager.getLastKnownLocation(provider)
                    } catch (_: SecurityException) {
                        null
                    }
                }
                .maxByOrNull { it.time }
        } catch (_: Throwable) {
            null
        }
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
