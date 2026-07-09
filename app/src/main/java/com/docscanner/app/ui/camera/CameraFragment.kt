package com.docscanner.app.ui.camera

import android.Manifest
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.docscanner.app.R
import com.docscanner.app.camera.CameraController
import com.docscanner.app.camera.FlashMode
import com.docscanner.app.databinding.FragmentCameraBinding
import com.docscanner.app.ui.utils.UiEffects
import com.docscanner.app.viewmodel.ScanSessionViewModel
import kotlinx.coroutines.launch

class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ScanSessionViewModel by activityViewModels()
    private var cameraController: CameraController? = null
    private var navigatedToCrop = false

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        lifecycleScope.launch {
            val bitmaps = com.docscanner.app.utils.BitmapLoader.loadUris(requireContext(), uris)
            if (bitmaps.isEmpty()) {
                Toast.makeText(requireContext(), R.string.export_failed, Toast.LENGTH_SHORT).show()
            } else {
                viewModel.beginCropAdjust(bitmaps.first())
                navigateToCropWhenReady()
            }
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else {
            Toast.makeText(requireContext(), R.string.camera_permission_required, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        UiEffects.slideUpFade(binding.topBar, 100)
        UiEffects.pulseGlow(binding.captureGlowRing)
        UiEffects.rotateSlow(binding.captureGlowRing)

        binding.documentOverlay.visibility = View.GONE
        binding.holdSteadyLayout.visibility = View.GONE
        binding.txtDetecting.visibility = View.GONE
        binding.multiPageBar.visibility = View.GONE

        UiEffects.bindClick(binding.btnFlash) {
            cameraController?.cycleFlash() ?: FlashMode.OFF
        }
        UiEffects.bindClick(binding.btnGallery) {
            galleryLauncher.launch(arrayOf("image/*"))
        }
        UiEffects.bindClick(binding.btnCapture) { capturePhoto() }

        if (hasCameraPermission()) startCamera()
        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.visibility =
                        if (state.isProcessing) View.VISIBLE else View.GONE

                    if (state.cropAdjust != null && !navigatedToCrop) {
                        navigateToCropWhenReady()
                    }
                }
            }
        }
    }

    private fun capturePhoto() {
        navigatedToCrop = false
        cameraController?.capture { bitmap ->
            bitmap?.let {
                playShutter()
                viewModel.beginCropAdjust(it)
            }
        }
    }

    private fun navigateToCropWhenReady() {
        if (navigatedToCrop || !isAdded) return
        val state = viewModel.uiState.value
        if (state.cropAdjust != null && !state.isProcessing) {
            navigatedToCrop = true
            findNavController().navigate(R.id.action_camera_to_crop)
        }
    }

    override fun onResume() {
        super.onResume()
        navigatedToCrop = false
    }

    private fun startCamera() {
        cameraController?.stop()
        cameraController = CameraController(
            requireContext(),
            viewLifecycleOwner,
            binding.previewView,
            onDetection = { },
            onAutoCaptureReady = { }
        ).also {
            it.autoCaptureEnabled = false
            it.enableAnalysis = false
            it.start()
        }
    }

    private fun playShutter() {
        binding.shutterFlash.alpha = 0.85f
        ObjectAnimator.ofFloat(binding.shutterFlash, View.ALPHA, 0.85f, 0f).apply {
            duration = 180
            start()
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    override fun onDestroyView() {
        cameraController?.stop()
        cameraController = null
        navigatedToCrop = false
        super.onDestroyView()
        _binding = null
    }
}
