package com.raoulsson.ssidresolverandroid.view

import android.annotation.SuppressLint
import android.content.ContentValues.TAG
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.raoulsson.ssidresolverandroid.core.PermissionHandler
import com.raoulsson.ssidresolverandroid.core.SSIDResolverViewModel
import com.raoulsson.ssidresolverandroid.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: SSIDResolverViewModel by viewModels {
        SSIDResolverViewModel.Factory(this)
    }

    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { itx ->
            itx.value
        }
        if (allGranted) {
            viewModel.onPermissionsGranted()
        } else {
            viewModel.onPermissionsDenied()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupObservers()
        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.getSSIDButton.setOnClickListener {
            viewModel.fetchSSID()
        }

        binding.requestPermissionButton.setOnClickListener {
            viewModel.requestPermissionsTriggered()
            requestPermissions()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Collect loading state
                launch {
                    viewModel.isLoading.collect { isLoading ->
                        binding.getSSIDButton.isEnabled = !isLoading
                        binding.requestPermissionButton.isEnabled = !isLoading
                        if (isLoading) {
                            binding.resultTextView.text = "Resolving..."
                        }
                    }
                }

                // Collect SSID updates
                launch {
                    viewModel.ssid.collect { ssid ->
                        binding.resultTextView.text = ssid
                    }
                }

                // Collect permission status updates
                launch {
                    viewModel.permissionStatus.collect { status ->
                        binding.permissionStatusTextView.text = status
                    }
                }

                // Collect error messages
                launch {
                    viewModel.errorMessage.collect { error ->
                        Log.d(TAG, "MainActivity: Error message collected: '$error'")
                        if (error.isNotEmpty()) {
                            Log.d(TAG, "Setting error text and making visible")
                            binding.errorTextView.text = error
                            binding.errorTextView.visibility = android.view.View.VISIBLE
                        } else {
                            Log.d(TAG, "Clearing error and hiding")
                            binding.errorTextView.text = ""
                            binding.errorTextView.visibility = android.view.View.GONE
                        }
                    }
                }

                // Collect granted permissions
                launch {
                    viewModel.grantedPermissions.collect { permissions ->
                        binding.grantedPermissionsList.text = if (permissions.isEmpty()) {
                            "None"
                        } else {
                            permissions.joinToString("\n")
                        }
                    }
                }

                // Collect denied permissions
                launch {
                    viewModel.deniedPermissions.collect { permissions ->
                        binding.deniedPermissionsList.text = if (permissions.isEmpty()) {
                            "None"
                        } else {
                            permissions.joinToString("\n")
                        }
                    }
                }
            }
        }
    }

    private fun requestPermissions() {
        permissionRequest.launch(
            PermissionHandler.requiredPermissions.toTypedArray()
        )
    }
}