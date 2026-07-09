package com.docscanner.app.ui.compress

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.docscanner.app.R
import com.docscanner.app.compress.DocumentCompressor
import com.docscanner.app.databinding.FragmentFileCompressBinding
import com.docscanner.app.export.CompressionResult
import com.docscanner.app.export.ExportManager
import com.docscanner.app.ui.utils.UiEffects
import com.google.android.material.slider.Slider
import kotlinx.coroutines.launch
import java.io.File

class FileCompressFragment : Fragment() {

    private var _binding: FragmentFileCompressBinding? = null
    private val binding get() = _binding!!
    private val compressor by lazy { DocumentCompressor(requireContext()) }
    private val exportManager by lazy { ExportManager(requireContext()) }
    private var selectedUri: Uri? = null
    private var compressionResult: CompressionResult? = null

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        selectedUri = uri
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        binding.txtSelectedFile.text = uri.lastPathSegment ?: getString(R.string.select_file)
        binding.btnCompress.isEnabled = true
        compressionResult = null
        binding.txtCompressionResult.visibility = View.GONE
        binding.btnDownload.visibility = View.GONE
        binding.btnShare.visibility = View.GONE
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFileCompressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        var quality = 65
        binding.sliderQuality.value = quality.toFloat()
        updateQualityLabel(quality)

        binding.sliderQuality.addOnChangeListener { _: Slider, value, _ ->
            quality = value.toInt()
            updateQualityLabel(quality)
        }

        UiEffects.bindClick(binding.btnBack) {
            findNavController().popBackStack()
        }
        UiEffects.bindClick(binding.btnPickFile) {
            pickFileLauncher.launch(arrayOf("application/pdf", "image/*"))
        }
        UiEffects.bindClick(binding.cardUpload) {
            pickFileLauncher.launch(arrayOf("application/pdf", "image/*"))
        }
        UiEffects.bindClick(binding.btnCompress) {
            val uri = selectedUri ?: return@bindClick
            runCompression(uri, quality)
        }
        UiEffects.bindClick(binding.btnDownload) {
            saveToDownloads()
        }
        UiEffects.bindClick(binding.btnShare) {
            shareCompressed()
        }
    }

    private fun updateQualityLabel(quality: Int) {
        binding.txtQualityLabel.text = getString(R.string.quality_label, quality)
    }

    private fun runCompression(uri: Uri, quality: Int) {
        binding.compressProgress.visibility = View.VISIBLE
        binding.btnCompress.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { compressor.compress(uri, quality) }
            binding.compressProgress.visibility = View.GONE
            binding.btnCompress.isEnabled = true
            result.fold(
                onSuccess = { compression ->
                    compressionResult = compression
                    binding.txtCompressionResult.visibility = View.VISIBLE
                    binding.txtCompressionResult.text = getString(
                        R.string.compression_saved,
                        exportManager.formatSize(compression.originalSizeBytes),
                        exportManager.formatSize(compression.compressedSizeBytes),
                        compression.savingsPercent()
                    )
                    binding.btnDownload.visibility = View.VISIBLE
                    binding.btnShare.visibility = View.VISIBLE
                    binding.btnDownload.isEnabled = true
                    binding.btnShare.isEnabled = true
                    Toast.makeText(requireContext(), R.string.conversion_success, Toast.LENGTH_SHORT)
                        .show()
                },
                onFailure = {
                    Toast.makeText(requireContext(), R.string.compression_failed, Toast.LENGTH_LONG)
                        .show()
                }
            )
        }
    }

    private fun saveToDownloads() {
        val result = compressionResult ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val ext = result.file.extension.lowercase()
            val mime = when (ext) {
                "pdf" -> "application/pdf"
                "png" -> "image/png"
                else -> "image/jpeg"
            }
            val saved = exportManager.saveConvertedFileToDownloads(result.file, ext, mime)
            Toast.makeText(
                requireContext(),
                if (saved != null) R.string.saved_to_downloads else R.string.export_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun shareCompressed() {
        val file = compressionResult?.file ?: return
        val ext = file.extension.lowercase()
        val mime = when (ext) {
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            else -> "image/jpeg"
        }
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_converted)))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun CompressionResult.savingsPercent(): Int {
        if (originalSizeBytes <= 0) return 0
        val saved = originalSizeBytes - compressedSizeBytes
        return ((saved * 100) / originalSizeBytes).toInt().coerceAtLeast(0)
    }
}
