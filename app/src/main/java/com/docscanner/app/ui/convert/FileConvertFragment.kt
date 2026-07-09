package com.docscanner.app.ui.convert

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.docscanner.app.R
import androidx.core.os.bundleOf
import com.docscanner.app.databinding.FragmentFileConvertBinding
import com.docscanner.app.ui.utils.UiEffects

class FileConvertFragment : Fragment() {

    private var _binding: FragmentFileConvertBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFileConvertBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.conversionOptionsList.layoutManager = LinearLayoutManager(requireContext())
        binding.conversionOptionsList.adapter = ConversionOptionAdapter { type ->
            findNavController().navigate(
                R.id.action_convert_to_run,
                bundleOf("conversionType" to type.name)
            )
        }
        UiEffects.bindClick(binding.btnBack) {
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
