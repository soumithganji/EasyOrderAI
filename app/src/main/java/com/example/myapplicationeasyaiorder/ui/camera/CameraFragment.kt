package com.example.myapplicationeasyaiorder.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.myapplicationeasyaiorder.databinding.FragmentCameraBinding

class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    // Permission request
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(context, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    // Camera capture
    private var tempImageUri: Uri? = null
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempImageUri != null) {
            processImage(tempImageUri!!)
        }
    }

    // Gallery picker (alternative)
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            processImage(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.captureButton.setOnClickListener {
            // For simplicity, use gallery picker
            // (Camera with CameraX requires more setup)
            pickImageLauncher.launch("image/*")
        }
    }

    private fun launchCamera() {
        // Create temp file URI for camera capture
        // For now, using gallery picker is simpler
        Toast.makeText(context, "Camera capture coming soon. Using gallery for now.", Toast.LENGTH_SHORT).show()
        pickImageLauncher.launch("image/*")
    }

    private fun processImage(uri: Uri) {
        Toast.makeText(context, "Image selected! AI processing coming soon...", Toast.LENGTH_LONG).show()
        
        // TODO: Send image to AI (NVIDIA NIM VLM) for processing
        // 1. Convert URI to Base64
        // 2. Send to AI endpoint
        // 3. Parse response for item names
        // 4. Add items to cart
        
        // For now, show placeholder
        binding.viewFinder.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

