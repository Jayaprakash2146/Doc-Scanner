package com.docscanner.app.ui.convert

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.docscanner.app.convert.ConversionType
import com.docscanner.app.databinding.ItemConversionOptionBinding
import com.docscanner.app.ui.utils.UiEffects

class ConversionOptionAdapter(
    private val onSelected: (ConversionType) -> Unit
) : RecyclerView.Adapter<ConversionOptionAdapter.Holder>() {

    private val items = ConversionType.all()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemConversionOptionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return Holder(binding, onSelected)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    class Holder(
        private val binding: ItemConversionOptionBinding,
        private val onSelected: (ConversionType) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(type: ConversionType, position: Int) {
            binding.imgOptionButton.setImageResource(type.iconRes)
            binding.imgOptionButton.contentDescription = type.title
            binding.cardOption.translationZ = 14f

            binding.root.alpha = 0f
            binding.root.translationY = 36f
            binding.root.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((position * 60).toLong())
                .setDuration(380)
                .start()

            UiEffects.applyFloatingPress(binding.cardOption) {
                onSelected(type)
            }
        }
    }
}
