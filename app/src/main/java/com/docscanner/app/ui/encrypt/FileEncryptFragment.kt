package com.docscanner.app.ui.encrypt

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.docscanner.app.databinding.FragmentFileEncryptBinding
import com.docscanner.app.export.ExportManager
import com.docscanner.app.pdf.PdfEncryptor
import com.docscanner.app.ui.utils.UiEffects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class FileEncryptFragment : Fragment() {

    private var _binding: FragmentFileEncryptBinding? = null
    private val binding get() = _binding!!
    private var sourceFile: File? = null
    private var encryptedFile: File? = null
    private val encryptor by lazy { PdfEncryptor(requireContext()) }
    private val exportManager by lazy { ExportManager(requireContext()) }

    private val pickPdf = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            sourceFile = copyPdf(uri)
            binding.txtSelectedFile.text = sourceFile?.name ?: getString(R.string.export_failed)
            binding.btnEncrypt.isEnabled = sourceFile != null
            encryptedFile = null
            binding.btnDownload.visibility = View.GONE
            binding.btnShare.visibility = View.GONE
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFileEncryptBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        UiEffects.bindClick(binding.btnBack) { findNavController().popBackStack() }
        UiEffects.bindClick(binding.btnPickPdf) {
            pickPdf.launch(arrayOf("application/pdf"))
        }
        UiEffects.bindClick(binding.btnEncrypt) { encrypt() }
        UiEffects.bindClick(binding.btnDownload) { saveFile() }
        UiEffects.bindClick(binding.btnShare) { shareFile() }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = updateStrength()
        }
        binding.inputPassword.addTextChangedListener(watcher)
        binding.inputConfirm.addTextChangedListener(watcher)
    }

    private fun updateStrength() {
        val pwd = binding.inputPassword.text?.toString().orEmpty()
        binding.txtStrength.text = when {
            pwd.length < 6 -> getString(R.string.password_weak)
            pwd.length < 10 -> getString(R.string.password_medium)
            else -> getString(R.string.password_strong)
        }
    }

    private suspend fun copyPdf(uri: Uri): File? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(requireContext().cacheDir, "encrypt").apply { mkdirs() }
            val dest = File(dir, "source_${System.currentTimeMillis()}.pdf")
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            dest
        }.getOrNull()
    }

    private fun encrypt() {
        val source = sourceFile ?: return
        val pwd = binding.inputPassword.text?.toString().orEmpty()
        val confirm = binding.inputConfirm.text?.toString().orEmpty()
        when {
            pwd.length < 6 -> Toast.makeText(requireContext(), R.string.password_too_short, Toast.LENGTH_SHORT).show()
            pwd != confirm -> Toast.makeText(requireContext(), R.string.password_mismatch, Toast.LENGTH_SHORT).show()
            else -> runEncrypt(source, pwd)
        }
    }

    private fun runEncrypt(source: File, password: String) {
        binding.encryptProgress.visibility = View.VISIBLE
        binding.btnEncrypt.isEnabled = false
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val out = File(requireContext().cacheDir, "encrypted_${System.currentTimeMillis()}.pdf")
                    encryptor.encrypt(source, password, out)
                    out
                }
            }
            binding.inputPassword.text?.clear()
            binding.inputConfirm.text?.clear()
            binding.encryptProgress.visibility = View.GONE
            binding.btnEncrypt.isEnabled = true
            result.fold(
                onSuccess = { file ->
                    encryptedFile = file
                    binding.btnDownload.visibility = View.VISIBLE
                    binding.btnShare.visibility = View.VISIBLE
                    binding.btnDownload.isEnabled = true
                    binding.btnShare.isEnabled = true
                    Toast.makeText(requireContext(), R.string.encrypt_success, Toast.LENGTH_SHORT).show()
                },
                onFailure = {
                    Toast.makeText(requireContext(), R.string.encrypt_failed, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun saveFile() {
        val file = encryptedFile ?: return
        lifecycleScope.launch {
            val saved = exportManager.saveConvertedFileToDownloads(file, "pdf", "application/pdf")
            Toast.makeText(
                requireContext(),
                if (saved != null) R.string.saved_to_downloads else R.string.export_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun shareFile() {
        val file = encryptedFile ?: return
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file
        )
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                getString(R.string.share_converted)
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
