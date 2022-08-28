package com.video.trimcrop

import android.content.ContentUris
import android.content.ContentValues
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import com.video.editor.interfaces.OnCommandVideoListener

abstract class BaseCommandActivity() : PermActivity(),
    OnCommandVideoListener {

    private val progressDialog: VideoDialog by lazy {
        VideoDialog(
            this,
            "Cropping video. Please wait..."
        )
    }

    override fun onStarted() {
        RunOnUiThread(this).safely {
            Toast.makeText(this, "Start", Toast.LENGTH_SHORT).show()
            progressDialog.show()
        }
    }

    override fun onError(message: String) {
        Log.e("ERROR", message)
    }

    override fun getResult(uri: Uri) {
        RunOnUiThread(this).safely {
            RunOnUiThread(this).safely {
                Toast.makeText(this, "Video saved at ${uri.path}", Toast.LENGTH_SHORT).show()
                progressDialog.dismiss()
            }
            val mediaMetadataRetriever = MediaMetadataRetriever()
            mediaMetadataRetriever.setDataSource(this, uri)
            val duration =
                mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLong()
            val width =
                mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toLong()
            val height =
                mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toLong()
            val values = ContentValues()
            values.put(MediaStore.Video.Media.DATA, uri.path)
            values.put(MediaStore.Video.VideoColumns.DURATION, duration)
            values.put(MediaStore.Video.VideoColumns.WIDTH, width)
            values.put(MediaStore.Video.VideoColumns.HEIGHT, height)
            val id = ContentUris.parseId(
                contentResolver.insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    values
                )!!
            )
            Log.e("VIDEO ID", id.toString())
        }
    }
}