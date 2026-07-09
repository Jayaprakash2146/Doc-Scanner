package com.docscanner.app.ui.editor

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.docscanner.app.R
import com.docscanner.app.databinding.FragmentEditorBinding
import com.docscanner.app.domain.model.ScanFilter
import androidx.recyclerview.widget.ItemTouchHelper
import com.docscanner.app.ui.adapter.PageThumbnailAdapter
import com.docscanner.app.ui.adapter.PageThumbnailDragCallback
import com.docscanner.app.ui.export.ExportBottomSheet
import com.docscanner.app.ui.utils.ToolButtonHelper
import com.docscanner.app.ui.utils.UiEffects
import com.docscanner.app.viewmodel.ScanSessionViewModel
import kotlinx.coroutines.launch

class EditorFragment : Fragment() {

    private var _binding: FragmentEditorBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ScanSessionViewModel by activityViewModels()
    private lateinit var thumbAdapter: PageThumbnailAdapter

    private var toolbarInitialized = false
    private val filterToolViews = mutableMapOf<ScanFilter, View>()
    private var lastHighlightedFilter: ScanFilter? = null
    private var lastHighlightedPageIndex: Int = -1
    private var lastPreviewToken: Int = -1
    private var lastPreviewPageId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        UiEffects.slideUpFade(binding.previewCard, 120)

        val inflater = LayoutInflater.from(requireContext())
        setupTopTools()
        setupFilterToolbarOnce(inflater)

        thumbAdapter = PageThumbnailAdapter { index ->
            viewModel.selectPage(index)
        }
        binding.pageStrip.adapter = thumbAdapter
        ItemTouchHelper(
            PageThumbnailDragCallback(thumbAdapter) { from, to ->
                viewModel.reorderPages(from, to)
            }
        ).attachToRecyclerView(binding.pageStrip)

        UiEffects.bindClick(binding.btnKeepScanningEditor) {
            findNavController().navigate(R.id.action_editor_to_camera)
        }
        UiEffects.bindClick(binding.btnExportEditor) {
            ExportBottomSheet().show(parentFragmentManager, ExportBottomSheet.TAG)
        }

        binding.cropOverlay.onCornersChanged = { corners ->
            viewModel.updatePageCorners(corners)
        }

        binding.editorProgress.isClickable = false
        binding.editorProgress.isFocusable = false

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.editorProgress.visibility =
                        if (state.isProcessing) View.VISIBLE else View.GONE

                    thumbAdapter.submit(state.pages, state.currentPageIndex)

                    val page = state.currentPage()
                    if (page != null) {
                        refreshPreview(page, state.previewToken)
                        highlightActiveFilter(page.filter, state.currentPageIndex)
                    }

                    if (state.cropMode) {
                        binding.cropOverlay.visibility = View.VISIBLE
                        binding.cropOverlay.isClickable = true
                        binding.editorBottomBar.visibility = View.GONE
                        binding.toolbarCard.visibility = View.GONE
                        showCropControls()
                    } else {
                        binding.cropOverlay.visibility = View.GONE
                        binding.cropOverlay.isClickable = false
                        binding.editorBottomBar.visibility = View.VISIBLE
                        binding.toolbarCard.visibility = View.VISIBLE
                        resetTopTools()
                    }
                }
            }
        }

        if (viewModel.uiState.value.pages.isEmpty()) {
            findNavController().navigate(R.id.action_editor_to_camera)
        }
    }

    private fun setupTopTools() {
        setupLabeledTool(binding.toolRetake.root, R.drawable.btn_rotate, getString(R.string.retake)) {
            viewModel.beginRetakeCurrentPage()
            findNavController().navigate(R.id.action_editor_to_camera)
        }
        setupLabeledTool(binding.toolDelete.root, R.drawable.btn_delete, getString(R.string.delete)) {
            viewModel.deleteCurrentPage()
            if (viewModel.uiState.value.pages.isEmpty()) {
                findNavController().navigate(R.id.action_editor_to_camera)
            }
        }
    }

    private fun showCropControls() {
        setupLabeledTool(binding.toolRetake.root, R.drawable.btn_save, "Done") {
            viewModel.applyCropToCurrentPage()
        }
        setupLabeledTool(binding.toolDelete.root, R.drawable.btn_delete, "Discard") {
            viewModel.setCropMode(false)
        }
    }

    private fun resetTopTools() {
        setupTopTools()
    }

    private fun setupFilterToolbarOnce(inflater: LayoutInflater) {
        if (toolbarInitialized) return
        toolbarInitialized = true

        val row = binding.toolbarRow
        row.removeAllViews()

        // Use PNG icons for Rotate and Adjust (with A letter for Adjust)
        ToolButtonHelper.addTool(row, inflater, R.drawable.btn_rotate, getString(R.string.rotate)) {
            if (!viewModel.uiState.value.isProcessing) {
                viewModel.rotateCurrentPage()
            }
        }
        ToolButtonHelper.addTool(row, inflater, R.drawable.ic_adjust_letter_a, getString(R.string.adjust)) {
            if (!viewModel.uiState.value.isProcessing) {
                AdjustBottomSheet().show(parentFragmentManager, AdjustBottomSheet.TAG)
            }
        }

        ScanFilter.entries.forEach { filter ->
            val label = filterLabel(filter)
            val icon = filterIcon(filter)
            val toolView = ToolButtonHelper.addTool(row, inflater, icon, label) {
                if (!viewModel.uiState.value.isProcessing) {
                    viewModel.applyFilter(filter)
                }
            }
            filterToolViews[filter] = toolView
        }
    }

    private fun highlightActiveFilter(active: ScanFilter, pageIndex: Int) {
        if (lastHighlightedFilter == active && lastHighlightedPageIndex == pageIndex) return
        lastHighlightedFilter = active
        lastHighlightedPageIndex = pageIndex

        val accent = ContextCompat.getColor(requireContext(), R.color.primary)
        val accentContainer = ContextCompat.getColor(requireContext(), R.color.primary_container)
        val normalLabel = ContextCompat.getColor(requireContext(), R.color.text_primary)
        val white = ContextCompat.getColor(requireContext(), R.color.white)

        filterToolViews.forEach { (filter, view) ->
            val selected = filter == active
            val card = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.iconContainer)
            if (selected) {
                card.setCardBackgroundColor(accentContainer)
                card.strokeColor = accent
                view.findViewById<TextView>(R.id.txtLabel).setTextColor(accent)
            } else {
                card.setCardBackgroundColor(white)
                card.strokeColor = ContextCompat.getColor(requireContext(), R.color.divider)
                view.findViewById<TextView>(R.id.txtLabel).setTextColor(normalLabel)
            }
        }
    }

    private fun filterLabel(filter: ScanFilter): String = when (filter) {
        ScanFilter.ORIGINAL -> getString(R.string.filter_original)
        ScanFilter.MAGIC_COLOR -> getString(R.string.filter_magic)
        ScanFilter.BLACK_WHITE -> getString(R.string.filter_bw)
        ScanFilter.GRAYSCALE -> getString(R.string.filter_gray)
        ScanFilter.SHARPEN -> getString(R.string.filter_sharpen)
    }

    private fun filterIcon(filter: ScanFilter): Int = when (filter) {
        ScanFilter.ORIGINAL -> R.drawable.btn_enhance
        ScanFilter.GRAYSCALE -> R.drawable.btn_grayscale
        ScanFilter.BLACK_WHITE -> R.drawable.btn_bw_filter
        ScanFilter.MAGIC_COLOR -> R.drawable.btn_color_filter
        ScanFilter.SHARPEN -> R.drawable.btn_enhance
    }

    private fun setupLabeledTool(root: View, iconRes: Int, label: String, onClick: () -> Unit) {
        root.findViewById<ImageButton>(R.id.btnIcon).setImageResource(iconRes)
        root.findViewById<TextView>(R.id.txtLabel).text = label
        UiEffects.bindClick(root, onClick)
    }

    private fun refreshPreview(page: com.docscanner.app.domain.model.ScanPage, token: Int) {
        if (page.id == lastPreviewPageId && token == lastPreviewToken) return
        lastPreviewPageId = page.id
        lastPreviewToken = token
        val bmp = page.workingBitmap()
        if (bmp.isRecycled) return
        binding.imgPreview.setImageDrawable(null)
        binding.imgPreview.setImageBitmap(bmp)
        binding.imgPreview.invalidate()
    }

    override fun onDestroyView() {
        toolbarInitialized = false
        filterToolViews.clear()
        lastHighlightedFilter = null
        lastHighlightedPageIndex = -1
        lastPreviewToken = -1
        lastPreviewPageId = null
        super.onDestroyView()
        _binding = null
    }
}
