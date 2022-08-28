package com.video.trimcrop

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
            if (extraIntent != null) path =
                extraIntent.getStringExtra(MainActivity.EXTRA_VIDEO_PATH)
            videoTrimmer
                .setOnCommandListener(this)
                .setOnVideoListener(this)
                .setVideoURI(Uri.parse(path))
                .setVideoInformationVisibility(true)
                .setMaxDuration(10)
                .setMinDuration(2)
                .setDestinationPath(
                    Environment.getExternalStorageDirectory()
                        .toString() + File.separator + "TrimCrop" + File.separator
                )
        }

        back.setOnClickListener {
            videoTrimmer.cancel()
        }

        save.setOnClickListener {
            videoTrimmer.save()
        }
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
