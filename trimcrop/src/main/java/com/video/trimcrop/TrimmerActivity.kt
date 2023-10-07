package com.video.trimcrop

import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import com.video.editor.interfaces.OnVideoListener
import kotlinx.android.synthetic.main.activity_trimmer.back
import kotlinx.android.synthetic.main.activity_trimmer.save
import kotlinx.android.synthetic.main.activity_trimmer.videoTrimmer
import java.io.File

class TrimmerActivity : BaseCommandActivity(), OnVideoListener {

    private var segmentLengthInSeconds: Int = 10
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trimmer)

        segmentLengthInSeconds = intent?.getIntExtra("segmentLength", 10) ?: 10

        setupPermissions {
            val extraIntent = intent
            var path = ""
            if (extraIntent != null) {
                path = extraIntent.getStringExtra(MainActivity.EXTRA_VIDEO_PATH)!!
            }


            videoTrimmer
                .setOnCommandListener(this)
                .setOnVideoListener(this)
                .setVideoURI(Uri.parse(path))
                .setVideoInformationVisibility(true)
                .setMaxDuration(60)
                .setMinDuration(5)
                .setDestinationPath(
                    Environment.getExternalStorageDirectory()
                        .toString() + File.separator + "TrimCrop" + File.separator
                )

            back.setOnClickListener {
                videoTrimmer.cancel()
            }

            save.setOnClickListener {
                val videoDuration = getVideoDuration(path)
                val segmentDuration = segmentLengthInSeconds * 1000
                val numberOfSegments = (videoDuration / segmentDuration).toInt()

                for (i in 0 until numberOfSegments) {
                    val startTime = i * segmentDuration
                    val endTime = startTime + segmentDuration
                    val outputPath = Environment.getExternalStorageDirectory()
                        .toString() + File.separator + "TrimCrop" + File.separator + "segment_$i.mp4"
                    videoTrimmer.setTrimRange(path, outputPath, startTime.toLong(),
                        endTime.toLong()
                    )
                    // Notify the MediaStore about the new file
                    MediaScannerConnection.scanFile(
                        this,
                        arrayOf(outputPath),
                        null
                    ) { _, uri ->
                        Log.d("MediaScannerConnection", "Scanned $outputPath: -> uri=$uri")
                    }
                }

                // Check if there is any remaining time that needs to be saved
                val remainingTime = videoDuration - (numberOfSegments * segmentDuration)
                if (remainingTime > 0) {
                    val startTime = numberOfSegments * segmentDuration
                    //video duration is the same as endTime
                    val outputPath = Environment.getExternalStorageDirectory()
                        .toString() + File.separator + "TrimCrop" + File.separator + "segment_$numberOfSegments.mp4"
                    videoTrimmer.setTrimRange(path, outputPath, startTime.toLong(), videoDuration)

                    // Notify the MediaStore about the new file
                    MediaScannerConnection.scanFile(
                        this,
                        arrayOf(outputPath),
                        null
                    ) { _, uri ->
                        Log.d("MediaScannerConnection", "Scanned $outputPath: -> uri=$uri")
                    }
                }
            }

        }
    }

    private fun getVideoDuration(path: String): Long {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(path)
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        retriever.release()
        return duration?.toLong() ?: 0L
    }

    override fun cancelAction() {
        RunOnUiThread(this).safely {
            videoTrimmer.destroy()
            finish()
        }
    }

    override fun onVideoPrepared() {
        RunOnUiThread(this).safely {
            Toast.makeText(this, "onVideoPrepared", Toast.LENGTH_SHORT).show()
        }
    }
}