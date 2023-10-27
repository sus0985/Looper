package com.edd.looper

import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.util.Log
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.recyclerview.widget.RecyclerView
import com.edd.looper.databinding.ItemRecordBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RecordAdapter(
    private var records: List<Record> = arrayListOf(),
    private val onClickPlay: (Record, MediaPlayer) -> Unit,
    private val onClickStop: () -> Unit,
) : RecyclerView.Adapter<RecordAdapter.RecordViewHolder>() {

    private val mediaPlayers = hashMapOf<Int, MediaPlayer>()
    private val playingMap = hashMapOf<Int, Boolean>()
    private val progressJob = hashMapOf<Int, Job>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val binding = ItemRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RecordViewHolder(binding)
    }

    override fun getItemCount(): Int = records.size

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        val index = position.takeIf { it != RecyclerView.NO_POSITION } ?: return
        holder.bind(records[index], index)
    }

    fun addRecord(record: Record) {
        records.apply {
            val list = toMutableList()
            list.add(record)
            records = list
        }
        notifyItemRangeInserted(records.lastIndex, 1)
    }

    fun stopAudio() {
        mediaPlayers.values.forEach { player -> player.stop() }
    }

    inner class RecordViewHolder(private val binding: ItemRecordBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.buttonPlay.setOnClickListener {
                val position = adapterPosition.takeIf { it != RecyclerView.NO_POSITION }
                    ?: return@setOnClickListener
                play(position)
            }
            binding.buttonStop.setOnClickListener {
                val position = adapterPosition.takeIf { it != RecyclerView.NO_POSITION }
                    ?: return@setOnClickListener

                stop(position)
                binding.root.post {
                    binding.progressBar.progress = 0
//                    binding.visualizer.release()
                }

                onClickStop()
            }
            binding.buttonDelete.setOnClickListener {
                val position = adapterPosition.takeIf { it != RecyclerView.NO_POSITION }
                    ?: return@setOnClickListener
                stop(position)

                if (records[position].file.delete()) {
                    records = records.toMutableList().apply { removeAt(position) }
                    notifyItemRemoved(position)
                }

                onClickStop()
            }

            binding.buttonLoop.setOnClickListener {
                val position = adapterPosition.takeIf { it != RecyclerView.NO_POSITION }
                    ?: return@setOnClickListener
                play(position, true)
            }


//            binding.visualizer.setDensity(200f)
//            binding.visualizer.setColor(ContextCompat.getColor(binding.root.context, R.color.white))
        }

        private fun stop(position: Int) {
            progressJob[position]?.cancel()
            mediaPlayers[position]?.stop()
        }

        private fun play(position: Int, repeat: Boolean = false) {
            val record = records[position]
            val player = MediaPlayer().apply {
                try {
                    setDataSource(record.file.absolutePath)
                    isLooping = repeat
                    prepare()
                    start()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            mediaPlayers[position] = player
            onClickPlay(records[adapterPosition], player)
            playingMap[position] = true

            notifyItemChanged(position)
        }

        fun bind(record: Record, position: Int) {
            binding.textName.text = record.file.name

            if (playingMap[position] == true) {
                val player = mediaPlayers[position] ?: return
                val audioSessionId = player.audioSessionId
//                binding.visualizer.setPlayer(audioSessionId)

                val visualizer = Visualizer(player.audioSessionId)
                visualizer.enabled = false


                binding.visualizer.startVisualizing(false)
                player.setOnCompletionListener {
                    onClickStop()
                    binding.progressBar.progress = 0
//                    binding.visualizer.release()
                }

                progressJob[position] = CoroutineScope(Dispatchers.IO).launch {
                    val runningTime = player.duration

                    while (isActive && player.isPlaying) {
                        binding.progressBar.progress =
                            ((player.currentPosition.toFloat() / runningTime) * 100).toInt()
                    }
                }
            }
        }
    }
}