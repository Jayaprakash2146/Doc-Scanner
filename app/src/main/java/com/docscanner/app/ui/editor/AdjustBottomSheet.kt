package com.docscanner.app.ui.editor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.docscanner.app.R
import com.docscanner.app.databinding.BottomSheetAdjustBinding
import com.docscanner.app.domain.model.ImageAdjustment
import com.docscanner.app.viewmodel.ScanSessionViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.slider.Slider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AdjustBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAdjustBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ScanSessionViewModel by activityViewModels()
    private var selected: ImageAdjustment = ImageAdjustment.BRIGHTNESS
    private var adjustmentJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAdjustBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val page = viewModel.uiState.value.currentPage() ?: run {
            dismiss()
            return
        }

        binding.chipGroupAdjust.removeAllViews()
        ImageAdjustment.entries.forEach { adj ->
            val chip = Chip(requireContext()).apply {
                text = adj.label
                isCheckable = true
                id = View.generateViewId()
                tag = adj
            }
            binding.chipGroupAdjust.addView(chip)
            if (adj == ImageAdjustment.BRIGHTNESS) chip.isChecked = true
        }

        binding.chipGroupAdjust.setOnCheckedStateChangeListener { group, _ ->
            val id = group.checkedChipId
            if (id == View.NO_ID) return@setOnCheckedStateChangeListener
            selected = group.findViewById<Chip>(id)?.tag as? ImageAdjustment ?: return@setOnCheckedStateChangeListener
            syncSlider(page.adjustments[selected] ?: 0)
        }

        binding.sliderAdjust.addOnChangeListener { _: Slider, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val v = value.toInt()
            binding.txtAdjustValue.text = if (v > 0) "+$v" else v.toString()
            
            // Debounce the adjustment to prevent crashes from overlapping rapid requests
            adjustmentJob?.cancel()
            adjustmentJob = lifecycleScope.launch {
                delay(30) // small delay to debounce
                viewModel.updatePageAdjustment(selected, v)
            }
        }

        syncSlider(page.adjustments[selected] ?: 0)
        binding.btnAdjustDone.setOnClickListener { dismiss() }
    }

    private fun syncSlider(value: Int) {
        binding.sliderAdjust.value = value.toFloat()
        binding.txtAdjustValue.text = if (value > 0) "+$value" else value.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AdjustBottomSheet"
    }
}
