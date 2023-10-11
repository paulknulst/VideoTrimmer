package com.video.trimcrop

import android.content.ContentValues
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

    fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }


    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)  // This is correct
        val progressDialog = findViewById<LinearLayout>(R.id.progress_dialog)

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
                CoroutineScope(Dispatchers.IO).launch {
                    val videoDuration = getVideoDuration(videoUri)
                    val segmentDuration = segmentLengthInSeconds * 1000
                    val numberOfSegments = (videoDuration / segmentDuration).toInt()

                    // Initialize ProgressBar
                    val progressBar = findViewById<ProgressBar>(R.id.progress_bar)

//                    linearLayout.visibility = View.VISIBLE
                    progressBar.max = numberOfSegments

                    withContext(Dispatchers.IO) {
                        for (i in 0 until numberOfSegments) {
                            val startTime = i * segmentDuration
                            val endTime = startTime + segmentDuration

                            val contentValues = ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, "segment_$i.mp4")
                                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                                put(
                                    MediaStore.MediaColumns.RELATIVE_PATH,
                                    "${Environment.DIRECTORY_MOVIES}/TrimCrop"
                                )
                            }

                            val uri = resolver.insert(
                                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                contentValues
                            )

                            uri?.let {
                                val outputFilePath = getExternalOutputFilePath("segment_$i.mp4")
                                binding.videoTrimmer.setTrimRange(
                                    videoUri,
                                    outputFilePath,
                                    startTime.toLong(),
                                    endTime.toLong()
                                )
                            }
                            withContext(Dispatchers.Main) {
                                progressBar.progress = i + 1
                            }
                        }

                        // Check if there is any remaining time that needs to be saved
                        val remainingTime = videoDuration - (numberOfSegments * segmentDuration)
                        if (remainingTime > 0) {
                            val startTime = numberOfSegments * segmentDuration
                            //video duration is the same as endTime
                            val contentValues = ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, "trimmed_video.mp4")
                                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                                put(
                                    MediaStore.MediaColumns.RELATIVE_PATH,
                                    Environment.DIRECTORY_MOVIES
                                )
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

                    withContext(Dispatchers.Main) {
                        progressDialog.visibility = View.GONE
                        Toast.makeText(
                            this@TrimmerActivity,
                            "Video Saved!",
                            Toast.LENGTH_LONG
                        ).show()
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
                progressDialog.visibility = View.VISIBLE
                if (::rewardedInterstitialAd.isInitialized) {
                    rewardedInterstitialAd.fullScreenContentCallback =
                        object : FullScreenContentCallback() {
                            override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                                // Display a toast message when the ad fails to show
                                Toast.makeText(
                                    this@TrimmerActivity,
                                    "Error loading Ad $p0",
                                    Toast.LENGTH_SHORT
                                ).show()
                                beginVideoTrimming() //Will start video trimming if Ad fails to load
                            }

                            override fun onAdShowedFullScreenContent() {
                                Toast.makeText(
                                    this@TrimmerActivity,
                                    "Video trimming will start after the ad",
                                    Toast.LENGTH_SHORT
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
                } else {
                    beginVideoTrimming() //Will start if Ad fails to load
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