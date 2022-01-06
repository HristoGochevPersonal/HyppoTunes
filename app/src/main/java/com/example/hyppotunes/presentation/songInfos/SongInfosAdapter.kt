package com.example.hyppotunes.presentation.songInfos

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.hyppotunes.R
import com.example.hyppotunes.databinding.SongSummaryBinding

class SongInfosAdapter(
    private val interaction: Interaction? = null
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val diffCallback = object : DiffUtil.ItemCallback<SongInfoModel>() {
        override fun areItemsTheSame(oldItem: SongInfoModel, newItem: SongInfoModel): Boolean {
            return oldItem.name == newItem.name && oldItem.artist == newItem.artist
        }

        override fun areContentsTheSame(oldItem: SongInfoModel, newItem: SongInfoModel): Boolean {
            return when {
                oldItem.name != newItem.name -> {
                    false
                }
                oldItem.artist != newItem.artist -> {
                    false
                }
                oldItem.isLocal != newItem.isLocal -> {
                    false
                }
                oldItem.isCurrentlyPlaying != newItem.isCurrentlyPlaying -> {
                    false
                }
                else -> true
            }
        }
    }

    private val differ = AsyncListDiffer(this, diffCallback)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding = SongSummaryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SongInfoViewHolder(binding, interaction)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is SongInfoViewHolder -> {
                holder.bind(differ.currentList[position])
            }
        }
    }

    override fun getItemCount(): Int {
        return differ.currentList.size
    }

    fun submitList(list: List<SongInfoModel>) {
        differ.submitList(list)
    }

    class SongInfoViewHolder constructor(
        private var binding: SongSummaryBinding,
        private val interaction: Interaction?
    ) :
        RecyclerView.ViewHolder(binding.root) {

        private lateinit var songInfoModel: SongInfoModel

        fun bind(songInfoModel: SongInfoModel) {
            this.songInfoModel = songInfoModel
            binding.songName.text = songInfoModel.name
            val songArtistAndName = "${songInfoModel.artist} - ${songInfoModel.name}"
            binding.songArtistAndName.text = songArtistAndName
            if (songInfoModel.isLocal) {
                if (songInfoModel.isCurrentlyPlaying) {
                    binding.playDownloadButton.setImageResource(R.drawable.ic_baseline_stop_24)
                } else {
                    binding.playDownloadButton.setImageResource(R.drawable.play)
                }
            } else {
                binding.playDownloadButton.setImageResource(R.drawable.download)
            }
            binding.root.setOnClickListener {
                interaction?.onItemSelected(adapterPosition, songInfoModel)
            }
        }

    }


    interface Interaction {
        fun onItemSelected(position: Int, item: SongInfoModel)
    }
}