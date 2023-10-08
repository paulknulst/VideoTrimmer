package com.video.trimcrop

import android.net.Uri
import android.os.Bundle
import android.os.Environment
import com.video.trimcrop.databinding.ActivityCropperBinding
import java.io.File

class CropperActivity : BaseCommandActivity() {
    private lateinit var binding: ActivityCropperBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cropper)

        setupPermissions {
            val extraIntent = intent
            var path = ""
            if (extraIntent != null) {
                path = extraIntent.getStringExtra(MainActivity.EXTRA_VIDEO_URI)!!
            }
            binding.videoCropper.setVideoURI(Uri.parse(path))
                .setOnCommandVideoListener(this)
                .setMinMaxRatios(0.3f, 3f)
                .setDestinationPath(
                    Environment.getExternalStorageDirectory()
                        .toString() + File.separator + "TrimCrop" + File.separator
                )
        }

        binding.back.setOnClickListener {
            binding.videoCropper.cancel()
        }

        binding.save.setOnClickListener {
            binding.videoCropper.save()
        }
    }

    override fun cancelAction() {
        RunOnUiThread(this).safely {
            finish()
        }
    }


}