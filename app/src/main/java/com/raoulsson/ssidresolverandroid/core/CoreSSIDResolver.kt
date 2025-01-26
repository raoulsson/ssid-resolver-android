package com.raoulsson.ssidresolverandroid.core

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

class MissingPermissionException(message: String) : Exception(message)

class CoreSSIDResolver(
    private val context: Context,
    private var permissionHandler: PermissionHandler? = null
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

    init {
        if (permissionHandler == null) {
            permissionHandler = PermissionHandler(context)
        }
    }

    suspend fun fetchSSID(): String {
        if (!permissionHandler!!.hasRequiredPermissions()) {
            val deniedPermissions = permissionHandler!!.listDeniedPermissions().joinToString(", ")
            Log.d(
                TAG,
                "Missing required permissions: $deniedPermissions. Aborting fetchSSID to prevent potential crash."
            )
            throw MissingPermissionException("Aborting fetchSSID to prevent potential crash. Missing permissions for: $deniedPermissions.")
        }

        return withTimeout(5000) {
            suspendCancellableCoroutine { continuation ->
                var receiverRegistered = false
                var wifiScanReceiver: BroadcastReceiver? = null
                var networkCallback: ConnectivityManager.NetworkCallback? = null
                var hasResumed = false

                // Define cleanup function
                val cleanup = {
                    if (receiverRegistered) {
                        try {
                            wifiScanReceiver?.let { context.unregisterReceiver(it) }
                            receiverRegistered = false
                        } catch (e: Exception) {
                            Log.e(TAG, "Error unregistering receiver", e)
                        }
                    }
                    networkCallback?.let {
                        try {
                            connectivityManager.unregisterNetworkCallback(it)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error unregistering network callback", e)
                        }
                    }
                }

                // Define safe resume function
                val safeResume = { result: String ->
                    if (!hasResumed) {
                        hasResumed = true
                        cleanup()
                        if (result == "Not connected to WiFi network" ||
                            result == "Error getting WiFi status" ||
                            result.isEmpty() ||
                            result == "<unknown ssid>"
                        ) {
                            continuation.resume("Unknown")
                        } else {
                            continuation.resume(result)
                        }
                    }
                }

                continuation.invokeOnCancellation {
                    cleanup()
                }

                networkCallback = object : ConnectivityManager.NetworkCallback() {
                    @RequiresApi(Build.VERSION_CODES.R)
                    override fun onCapabilitiesChanged(
                        network: Network,
                        capabilities: NetworkCapabilities
                    ) {
                        super.onCapabilitiesChanged(network, capabilities)
                        try {
                            wifiScanReceiver = object : BroadcastReceiver() {
                                @SuppressLint("MissingPermission")
                                override fun onReceive(context: Context, intent: Intent) {
                                    val scanResults = wifiManager.scanResults

                                    val connectedBssid = wifiManager.connectionInfo?.bssid
                                    val connectedNetwork = scanResults.firstOrNull {
                                        it.BSSID == connectedBssid
                                    }

                                    if (connectedNetwork != null) {
                                        val ssid =
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                connectedNetwork.wifiSsid.toString()
                                            } else {
                                                connectedNetwork.SSID
                                            }.removeSurrounding("\"")

                                        if (ssid.isNotEmpty() && ssid != "<unknown ssid>") {
                                            safeResume(ssid)
                                        } else {
                                            safeResume("Unknown")
                                        }
                                    } else if (scanResults.isNotEmpty()) {
                                        // If we can't find the connected network, use the strongest signal
                                        val strongestNetwork = scanResults.maxByOrNull { it.level }
                                        if (strongestNetwork != null) {
                                            val ssid =
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                    strongestNetwork.wifiSsid.toString()
                                                } else {
                                                    strongestNetwork.SSID
                                                }.removeSurrounding("\"")

                                            if (ssid.isNotEmpty() && ssid != "<unknown ssid>") {
                                                safeResume(ssid)
                                            } else {
                                                safeResume("Unknown")
                                            }
                                        } else {
                                            safeResume("Unknown")
                                        }
                                    } else {
                                        safeResume("Unknown")
                                    }
                                }
                            }

                            val intentFilter =
                                IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                            context.registerReceiver(wifiScanReceiver, intentFilter)
                            receiverRegistered = true

                            wifiManager.startScan()

                        } catch (e: Exception) {
                            Log.e(TAG, "Error in callback", e)
                            safeResume("Unknown")
                        }
                    }

                    override fun onLost(network: Network) {
                        super.onLost(network)
                        Log.d(TAG, "Network lost")
                        safeResume("Unknown")
                    }
                }

                try {
                    val request = NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .build()
                    connectivityManager.requestNetwork(request, networkCallback)
                } catch (e: Exception) {
                    Log.e(TAG, "Error requesting network", e)
                    safeResume("Unknown")
                }
            }
        }
    }

    companion object {
        private const val TAG = "CoreSSIDResolver"
    }
}