package com.docscanner.app.ui.crop

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.docscanner.app.R
import com.docscanner.app.databinding.FragmentCropAdjustBinding
import com.docscanner.app.domain.model.CaptureFlowPhase
import com.docscanner.app.domain.model.Point2D
import com.docscanner.app.ui.utils.UiEffects
import com.docscanner.app.viewmodel.ScanSessionViewModel
import kotlinx.coroutines.launch

class CropAdjustFragment : Fragment() {

    private var _binding: FragmentCropAdjustBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ScanSessionViewModel by activityViewModels()

    private var lastAdjustKey: String? = null
    private var isLeaving = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCropAdjustBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        UiEffects.slideUpFade(binding.previewCard, 100)

        binding.cropOverlay.onCornersChanged = { corners ->
            viewModel.updateCropAdjustCorners(corners)
        }

        UiEffects.bindClick(binding.btnDiscard) {
            if (isLeaving) return@bindClick
            isLeaving = true
            viewModel.discardCropAdjust()
            findNavController().navigate(R.id.action_crop_to_camera)
        }

        UiEffects.bindClick(binding.btnDone) {
            viewModel.confirmCropAdjust()
        }

        UiEffects.bindClick(binding.btnKeepScanning) {
            if (isLeaving) return@bindClick
            isLeaving = true
            viewModel.keepScanningAfterPreview()
            findNavController().navigate(R.id.action_crop_to_camera)
        }

        UiEffects.bindClick(binding.btnExport) {
            if (isLeaving) return@bindClick
            isLeaving = true
            viewModel.exportAfterPreview {
                if (!isAdded) return@exportAfterPreview
                findNavController().navigate(R.id.action_crop_to_editor)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progress.visibility =
                        if (state.isProcessing) View.VISIBLE else View.GONE

                    val session = state.cropAdjust ?: return@collect

                    when (session.phase) {
                        CaptureFlowPhase.ADJUST_EDGES -> {
                            val key = "${session.captureBitmap.width}x${session.captureBitmap.height}-${session.corners.hashCode()}"
                            if (key != lastAdjustKey) {
                                lastAdjustKey = key
                                showAdjustPhase(session.captureBitmap, session.corners)
                            }
                        }
                        CaptureFlowPhase.SCAN_PREVIEW -> {
                            lastAdjustKey = null
                            showPreviewPhase(session.scannedPreview)
                        }
                    }
                }
            }
        }
    }

    private fun showAdjustPhase(bitmap: Bitmap, corners: List<Point2D>) {
        binding.txtTitle.text = getString(R.string.adjust_edges_title)
        binding.panelAdjust.visibility = View.VISIBLE
        binding.panelPreview.visibility = View.GONE
        binding.cropOverlay.visibility = View.VISIBLE
        binding.cropOverlay.isClickable = true

        if (!bitmap.isRecycled) {
            binding.imgPreview.setImageDrawable(null)
            binding.imgPreview.setImageBitmap(bitmap)
        }

        binding.previewCard.post {
            if (_binding == null || bitmap.isRecycled) return@post
            binding.cropOverlay.setImageSize(bitmap.width, bitmap.height)
            if (corners.size == 4) {
                binding.cropOverlay.setCornersPixels(corners)
            }
        }
    }

    private fun showPreviewPhase(scanned: Bitmap?) {
        binding.txtTitle.text = getString(R.string.scan_preview_title)
        binding.panelAdjust.visibility = View.GONE
        binding.panelPreview.visibility = View.VISIBLE
        binding.cropOverlay.visibility = View.GONE

        scanned?.let { bmp ->
            if (!bmp.isRecycled) {
                binding.imgPreview.setImageDrawable(null)
                binding.imgPreview.setImageBitmap(bmp)
            }
        }
    }

    override fun onDestroyView() {
        lastAdjustKey = null
        isLeaving = false
        super.onDestroyView()
        _binding = null
    }
}
