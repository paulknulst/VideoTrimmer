package com.video.trimcrop

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import com.video.editor.interfaces.OnVideoListener
import kotlinx.android.synthetic.main.activity_trimmer.*
import java.io.File

class TrimmerActivity : BaseCommandActivity(), OnVideoListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trimmer)

        setupPermissions {
            val extraIntent = intent
            var path = ""
            if (extraIntent != null) {
                path = extraIntent.getStringExtra(MainActivity.EXTRA_VIDEO_PATH)!!
            }
            val videoDuration = getVideoDuration(path) // Get the video duration
            val segmentDuration = 10_000 // 10 seconds in milliseconds
            val numberOfSegments = (videoDuration / segmentDuration).toInt()

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
                for (i in 0 until numberOfSegments) {
                    val startTime = i * segmentDuration
                    val endTime = startTime + segmentDuration
//                    videoTrimmer.setTrimRange(startTime, endTime)
                    videoTrimmer.save()
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