package com.docscanner.app.ui.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.docscanner.app.R
import com.docscanner.app.databinding.ItemRecentScanBinding
import com.docscanner.app.domain.model.RecentScanItem
import java.io.File

class RecentScanAdapter(
    private val onClick: (RecentScanItem) -> Unit,
    private val onLongClick: (RecentScanItem, View) -> Unit
) : ListAdapter<RecentScanItem, RecentScanAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemRecentScanBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return Holder(binding, onClick, onLongClick)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    class Holder(
        private val binding: ItemRecentScanBinding,
        private val onClick: (RecentScanItem) -> Unit,
        private val onLongClick: (RecentScanItem, View) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RecentScanItem) {
            binding.txtTitle.text = item.title
            binding.txtMeta.text = binding.root.context.getString(
                R.string.pages_count,
                item.pageCount
            )
            binding.imgThumb.load(File(item.thumbnailPath)) {
                crossfade(true)
            }
            binding.root.setOnClickListener { onClick(item) }
            binding.root.setOnLongClickListener {
                onLongClick(item, binding.root)
                true
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<RecentScanItem>() {
        override fun areItemsTheSame(oldItem: RecentScanItem, newItem: RecentScanItem) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: RecentScanItem, newItem: RecentScanItem) =
            oldItem == newItem
    }
}
