package com.raoulsson.ssidresolverandroid.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class PermissionHandler(private val context: Context) {

    private fun isPermissionGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasRequiredPermissions(): Boolean =
        requiredPermissions.all { permission -> isPermissionGranted(permission) }

    fun listGrantedPermissions(): List<String> =
        requiredPermissions.filter { permission -> isPermissionGranted(permission) }

    fun listDeniedPermissions(): List<String> =
        requiredPermissions.filter { permission -> !isPermissionGranted(permission) }

    companion object {
        val requiredPermissions: List<String> = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CHANGE_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )
    }
}