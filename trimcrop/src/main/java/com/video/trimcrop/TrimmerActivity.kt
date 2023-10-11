package com.video.trimcrop

import android.content.ContentValues
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.video.editor.interfaces.OnVideoListener
import com.video.trimcrop.databinding.ActivityTrimmerBinding
import java.io.File
import java.io.FileOutputStream

class TrimmerActivity : BaseCommandActivity(), OnVideoListener {

    private var segmentLengthInSeconds: Int = 10
    private lateinit var rewardedInterstitialAd: RewardedInterstitialAd

    private val binding: ActivityTrimmerBinding by lazy {
        ActivityTrimmerBinding.inflate(layoutInflater)
    }

    private val resolver by lazy { contentResolver }


    private fun getExternalOutputFilePath(fileName: String): String {
        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val appDir = File(moviesDir, "AutoTrim") // replace "MyApp" with your app's name
        appDir.mkdirs()
        val outputFile = File(appDir, fileName)
        return outputFile.absolutePath
    }


    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)  // This is correct

        val videoUriString = intent.getStringExtra(MainActivity.EXTRA_VIDEO_URI)
        val videoUri = Uri.parse(videoUriString)
        println("TrimmerActivity onCreate videoUri: $videoUri")

        val adRequest = AdRequest.Builder().build()
        RewardedInterstitialAd.load(
            this,
            "ca-app-pub-3940256099942544/5354046379",
            adRequest,
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    rewardedInterstitialAd = ad
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    // Handle the error
                }
            }
        )

        segmentLengthInSeconds = intent?.getIntExtra("segmentLength", 10) ?: 10

        setupPermissions {
            val extraIntent = intent
            var path = ""
            if (extraIntent != null) {
                path = extraIntent.getStringExtra(MainActivity.EXTRA_VIDEO_URI)!!
            }

            fun beginVideoTrimming() {
                val videoDuration = getVideoDuration(videoUri)
                val segmentDuration = segmentLengthInSeconds * 1000
                val numberOfSegments = (videoDuration / segmentDuration).toInt()

                for (i in 0 until numberOfSegments) {
                    val startTime = i * segmentDuration
                    val endTime = startTime + segmentDuration
                    val outputStream = FileOutputStream(
                        File(
                            Environment.getExternalStorageDirectory(),
                            "TrimCrop${File.separator}segment_$i.mp4"
                        )
                    )

                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, "trimmed_video.mp4")
                        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
                    }
                    val videoFile = File(path)
                    val originalVideoName = videoFile.nameWithoutExtension
                    val uri = resolver.insert(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        contentValues
                    )

                    uri?.let {
                        val outputFilePath =
                            getExternalOutputFilePath("$originalVideoName${i}.mp4")
                        binding.videoTrimmer.setTrimRange(
                            videoUri,
                            outputFilePath,
                            startTime.toLong(),
                            endTime.toLong()
                        )
                    }

                    outputStream.close()
                }

                // Check if there is any remaining time that needs to be saved
                val remainingTime = videoDuration - (numberOfSegments * segmentDuration)
                if (remainingTime > 0) {
                    val startTime = numberOfSegments * segmentDuration
                    //video duration is the same as endTime
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, "trimmed_video.mp4")
                        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
                    }

                    val uri = resolver.insert(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        contentValues
                    )
                    val videoFile = File(path)
                    val originalVideoName = videoFile.nameWithoutExtension

                    uri?.let {
                        val outputFilePath =
                            getExternalOutputFilePath("$originalVideoName${numberOfSegments}_remainder.mp4")
                        binding.videoTrimmer.setTrimRange(
                            videoUri,
                            outputFilePath,
                            startTime.toLong(),
                            videoDuration
                        )
                    }
                }
            }


            binding.videoTrimmer
                .setOnCommandListener(this)
                .setOnVideoListener(this)
                .setVideoURI(Uri.parse(path))  // Use Uri directly here
                .setVideoInformationVisibility(true)
                .setMaxDuration(60)
                .setMinDuration(5)
                .setDestinationPath(
                    Environment.getExternalStorageDirectory()
                        .toString() + File.separator + "TrimCrop" + File.separator
                )

            binding.back.setOnClickListener {
                binding.videoTrimmer.cancel()
            }

            binding.save.setOnClickListener {
                println("TrimmerActivity onSave Called")
                if (::rewardedInterstitialAd.isInitialized) {
                    rewardedInterstitialAd.fullScreenContentCallback =
                        object : FullScreenContentCallback() {
                            override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                                // Display a toast message when the ad fails to show
                                Toast.makeText(
                                    this@TrimmerActivity,
                                    "Error loading Ad $p0",
                                    Toast.LENGTH_LONG
                                ).show()
                                beginVideoTrimming() //Will start video trimming if Ad fails to load
                            }

                            override fun onAdShowedFullScreenContent() {
                                Toast.makeText(
                                    this@TrimmerActivity,
                                    "Video trimming will start after the ad",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    rewardedInterstitialAd.show(this) { rewardItem ->
                        // Handle the reward
                        Toast.makeText(
                            this,
                            "Video Trimming Started",
                            Toast.LENGTH_LONG
                        ).show()
                        // Now you could proceed with the save operation
                        beginVideoTrimming()
                    }
                }
            }

        }
    }

    private fun getVideoDuration(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(this, uri)  // Use this method to set Uri
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        retriever.release()
        return duration?.toLong() ?: 0L
    }

    override fun cancelAction() {
        RunOnUiThread(this).safely {
            binding.videoTrimmer.destroy()
            finish()
        }
    }

    override fun onVideoPrepared() {
        RunOnUiThread(this).safely {
            Toast.makeText(this, "Ready to start", Toast.LENGTH_SHORT).show()
        }
    }
}