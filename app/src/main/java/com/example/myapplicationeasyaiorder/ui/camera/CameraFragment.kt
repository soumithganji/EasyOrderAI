package com.example.myapplicationeasyaiorder.ui.camera

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplicationeasyaiorder.R
import com.example.myapplicationeasyaiorder.databinding.FragmentCameraBinding
import com.example.myapplicationeasyaiorder.ui.EasyOrderViewModelFactory
import com.example.myapplicationeasyaiorder.ui.chat.PendingCartAdapter
import com.example.myapplicationeasyaiorder.ui.chat.PendingCartItem
import java.io.ByteArrayOutputStream
import java.io.File

class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ScanViewModel by viewModels {
        EasyOrderViewModelFactory(requireContext())
    }

    private var tempImageUri: Uri? = null
    private var confirmDialog: AlertDialog? = null

    // Permission request
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(context, "Camera permission required to take photos", Toast.LENGTH_SHORT).show()
        }
    }

    // Camera capture
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempImageUri != null) {
            processImage(tempImageUri!!)
        }
    }

    // Gallery picker
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

        setupButtons()
        observeViewModel()
    }

    private fun setupButtons() {
        binding.takePhotoButton.setOnClickListener {
            checkCameraPermissionAndLaunch()
        }

        binding.uploadButton.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.resetButton.setOnClickListener {
            viewModel.reset()
            resetUI()
        }
    }

    private fun observeViewModel() {
        viewModel.statusMessage.observe(viewLifecycleOwner) { message ->
            binding.statusText.text = message
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.loadingBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.takePhotoButton.isEnabled = !isLoading
            binding.uploadButton.isEnabled = !isLoading
        }

        viewModel.capturedImageBase64.observe(viewLifecycleOwner) { base64 ->
            if (base64 != null) {
                binding.resetButton.visibility = View.VISIBLE
            } else {
                binding.resetButton.visibility = View.GONE
                resetUI()
            }
        }

        viewModel.pendingItems.observe(viewLifecycleOwner) { items ->
            if (items != null && items.isNotEmpty()) {
                showConfirmDialog(items, viewModel.unavailableItems.value ?: emptyList())
            } else {
                confirmDialog?.dismiss()
            }
        }

        viewModel.resultMessage.observe(viewLifecycleOwner) { message ->
            if (message != null) {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                viewModel.clearResultMessage()
            }
        }
    }

    private fun checkCameraPermissionAndLaunch() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                launchCamera()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                Toast.makeText(context, "Camera permission needed to take photos", Toast.LENGTH_LONG).show()
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun launchCamera() {
        try {
            // Create temp file for camera capture
            val photoFile = File.createTempFile(
                "grocery_list_",
                ".jpg",
                requireContext().cacheDir
            )
            
            tempImageUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                photoFile
            )
            
            takePictureLauncher.launch(tempImageUri)
        } catch (e: Exception) {
            android.util.Log.e("CameraFragment", "Error launching camera", e)
            Toast.makeText(context, "Error launching camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processImage(uri: Uri) {
        try {
            // Show image preview
            binding.imagePreview.setImageURI(uri)
            binding.imagePreview.visibility = View.VISIBLE
            binding.placeholderLayout.visibility = View.GONE

            // Convert to Base64
            val base64 = uriToBase64(uri)
            if (base64 != null) {
                viewModel.setImageBase64(base64)
            } else {
                Toast.makeText(context, "Error processing image", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("CameraFragment", "Error processing image", e)
            Toast.makeText(context, "Error processing image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uriToBase64(uri: Uri): String? {
        return try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            // Resize if too large - use higher resolution for better OCR
            val maxSize = 1536  // Increased from 1024 for better text recognition
            val scaledBitmap = if (bitmap.width > maxSize || bitmap.height > maxSize) {
                val ratio = minOf(maxSize.toFloat() / bitmap.width, maxSize.toFloat() / bitmap.height)
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * ratio).toInt(),
                    (bitmap.height * ratio).toInt(),
                    true
                )
            } else {
                bitmap
            }

            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 92, outputStream)  // Higher quality
            val bytes = outputStream.toByteArray()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            android.util.Log.e("CameraFragment", "Error converting URI to Base64", e)
            null
        }
    }

    private fun resetUI() {
        binding.imagePreview.visibility = View.GONE
        binding.placeholderLayout.visibility = View.VISIBLE
        binding.imagePreview.setImageDrawable(null)
    }

    private fun showConfirmDialog(items: List<PendingCartItem>, unavailable: List<String>) {
        confirmDialog?.dismiss()

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_confirm_items, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.pendingItemsRecyclerView)

        val adapter = PendingCartAdapter(
            onQuantityChange = { item, newQty ->
                viewModel.updatePendingItemQuantity(item.productId, newQty)
            },
            onRemove = { item ->
                viewModel.removePendingItem(item.productId)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        adapter.submitList(items.toList())

        // Add unavailable items text if any
        if (unavailable.isNotEmpty()) {
            val unavailableText = TextView(requireContext()).apply {
                text = "⚠️ Not found: ${unavailable.joinToString(", ")}"
                setPadding(32, 16, 32, 0)
                setTextColor(resources.getColor(android.R.color.holo_orange_dark, null))
            }
            (dialogView as ViewGroup).addView(unavailableText, 1)
        }

        confirmDialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialogView.findViewById<View>(R.id.cancelButton).setOnClickListener {
            viewModel.cancelPendingItems()
            confirmDialog?.dismiss()
        }

        dialogView.findViewById<View>(R.id.confirmButton).setOnClickListener {
            viewModel.confirmPendingItems()
            confirmDialog?.dismiss()
        }

        // Update adapter when pending items change
        viewModel.pendingItems.observe(viewLifecycleOwner) { updatedItems ->
            if (updatedItems == null || updatedItems.isEmpty()) {
                confirmDialog?.dismiss()
            } else {
                adapter.submitList(updatedItems.toList())
            }
        }

        confirmDialog?.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        confirmDialog?.dismiss()
        _binding = null
    }
}
