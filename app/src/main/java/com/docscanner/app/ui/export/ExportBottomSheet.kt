package com.docscanner.app.ui.export

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.docscanner.app.R
import com.docscanner.app.databinding.BottomSheetExportBinding
import com.docscanner.app.export.CompressionResult
import com.docscanner.app.ui.utils.UiEffects
import com.docscanner.app.viewmodel.ScanSessionViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.slider.Slider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ExportBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetExportBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ScanSessionViewModel by activityViewModels()
    private var lastCompressedFile: File? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetExportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        UiEffects.slideUpFade(binding.root, 0)
        UiEffects.bounceIn(binding.btnCompressDownload)

        var quality = 65
        binding.sliderQuality.value = quality.toFloat()
        updateQualityLabel(quality)

        binding.sliderQuality.addOnChangeListener { _: Slider, value, _ ->
            quality = value.toInt()
            updateQualityLabel(quality)
        }

        UiEffects.applyFloatingPress(binding.btnCompressDownload) {
            compressAndDownload(quality)
        }
        UiEffects.applyFloatingPress(binding.btnDownloadCompressed) {
            val file = lastCompressedFile
            if (file != null && file.exists()) sharePdf(file)
            else toast(R.string.export_failed)
        }

        binding.btnExportPdf.setOnClickListener {
            exportPdf(saveToDownloads = false) { it?.let { sharePdf(it) } }
        }
        binding.btnSaveDevice.setOnClickListener {
            exportPdf(saveToDownloads = true) {
                toast(if (it != null) R.string.saved_to_downloads else R.string.export_failed)
            }
        }
        binding.btnSharePdf.setOnClickListener {
            exportPdf(saveToDownloads = false) { it?.let { sharePdf(it) } }
        }
        binding.btnShareImages.setOnClickListener {
            viewModel.exportJpegs(saveToDownloads = false) { files ->
                if (!isAdded) return@exportJpegs
                if (files.isEmpty()) toast(R.string.export_failed)
                else shareMultiple(files, "image/jpeg")
            }
        }
        binding.btnExportJpg.setOnClickListener {
            viewModel.compressAndDownloadJpegs(quality) { results ->
                if (!isAdded) return@compressAndDownloadJpegs
                if (results.isEmpty()) toast(R.string.export_failed)
                else {
                    showCompressionStats(results.first())
                    toast(R.string.saved_to_downloads)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (!isAdded || _binding == null) return@collect
                    binding.exportProgress.visibility =
                        if (state.isProcessing) View.VISIBLE else View.GONE
                    state.lastCompression?.let { showCompressionStats(it) }
                }
            }
        }
    }

    private fun compressAndDownload(quality: Int) {
        if (!isAdded) return
        binding.exportProgress.visibility = View.VISIBLE
        viewModel.compressAndDownloadPdf(quality) { result ->
            if (!isAdded || _binding == null) return@compressAndDownloadPdf
            binding.exportProgress.visibility = View.GONE
            if (result == null) {
                toast(R.string.export_failed)
                return@compressAndDownloadPdf
            }
            lastCompressedFile = result.file
            showCompressionStats(result)
            binding.btnDownloadCompressed.visibility = View.VISIBLE
            toast(R.string.saved_to_downloads)
        }
    }

    private fun showCompressionStats(result: CompressionResult) {
        lastCompressedFile = result.file
        binding.txtCompressionStats.visibility = View.VISIBLE
        binding.txtCompressionStats.text = getString(
            R.string.compression_saved,
            viewModel.formatSize(result.originalSizeBytes),
            viewModel.formatSize(result.compressedSizeBytes),
            result.savedPercent
        )
    }

    private fun updateQualityLabel(quality: Int) {
        binding.txtQualityLabel.text = getString(R.string.quality_label, quality)
    }

    private fun exportPdf(saveToDownloads: Boolean, onDone: (File?) -> Unit) {
        binding.exportProgress.visibility = View.VISIBLE
        viewModel.exportPdf(saveToDownloads) { file ->
            if (!isAdded || _binding == null) return@exportPdf
            binding.exportProgress.visibility = View.GONE
            if (file == null) toast(R.string.no_pages) else onDone(file)
        }
    }

    private fun sharePdf(file: File) {
        if (!file.exists()) {
            toast(R.string.export_failed)
            return
        }
        lifecycleScope.launch {
            val uri = withContext(Dispatchers.IO) {
                runCatching { viewModel.getShareUri(file) }.getOrNull()
            } ?: return@launch
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_pdf)))
        }
    }

    private fun shareMultiple(files: List<File>, mime: String) {
        lifecycleScope.launch {
            val uris = withContext(Dispatchers.IO) {
                files.filter { it.exists() }.mapNotNull { f ->
                    runCatching { viewModel.getShareUri(f) }.getOrNull()
                }
            }
            if (uris.isEmpty()) {
                toast(R.string.export_failed)
                return@launch
            }
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mime
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_images)))
        }
    }

    private fun toast(res: Int) {
        if (isAdded) {
            Toast.makeText(requireContext(), res, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ExportBottomSheet"
    }
}
