package com.raoulsson.ssidresolverandroid.core

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SSIDResolverViewModel(
    private val coreResolver: CoreSSIDResolver,
    private val permissionHandler: PermissionHandler
) : ViewModel() {

    private val _ssid = MutableStateFlow("Unresolved SSID")
    val ssid: StateFlow<String> = _ssid

    private val _permissionStatus = MutableStateFlow("Permissions not checked")
    val permissionStatus: StateFlow<String> = _permissionStatus

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _grantedPermissions = MutableStateFlow<List<String>>(emptyList())
    val grantedPermissions: StateFlow<List<String>> = _grantedPermissions

    private val _deniedPermissions = MutableStateFlow<List<String>>(emptyList())
    val deniedPermissions: StateFlow<List<String>> = _deniedPermissions

    init {
        updatePermissionLists()
        if (permissionHandler.hasRequiredPermissions()) {
            _permissionStatus.value = "All permissions granted"
            _errorMessage.value = ""
        } else {
            _permissionStatus.value = "Permissions denied"
            _errorMessage.value =
                "Missing permissions: ${_deniedPermissions.value.joinToString(", ")}"
        }
        Log.d(TAG, "ViewModel initialized with error: ${_errorMessage.value}")
    }

    private fun updatePermissionLists() {
        _grantedPermissions.value = permissionHandler.listGrantedPermissions()
        _deniedPermissions.value = permissionHandler.listDeniedPermissions()
    }

    fun onPermissionsGranted() {
        _permissionStatus.value = "All permissions granted"
        _errorMessage.value = ""
        updatePermissionLists()
        Log.d(TAG, "All permissions granted")
        _isLoading.value = false
    }

    fun onPermissionsDenied() {
        _permissionStatus.value = "Permissions denied"
        _errorMessage.value = "Denied permissions: ${_deniedPermissions.value.joinToString(", ")}"
        updatePermissionLists()
        Log.d(TAG, "Permissions denied. ${_errorMessage.value}")
    }

    fun requestPermissionsTriggered() {
        _permissionStatus.value = "Requesting..."
        _errorMessage.value = ""
    }

    fun fetchSSID() {
        _isLoading.value = true
        _ssid.value = "Resolving..."
        _errorMessage.value = ""

        viewModelScope.launch {
            try {
                val fetchedSSID = coreResolver.fetchSSID()
                _ssid.value = fetchedSSID
                _errorMessage.value = ""
                Log.d(TAG, "SSID fetch successful: $fetchedSSID")
            } catch (e: MissingPermissionException) {
                _ssid.value = "Unknown"
                _errorMessage.value = e.message ?: "${e.message}"
                Log.e(TAG, "Permission Exception: ${e.message}")
                Log.d(TAG, "Set error message to: ${_errorMessage.value}")
            } catch (e: SecurityException) {
                _ssid.value = "Unknown"
                _errorMessage.value = "Missing required permissions"
                Log.e(TAG, "Security Exception: ${e.message}")
                Log.d(TAG, "Set error message to: ${_errorMessage.value}")
            } catch (e: Exception) {
                _ssid.value = "Unknown"
                _errorMessage.value = "Error getting SSID: ${e.message}"
                Log.e(TAG, "General Exception: ${e.message}")
                Log.d(TAG, "Set error message to: ${_errorMessage.value}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SSIDResolverViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SSIDResolverViewModel(
                    CoreSSIDResolver(context, PermissionHandler(context)),
                    PermissionHandler(context)
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    companion object {
        private const val TAG = "SSIDViewModel"
    }
}