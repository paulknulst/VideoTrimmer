package com.video.trimcrop

import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.video.editor.interfaces.OnVideoListener
import com.video.trimcrop.databinding.ActivityTrimmerBinding
import java.io.File

class TrimmerActivity : BaseCommandActivity(), OnVideoListener {

    private var segmentLengthInSeconds: Int = 10
    private lateinit var rewardedInterstitialAd: RewardedInterstitialAd

    private lateinit var binding: ActivityTrimmerBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrimmerBinding.inflate(layoutInflater)
        setContentView(R.layout.activity_trimmer)

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
                if (::rewardedInterstitialAd.isInitialized) {
                    rewardedInterstitialAd.fullScreenContentCallback =
                        object : FullScreenContentCallback() {
                            override fun onAdDismissedFullScreenContent() {
                                // Continue with the save operation after the ad is dismissed
                                //proceedWithSaveOperation()
                            }

//                        override fun onAdFailedToShowFullScreenContent(adError: AdError?) {
//                            // Handle the error and continue with the save operation
//                            //proceedWithSaveOperation()
//                        }

                            override fun onAdShowedFullScreenContent() {
                                // Ad is being shown
                            }
                        }
                    rewardedInterstitialAd.show(this) { rewardItem ->
                        // Handle the reward
                        Toast.makeText(
                            this,
                            "Reward: ${rewardItem.amount} ${rewardItem.type}",
                            Toast.LENGTH_LONG
                        ).show()
                        // Now you could proceed with the save operation
                        // proceedWithSaveOperation()
                    }
                }
                val videoDuration = getVideoDuration(videoUri)
                val segmentDuration = segmentLengthInSeconds * 1000
                val numberOfSegments = (videoDuration / segmentDuration).toInt()

                for (i in 0 until numberOfSegments) {
                    val startTime = i * segmentDuration
                    val endTime = startTime + segmentDuration
                    val outputPath = Environment.getExternalStorageDirectory()
                        .toString() + File.separator + "TrimCrop" + File.separator + "segment_$i.mp4"
                    binding.videoTrimmer.setTrimRange(
                        path, outputPath, startTime.toLong(),
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
                    binding.videoTrimmer.setTrimRange(
                        path,
                        outputPath,
                        startTime.toLong(),
                        videoDuration
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
            Toast.makeText(this, "onVideoPrepared", Toast.LENGTH_SHORT).show()
        }
    }
}