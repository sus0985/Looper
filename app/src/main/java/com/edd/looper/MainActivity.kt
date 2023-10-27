package com.edd.looper

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.edd.looper.databinding.ActivityMainBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var isRecording: Boolean = false
    private var isPlaying: Boolean = false

    private var mediaRecorder: MediaRecorder? = null
    private var recordingMediaPlayer: MediaPlayer? = null

    private val recordAdapter by lazy {
        RecordAdapter(
            records = getAudioFiles(),
            onClickPlay = { record, player -> playAudio(record.file, player) },
            onClickStop = {
                isPlaying = false
                binding.buttonRecord.text = "Start"
            }
        )
    }

    private var recordFile: File? = null

    private val requestAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                showToast("Audio permission granted")
            } else {
                showToast("You need to grant audio permission")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerRecord.adapter = recordAdapter

        requestAudioPermission()

        binding.buttonRecord.setOnClickListener {
            if (isPlaying) {
                stopAudio()
                return@setOnClickListener
            }

            if (isRecording) {
                stopRecording()
                return@setOnClickListener
            }

            startRecording()
        }

        binding.recordVisualizer.onRequestCurrentAmplitude = {
            mediaRecorder?.maxAmplitude ?: 0
        }
    }

    private fun startRecording() {
        isRecording = true
        binding.buttonRecord.text = "Recording"

        recordFile = File(cacheDir, getRecordFileName())

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            MediaRecorder()
        }

        recordingMediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(recordFile?.absolutePath)
                prepare()
                start()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        mediaRecorder?.run {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setOutputFile(recordFile?.absolutePath)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
        }

        try {
            mediaRecorder?.prepare()
            mediaRecorder?.start()
        } catch (e: Exception) {
            showToast(e.message ?: "Exception with mediaRecorder")
            isRecording = false
            binding.buttonRecord.text = "Start"
        }

        binding.recordVisualizer.startVisualizing(false)
    }

    private fun stopRecording() {
        isRecording = false
        binding.buttonRecord.text = "Start"
        mediaRecorder?.stop()
        mediaRecorder?.release()
        mediaRecorder = null

        recordFile?.let { file ->
            recordAdapter.addRecord(Record(file))
            recordFile = null
        }

        binding.recordVisualizer.stopVisualizing()
        binding.recordVisualizer.clearVisualization()
    }

    private fun playAudio(file: File, mediaPlayer: MediaPlayer) {
        binding.buttonRecord.text = "Playing"
        isPlaying = true

        mediaPlayer.setOnCompletionListener {
            playAudio(file, mediaPlayer)
        }
    }

    private fun stopAudio() {
        isPlaying = false
        binding.buttonRecord.text = "Start"

        recordAdapter.stopAudio()
    }

    private fun getRecordFileName(): String {
        val date = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "${date}_audio.mp4"
    }

    private fun requestAudioPermission() {
        val permission = Manifest.permission.RECORD_AUDIO

        if (ContextCompat.checkSelfPermission(
                this,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestAudioPermissionLauncher.launch(permission)
        }
    }

    private fun getAudioFiles(): List<Record> {
        val files = cacheDir.listFiles()?.filterNotNull() ?: return emptyList()

        return files.map { file -> Record(file) }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}