package com.video.editor.view

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Message
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.SeekBar
import com.arthenica.mobileffmpeg.FFmpeg
import com.video.editor.R
import com.video.editor.databinding.ViewTrimmerBinding
import com.video.editor.interfaces.OnCommandVideoListener
import com.video.editor.interfaces.OnProgressVideoListener
import com.video.editor.interfaces.OnRangeSeekBarListener
import com.video.editor.interfaces.OnVideoListener
import com.video.editor.utils.BackgroundExecutor
import com.video.editor.utils.RealPathUtil
import com.video.editor.utils.UiThreadExecutor
import com.video.editor.utils.Utility
import com.video.editor.utils.VideoCommands
import java.io.File
import java.lang.ref.WeakReference
import java.util.Calendar


class VideoTrimmer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding = ViewTrimmerBinding.inflate(LayoutInflater.from(context), this, true)

    companion object {
        private const val MIN_TIME_FRAME = 1000
        private const val SHOW_PROGRESS = 2
    }

    private lateinit var fileUri: Uri
    private var path: String? = null
    private var maxDuration: Int = -1
    private var minDuration: Int = -1
    private var duration = 0f
    private var commandVideoListener: OnCommandVideoListener? = null
    private var videoListener: OnVideoListener? = null
    private var progressListener: ArrayList<OnProgressVideoListener> = ArrayList()
    private var mTimeVideo = 0f
    private var startPos = 0f
    private var endPos = 0f
    private var resetBar = true
    private val messageHandler = MessageHandler(this)

    private var destinationPath: String
        get() {
            if (path == null) {
                val folder = Environment.getExternalStorageDirectory()
                path = folder.path + File.separator
            }
            return path ?: ""
        }
        set(destPath) {
            path = destPath
        }

    init {
        LayoutInflater.from(context).inflate(R.layout.view_trimmer, this, true)
        setUpListeners()
        setUpMargins()
    }

    private fun setUpListeners() {
        progressListener = ArrayList()
        progressListener.add(object : OnProgressVideoListener {
            override fun updateProgress(time: Float, max: Float, scale: Float) {
                updateVideoProgress(time)
            }
        })

        val gestureDetector =
            GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    togglePause()
                    return true
                }
            })

        binding.videoLoader.setOnErrorListener { _, what, _ ->
            commandVideoListener?.onError("There was an error: $what")
            false
        }

        binding.videoLoader.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        binding.handlerTop.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                onPlayerIndicatorSeekChanged(progress, fromUser)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                onPlayerIndicatorSeekStart()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                onPlayerIndicatorSeekStop(seekBar)
            }
        })

        binding.timeLineBar.addOnRangeSeekBarListener(object : OnRangeSeekBarListener {
            override fun onCreate(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float) {
            }

            override fun onSeek(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float) {
                binding.handlerTop.visibility = View.GONE
                onSeekThumbs(index, value)
            }

            override fun onSeekStart(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float) {
            }

            override fun onSeekStop(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float) {
                onStopSeekThumbs()
            }
        })

        binding.videoLoader.setOnPreparedListener { mp -> onVideoPrepared(mp) }
        binding.videoLoader.setOnCompletionListener { onVideoCompleted() }
    }

    private fun onPlayerIndicatorSeekChanged(progress: Int, fromUser: Boolean) {
        val duration = (duration * progress / 1000L)
        if (fromUser) {
            if (duration < startPos) setProgressBarPosition(startPos)
            else if (duration > endPos) setProgressBarPosition(endPos)
        }
    }

    private fun onPlayerIndicatorSeekStart() {
        messageHandler.removeMessages(SHOW_PROGRESS)
        binding.videoLoader.pause()
        binding.iconVideoPlay.visibility = View.VISIBLE
        notifyProgressUpdate(false)
    }

    fun setTrimRange(inputPath: String, outputPath: String, startTime: Long, endTime: Long) {
        val startTimeFormatted = convertTimestampToString(startTime)
        val endTimeFormatted = convertTimestampToString(endTime)
        val command = arrayOf("-y", "-i", inputPath, "-ss", startTimeFormatted, "-to", endTimeFormatted, "-c", "copy", outputPath)
        FFmpeg.execute(command)
    }

    private fun convertTimestampToString(timeInMs: Long): String {
        val totalSeconds = (timeInMs / 1000).toInt()
        val seconds = totalSeconds % 60
        val minutes = totalSeconds / 60 % 60
        val hours = totalSeconds / 3600
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun onPlayerIndicatorSeekStop(seekBar: SeekBar) {
        messageHandler.removeMessages(SHOW_PROGRESS)
        binding.videoLoader.pause()
        binding.iconVideoPlay.visibility = View.VISIBLE

        val duration = (duration * seekBar.progress / 1000L).toInt()
        binding.videoLoader.seekTo(duration)
        notifyProgressUpdate(false)
    }

    private fun setProgressBarPosition(position: Float) {
        if (duration > 0) binding.handlerTop.progress = (1000L * position / duration).toInt()
    }

    private fun setUpMargins() {
        val marge = binding.timeLineBar.thumbs[0].widthBitmap
        val lp = binding.timeLineView.layoutParams as LayoutParams
        lp.setMargins(marge, 0, marge, 0)
        binding.timeLineView.layoutParams = lp
    }

    fun save() {
        commandVideoListener?.onStarted()
        binding.iconVideoPlay.visibility = View.VISIBLE
        binding.videoLoader.pause()

        val mediaMetadataRetriever = MediaMetadataRetriever()
        mediaMetadataRetriever.setDataSource(context, fileUri)
        val metaDataKeyDuration =
            mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.let { java.lang.Long.parseLong(it) }

        val file = File(fileUri.path ?: "")

        if (mTimeVideo < MIN_TIME_FRAME) {
            if (metaDataKeyDuration != null) {
                if (metaDataKeyDuration - endPos > MIN_TIME_FRAME - mTimeVideo) endPos += MIN_TIME_FRAME - mTimeVideo
                else if (startPos > MIN_TIME_FRAME - mTimeVideo) startPos -= MIN_TIME_FRAME - mTimeVideo
            }
        }

        val root = File(destinationPath)
        root.mkdirs()
        val outputFileUri = Uri.fromFile(
            File(
                root,
                "t_${Calendar.getInstance().timeInMillis}_" + file.nameWithoutExtension + ".mp4"
            )
        )
        val outPutPath = RealPathUtil.realPathFromUriApi19(context, outputFileUri)
            ?: File(
                root,
                "t_${Calendar.getInstance().timeInMillis}_" + fileUri.path?.substring(
                    fileUri.path!!.lastIndexOf("/") + 1
                )
            ).absolutePath
        Log.e("SOURCE", file.path)
        Log.e("DESTINATION", outPutPath)
        val extractor = MediaExtractor()
        var frameRate = 24
        try {
            extractor.setDataSource(file.path)
            val numTracks = extractor.trackCount
            for (i in 0..numTracks) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                mime?.let {
                    if (it.startsWith("video/")) {
                        if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                            frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            extractor.release()
        }

        VideoCommands(context).trimVideo(
            Utility.convertTimestampToString(startPos),
            Utility.convertTimestampToString(endPos),
            file.path,
            outPutPath,
            outputFileUri,
            commandVideoListener
        )
    }

    private fun togglePause() {
        if (binding.videoLoader.isPlaying) {
            binding.iconVideoPlay.visibility = View.VISIBLE
            messageHandler.removeMessages(SHOW_PROGRESS)
            binding.videoLoader.pause()
        } else {
            binding.iconVideoPlay.visibility = View.GONE
            if (resetBar) {
                resetBar = false
                binding.videoLoader.seekTo(startPos.toInt())
            }
            messageHandler.sendEmptyMessage(SHOW_PROGRESS)
            binding.videoLoader.start()
        }
    }

    fun cancel() {
        binding.videoLoader.stopPlayback()
        commandVideoListener?.cancelAction()
    }

    private fun onVideoPrepared(mp: MediaPlayer) {
        val videoWidth = mp.videoWidth
        val videoHeight = mp.videoHeight
        val videoProportion = videoWidth.toFloat() / videoHeight.toFloat()
        val screenWidth = binding.layoutSurfaceView.width
        val screenHeight = binding.layoutSurfaceView.height
        val screenProportion = screenWidth.toFloat() / screenHeight.toFloat()
        val lp = binding.videoLoader.layoutParams

        if (videoProportion > screenProportion) {
            lp.width = screenWidth
            lp.height = (screenWidth.toFloat() / videoProportion).toInt()
        } else {
            lp.width = (videoProportion * screenHeight.toFloat()).toInt()
            lp.height = screenHeight
        }
        binding.videoLoader.layoutParams = lp

        binding.iconVideoPlay.visibility = View.VISIBLE

        duration = binding.videoLoader.duration.toFloat()
        setSeekBarPosition()

        setTimeFrames()

        videoListener?.onVideoPrepared()
    }

    private fun setSeekBarPosition() {
        when {
            duration >= maxDuration && maxDuration != -1 -> {
                startPos = duration / 2 - maxDuration / 2
                endPos = duration / 2 + maxDuration / 2
                binding.timeLineBar.setThumbValue(0, (startPos * 100 / duration))
                binding.timeLineBar.setThumbValue(1, (endPos * 100 / duration))
            }
            duration <= minDuration && minDuration != -1 -> {
                startPos = duration / 2 - minDuration / 2
                endPos = duration / 2 + minDuration / 2
                binding.timeLineBar.setThumbValue(0, (startPos * 100 / duration))
                binding.timeLineBar.setThumbValue(1, (endPos * 100 / duration))
            }
            else -> {
                startPos = 0f
                endPos = duration
            }
        }
        binding.videoLoader.seekTo(startPos.toInt())
        mTimeVideo = duration
        binding.timeLineBar.initMaxWidth()
    }

    private fun setTimeFrames() {
        val seconds = context.getString(R.string.short_seconds)
        binding.textTimeSelection.text = String.format(
            "%s %s - %s %s",
            Utility.convertTimestampToString(startPos),
            seconds,
            Utility.convertTimestampToString(endPos),
            seconds
        )
    }

    private fun onSeekThumbs(index: Int, value: Float) {
        when (index) {
            Thumb.LEFT -> {
                startPos = (duration * value / 100L)
                binding.videoLoader.seekTo(startPos.toInt())
            }
            Thumb.RIGHT -> {
                endPos = (duration * value / 100L)
            }
        }
        setTimeFrames()
        mTimeVideo = endPos - startPos
    }

    private fun onStopSeekThumbs() {
        messageHandler.removeMessages(SHOW_PROGRESS)
        binding.videoLoader.pause()
        binding.iconVideoPlay.visibility = View.VISIBLE
    }

    private fun onVideoCompleted() {
        binding.videoLoader.seekTo(startPos.toInt())
    }

    private fun notifyProgressUpdate(all: Boolean) {
        if (duration == 0f) return
        val position = binding.videoLoader.currentPosition
        if (all) {
            for (item in progressListener) {
                item.updateProgress(position.toFloat(), duration, (position * 100 / duration))
            }
        } else {
            progressListener[0].updateProgress(
                position.toFloat(),
                duration,
                (position * 100 / duration)
            )
        }
    }

    private fun updateVideoProgress(time: Float) {
        if (binding.videoLoader == null) return
        if (time <= startPos && time <= endPos) binding.handlerTop.visibility = View.GONE
        else binding.handlerTop.visibility = View.VISIBLE
        if (time >= endPos) {
            messageHandler.removeMessages(SHOW_PROGRESS)
            binding.videoLoader.pause()
            binding.iconVideoPlay.visibility = View.VISIBLE
            resetBar = true
            return
        }
        setProgressBarPosition(time)
    }

    fun setVideoInformationVisibility(visible: Boolean): VideoTrimmer {
        binding.timeFrame.visibility = if (visible) View.VISIBLE else View.GONE
        return this
    }

    fun setOnCommandListener(commandVideoListener: OnCommandVideoListener): VideoTrimmer {
        this.commandVideoListener = commandVideoListener
        return this
    }

    fun setOnVideoListener(onVideoListener: OnVideoListener): VideoTrimmer {
        videoListener = onVideoListener
        return this
    }

    fun destroy() {
        BackgroundExecutor.cancelAll("", true)
        UiThreadExecutor.cancelAll("")
    }

    fun setMaxDuration(maxDuration: Int): VideoTrimmer {
        this.maxDuration = maxDuration * 1000
        return this
    }

    fun setMinDuration(minDuration: Int): VideoTrimmer {
        this.minDuration = minDuration * 1000
        return this
    }

    fun setDestinationPath(path: String): VideoTrimmer {
        destinationPath = path
        return this
    }

    fun setVideoURI(videoURI: Uri): VideoTrimmer {
        fileUri = videoURI
        binding.videoLoader.setVideoURI(fileUri)
        binding.videoLoader.requestFocus()
        binding.timeLineView.setVideo(fileUri)
        return this
    }

    private class MessageHandler(view: VideoTrimmer) : Handler() {
        private val viewReference: WeakReference<VideoTrimmer> = WeakReference(view)
        override fun handleMessage(msg: Message) {
            val view = viewReference.get()
            if (view == null || view.binding.videoLoader == null) {
                return
            }
            view.notifyProgressUpdate(true)
            if (view.binding.videoLoader.isPlaying) {
                sendEmptyMessageDelayed(0, 10)
            }
        }
    }
}
