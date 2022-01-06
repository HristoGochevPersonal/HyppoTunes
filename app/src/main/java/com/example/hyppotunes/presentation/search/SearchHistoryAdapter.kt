package com.example.hyppotunes.presentation.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.hyppotunes.databinding.SearchHistoryItemBinding

class SearchHistoryAdapter(private val interaction: Interaction? = null) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val diffCallback = object : DiffUtil.ItemCallback<SearchHistoryItem>() {
        override fun areItemsTheSame(
            oldItem: SearchHistoryItem,
            newItem: SearchHistoryItem
        ): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(
            oldItem: SearchHistoryItem,
            newItem: SearchHistoryItem
        ): Boolean {
            return when {
                oldItem.name != newItem.name -> {
                    false
                }
                else -> true
            }
        }
    }

    private val differ = AsyncListDiffer(this, diffCallback)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding =
            SearchHistoryItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SearchHistoryItemViewHolder(binding, interaction)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is SearchHistoryItemViewHolder -> {
                holder.bind(differ.currentList[position])
            }
        }
    }

    override fun getItemCount(): Int {
        return differ.currentList.size
    }

    fun submitList(list: List<SearchHistoryItem>) {
        differ.submitList(null)
        differ.submitList(list)
    }

    class SearchHistoryItemViewHolder constructor(
        private var binding: SearchHistoryItemBinding,
        private val interaction: Interaction?
    ) :
        RecyclerView.ViewHolder(binding.root) {

        private lateinit var searchHistoryItem: SearchHistoryItem

        fun bind(searchHistoryItem: SearchHistoryItem) {
            this.searchHistoryItem = searchHistoryItem
            binding.text.text = searchHistoryItem.name
            binding.root.setOnClickListener {
                interaction?.onItemSelected(adapterPosition, searchHistoryItem)
            }
        }

    }

    interface Interaction {
        fun onItemSelected(position: Int, item: SearchHistoryItem)
    }
}