package com.docscanner.app.ui.merge

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.docscanner.app.R
import com.docscanner.app.databinding.FragmentFileMergeBinding
import com.docscanner.app.export.ExportManager
import com.docscanner.app.pdf.PdfMerger
import com.docscanner.app.ui.utils.UiEffects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class FileMergeFragment : Fragment() {

    private var _binding: FragmentFileMergeBinding? = null
    private val binding get() = _binding!!
    private val pdfFiles = mutableListOf<File>()
    private lateinit var adapter: MergePdfAdapter
    private val merger by lazy { PdfMerger(requireContext()) }
    private val exportManager by lazy { ExportManager(requireContext()) }
    private var mergedFile: File? = null

    private val pickPdfs = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        lifecycleScope.launch {
            binding.mergeProgress.visibility = View.VISIBLE
            val newFiles = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri -> copyPdf(uri) }
            }
            pdfFiles.addAll(newFiles)
            binding.mergeProgress.visibility = View.GONE
            refreshList()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFileMergeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        adapter = MergePdfAdapter(pdfFiles) { index ->
            pdfFiles.removeAt(index)
            refreshList()
        }
        binding.pdfList.layoutManager = LinearLayoutManager(requireContext())
        binding.pdfList.adapter = adapter
        ItemTouchHelper(MergeDragCallback(adapter) { from, to ->
            val item = pdfFiles.removeAt(from)
            pdfFiles.add(to, item)
            adapter.notifyItemMoved(from, to)
            refreshList()
        }).attachToRecyclerView(binding.pdfList)

        UiEffects.bindClick(binding.btnBack) { findNavController().popBackStack() }
        
        val triggerPicker = { pickPdfs.launch(arrayOf("application/pdf")) }
        UiEffects.bindClick(binding.btnAddPdfs, triggerPicker)
        UiEffects.bindClick(binding.cardUpload, triggerPicker)
        
        UiEffects.bindClick(binding.btnMerge) { mergePdfs() }
        UiEffects.bindClick(binding.btnDownload) { saveMerged() }
        UiEffects.bindClick(binding.btnShare) { shareMerged() }
        
        refreshList()
    }

    private suspend fun copyPdf(uri: Uri): File? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(requireContext().cacheDir, "merge").apply { mkdirs() }
            val name = getFileName(uri) ?: "doc_${System.currentTimeMillis()}.pdf"
            val dest = File(dir, name)
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            if (dest.exists() && dest.length() > 0) dest else null
        }.onFailure { Log.e("FileMerge", "Error copying", it) }.getOrNull()
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            runCatching {
                val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1) result = it.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            val path = uri.path
            if (path != null) {
                val cut = path.lastIndexOf('/')
                result = if (cut != -1) path.substring(cut + 1) else path
            }
        }
        return result
    }

    private fun refreshList() {
        adapter.notifyDataSetChanged()
        val count = pdfFiles.size
        binding.btnMerge.isEnabled = count >= 2
        
        if (count == 0) {
            binding.txtSelectedFile.text = getString(R.string.add_pdfs)
            binding.btnAddPdfs.text = getString(R.string.select_file)
        } else {
            binding.txtSelectedFile.text = getString(R.string.add_more_count, count)
            binding.btnAddPdfs.text = getString(R.string.add_pdfs)
        }
    }

    private fun mergePdfs() {
        if (pdfFiles.size < 2) return
        
        binding.mergeProgress.visibility = View.VISIBLE
        binding.btnMerge.isEnabled = false
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val outDir = File(requireContext().filesDir, "exports").apply { mkdirs() }
                    val out = File(outDir, "Merged_${System.currentTimeMillis()}.pdf")
                    merger.merge(pdfFiles, out)
                    out
                }
            }
            binding.mergeProgress.visibility = View.GONE
            binding.btnMerge.isEnabled = true
            result.fold(
                onSuccess = { file ->
                    mergedFile = file
                    binding.btnDownload.visibility = View.VISIBLE
                    binding.btnShare.visibility = View.VISIBLE
                    Toast.makeText(requireContext(), "Merged successfully!", Toast.LENGTH_SHORT).show()
                },
                onFailure = { e ->
                    Log.e("FileMerge", "Merge failed", e)
                    Toast.makeText(requireContext(), "Merge failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun saveMerged() {
        val file = mergedFile ?: return
        lifecycleScope.launch {
            val saved = exportManager.saveConvertedFileToDownloads(file, "pdf", "application/pdf")
            Toast.makeText(
                requireContext(),
                if (saved != null) R.string.saved_to_downloads else R.string.export_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun shareMerged() {
        val file = mergedFile ?: return
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
                "Share Merged Document"
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private class MergePdfAdapter(
    private val files: List<File>,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<MergePdfAdapter.Holder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_merge_pdf, parent, false)
        return Holder(v)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(files[position])
    }

    override fun getItemCount(): Int = files.size

    inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(file: File) {
            itemView.findViewById<android.widget.TextView>(R.id.txtFileName).text = file.name
            itemView.findViewById<View>(R.id.btnRemove).setOnClickListener { 
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onRemove(adapterPosition)
                }
            }
        }
    }
}

private class MergeDragCallback(
    private val adapter: MergePdfAdapter,
    private val onMove: (Int, Int) -> Unit
) : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
    override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
        val from = vh.adapterPosition
        val to = target.adapterPosition
        if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
        onMove(from, to)
        return true
    }
    override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) = Unit
    override fun isLongPressDragEnabled(): Boolean = true
}
