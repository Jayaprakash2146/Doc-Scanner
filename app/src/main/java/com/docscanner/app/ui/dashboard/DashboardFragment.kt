package com.docscanner.app.ui.dashboard

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.docscanner.app.R
import com.docscanner.app.data.repository.RecentScanRepository
import com.docscanner.app.databinding.FragmentDashboardBinding
import com.docscanner.app.domain.model.RecentScanItem
import com.docscanner.app.ui.utils.UiEffects
import com.docscanner.app.utils.BitmapLoader
import com.docscanner.app.viewmodel.ScanSessionViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val scanViewModel: ScanSessionViewModel by activityViewModels()
    private lateinit var recentAdapter: RecentScanAdapter
    private val recentRepository by lazy { RecentScanRepository(requireContext()) }

    private val galleryImportLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        lifecycleScope.launch {
            val bitmaps = BitmapLoader.loadUris(requireContext(), uris)
            if (bitmaps.isEmpty()) return@launch
            scanViewModel.clearSession()
            scanViewModel.addImportedBitmaps(bitmaps) {
                if (!isAdded) return@addImportedBitmaps
                findNavController().navigate(R.id.action_dashboard_to_editor)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        UiEffects.slideUpFade(binding.txtDashboardTitle, 80)
        UiEffects.slideUpFade(binding.cardGallery, 120)

        recentAdapter = RecentScanAdapter(
            onClick = { item ->
                scanViewModel.restoreFromHistory(item.id) {
                    if (!isAdded) return@restoreFromHistory
                    findNavController().navigate(R.id.action_dashboard_to_editor)
                }
            },
            onLongClick = { item, _ -> showRecentFloatingMenu(item) }
        )
        binding.recentScansList.layoutManager = LinearLayoutManager(requireContext())
        binding.recentScansList.adapter = recentAdapter

        UiEffects.bindClick(binding.btnScan) {
            scanViewModel.clearSession()
            findNavController().navigate(R.id.action_dashboard_to_camera)
        }
        UiEffects.bindClick(binding.cardGallery) {
            galleryImportLauncher.launch(arrayOf("image/*"))
        }
        UiEffects.bindClick(binding.cardConvert) {
            findNavController().navigate(R.id.action_dashboard_to_convert)
        }
        UiEffects.bindClick(binding.cardCompress) {
            findNavController().navigate(R.id.action_dashboard_to_compress)
        }
        UiEffects.bindClick(binding.cardMerge) {
            findNavController().navigate(R.id.action_dashboard_to_merge)
        }
        UiEffects.bindClick(binding.cardEncrypt) {
            findNavController().navigate(R.id.action_dashboard_to_encrypt)
        }
    }

    private fun showRecentFloatingMenu(item: RecentScanItem) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_recent_menu, null)
        val dialog = AlertDialog.Builder(requireContext(), R.style.Theme_DocScanner_FloatingDialog)
            .setView(dialogView)
            .create()

        dialogView.findViewById<TextView>(R.id.txtMenuTitle).text = item.title

        dialogView.findViewById<View>(R.id.btnRename).setOnClickListener {
            dialog.dismiss()
            showRenameDialog(item)
        }
        dialogView.findViewById<View>(R.id.btnEdit).setOnClickListener {
            dialog.dismiss()
            scanViewModel.restoreFromHistory(item.id) {
                if (isAdded) findNavController().navigate(R.id.action_dashboard_to_editor)
            }
        }
        dialogView.findViewById<View>(R.id.btnShare).setOnClickListener {
            dialog.dismiss()
            shareRecent(item)
        }
        dialogView.findViewById<View>(R.id.btnDelete).setOnClickListener {
            dialog.dismiss()
            deleteRecent(item)
        }

        dialog.show()
        dialog.window?.let { window ->
            window.setLayout(
                (resources.displayMetrics.widthPixels * 0.85).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            window.setGravity(Gravity.CENTER)
            // Animation for entry
            window.setWindowAnimations(android.R.style.Animation_Dialog)
        }
    }

    private fun showRenameDialog(item: RecentScanItem) {
        val input = EditText(requireContext()).apply {
            setText(item.title)
            setSelection(text.length)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.rename)
            .setView(input)
            .setPositiveButton(R.string.done) { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    lifecycleScope.launch {
                        recentRepository.rename(item.id, name)
                        loadRecentScans()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteRecent(item: RecentScanItem) {
        lifecycleScope.launch {
            recentRepository.delete(item.id)
            loadRecentScans()
            Snackbar.make(binding.root, R.string.deleted, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun shareRecent(item: RecentScanItem) {
        lifecycleScope.launch {
            val pdf = recentRepository.getShareablePdfFile(item)
            if (pdf != null) {
                shareFile(pdf, "application/pdf")
            } else {
                val bitmaps = recentRepository.loadPageBitmaps(item.id)
                if (bitmaps.isEmpty()) return@launch
                val cache = java.io.File(requireContext().cacheDir, "share_${item.id}.jpg")
                java.io.FileOutputStream(cache).use { out ->
                    bitmaps.first().compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                }
                bitmaps.forEach { if (!it.isRecycled) it.recycle() }
                shareFile(cache, "image/jpeg")
            }
        }
    }

    private fun shareFile(file: java.io.File, mime: String) {
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file
        )
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                getString(R.string.share)
            )
        )
    }

    override fun onResume() {
        super.onResume()
        loadRecentScans()
    }

    private fun loadRecentScans() {
        viewLifecycleOwner.lifecycleScope.launch {
            val items = recentRepository.getAll()
            recentAdapter.submitList(items)
            binding.txtEmptyRecent.visibility =
                if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
