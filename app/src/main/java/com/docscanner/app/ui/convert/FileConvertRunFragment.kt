package com.docscanner.app.ui.convert

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
import com.docscanner.app.convert.ConversionType
import com.docscanner.app.convert.FileConverter
import com.docscanner.app.databinding.FragmentFileConvertRunBinding
import com.docscanner.app.export.ExportManager
import com.docscanner.app.ui.utils.UiEffects
import kotlinx.coroutines.launch
import java.io.File

class FileConvertRunFragment : Fragment() {

    private var _binding: FragmentFileConvertRunBinding? = null
    private val binding get() = _binding!!
    private val conversionType: ConversionType by lazy {
        ConversionType.valueOf(
            requireArguments().getString("conversionType")
                ?: ConversionType.IMAGE_TO_PDF.name
        )
    }
    private val converter by lazy { FileConverter(requireContext()) }
    private val exportManager by lazy { ExportManager(requireContext()) }
    private var convertedFile: File? = null
    private var selectedUri: Uri? = null

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
        startConversion(uri)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFileConvertRunBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.txtRunTitle.text = conversionType.title

        UiEffects.bindClick(binding.btnBack) {
            findNavController().popBackStack()
        }
        UiEffects.bindClick(binding.btnPickFile) {
            pickFileLauncher.launch(arrayOf("*/*"))
        }
        UiEffects.bindClick(binding.cardUpload) {
            pickFileLauncher.launch(arrayOf("*/*"))
        }
        UiEffects.bindClick(binding.btnDownload) {
            downloadConverted()
        }
        UiEffects.bindClick(binding.btnShare) {
            shareConverted()
        }
    }

    private fun startConversion(uri: Uri) {
        binding.convertProgress.visibility = View.VISIBLE
        binding.btnDownload.visibility = View.GONE
        binding.btnShare.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            val result = converter.convert(conversionType, uri)
            binding.convertProgress.visibility = View.GONE
            result.fold(
                onSuccess = { file ->
                    convertedFile = file
                    binding.btnDownload.visibility = View.VISIBLE
                    binding.btnShare.visibility = View.VISIBLE
                    binding.btnDownload.isEnabled = true
                    binding.btnShare.isEnabled = true
                    Toast.makeText(requireContext(), R.string.conversion_success, Toast.LENGTH_SHORT)
                        .show()
                },
                onFailure = {
                    Toast.makeText(requireContext(), R.string.conversion_failed, Toast.LENGTH_LONG)
                        .show()
                }
            )
        }
    }

    private fun downloadConverted() {
        val file = convertedFile ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val saved = exportManager.saveConvertedFileToDownloads(
                file,
                conversionType.outputExtension,
                conversionType.mimeType
            )
            Toast.makeText(
                requireContext(),
                if (saved != null) R.string.saved_to_downloads else R.string.export_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun shareConverted() {
        val file = convertedFile ?: return
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = conversionType.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_converted)))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
