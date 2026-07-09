package com.docscanner.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.docscanner.app.R
import com.docscanner.app.databinding.ItemPageThumbnailBinding
import com.docscanner.app.domain.model.ScanPage

class PageThumbnailAdapter(
    private val onPageClick: (Int) -> Unit
) : RecyclerView.Adapter<PageThumbnailAdapter.Holder>() {

    private var pages: List<ScanPage> = emptyList()
    private var selectedIndex = 0

    fun submit(pages: List<ScanPage>, selectedIndex: Int) {
        this.pages = pages
        this.selectedIndex = selectedIndex
        notifyDataSetChanged()
    }

    fun moveItem(from: Int, to: Int) {
        if (from == to || from !in pages.indices || to !in pages.indices) return
        val mutable = pages.toMutableList()
        val moved = mutable.removeAt(from)
        mutable.add(to, moved)
        pages = mutable
        selectedIndex = when {
            selectedIndex == from -> to
            from < selectedIndex && to >= selectedIndex -> selectedIndex - 1
            from > selectedIndex && to <= selectedIndex -> selectedIndex + 1
            else -> selectedIndex
        }
        notifyItemMoved(from, to)
        notifyItemRangeChanged(0, itemCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemPageThumbnailBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(pages[position], position == selectedIndex, position + 1)
    }

    override fun getItemCount(): Int = pages.size

    inner class Holder(private val binding: ItemPageThumbnailBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(page: ScanPage, selected: Boolean, pageNumber: Int) {
            binding.imgThumb.setImageBitmap(page.workingBitmap())
            binding.txtPageNumber.text = pageNumber.toString()
            binding.root.strokeWidth = if (selected) 4 else 0
            binding.root.strokeColor = ContextCompat.getColor(
                binding.root.context,
                R.color.accent_blue
            )
            binding.root.cardElevation = if (selected) 18f else 8f
            binding.root.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onPageClick(pos)
                }
            }
        }
    }
}
