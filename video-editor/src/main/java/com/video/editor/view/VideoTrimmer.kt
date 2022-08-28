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
import com.video.editor.R
import com.video.editor.interfaces.OnCommandVideoListener
import com.video.editor.interfaces.OnProgressVideoListener
import com.video.editor.interfaces.OnRangeSeekBarListener
import com.video.editor.interfaces.OnVideoListener
import com.video.editor.utils.*
import kotlinx.android.synthetic.main.view_trimmer.view.*
import java.io.File
import java.lang.ref.WeakReference
import java.util.*


class VideoTrimmer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

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

        video_loader.setOnErrorListener { _, what, _ ->
            commandVideoListener?.onError("There was an error: $what")
            false
        }

        video_loader.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        handlerTop.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
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

        timeLineBar.addOnRangeSeekBarListener(object : OnRangeSeekBarListener {
            override fun onCreate(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float) {
            }

            override fun onSeek(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float) {
                handlerTop.visibility = View.GONE
                onSeekThumbs(index, value)
            }

            override fun onSeekStart(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float) {
            }

            override fun onSeekStop(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float) {
                onStopSeekThumbs()
            }
        })

        video_loader.setOnPreparedListener { mp -> onVideoPrepared(mp) }
        video_loader.setOnCompletionListener { onVideoCompleted() }
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
        video_loader.pause()
        icon_video_play.visibility = View.VISIBLE
        notifyProgressUpdate(false)
    }

    private fun onPlayerIndicatorSeekStop(seekBar: SeekBar) {
        messageHandler.removeMessages(SHOW_PROGRESS)
        video_loader.pause()
        icon_video_play.visibility = View.VISIBLE

        val duration = (duration * seekBar.progress / 1000L).toInt()
        video_loader.seekTo(duration)
        notifyProgressUpdate(false)
    }

    private fun setProgressBarPosition(position: Float) {
        if (duration > 0) handlerTop.progress = (1000L * position / duration).toInt()
    }

    private fun setUpMargins() {
        val marge = timeLineBar.thumbs[0].widthBitmap
        val lp = timeLineView.layoutParams as LayoutParams
        lp.setMargins(marge, 0, marge, 0)
        timeLineView.layoutParams = lp
    }

    fun save() {
        commandVideoListener?.onStarted()
        icon_video_play.visibility = View.VISIBLE
        video_loader.pause()

        val mediaMetadataRetriever = MediaMetadataRetriever()
        mediaMetadataRetriever.setDataSource(context, fileUri)
        val metaDataKeyDuration =
            java.lang.Long.parseLong(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))

        val file = File(fileUri.path ?: "")

        if (mTimeVideo < MIN_TIME_FRAME) {
            if (metaDataKeyDuration - endPos > MIN_TIME_FRAME - mTimeVideo) endPos += MIN_TIME_FRAME - mTimeVideo
            else if (startPos > MIN_TIME_FRAME - mTimeVideo) startPos -= MIN_TIME_FRAME - mTimeVideo
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
        if (video_loader.isPlaying) {
            icon_video_play.visibility = View.VISIBLE
            messageHandler.removeMessages(SHOW_PROGRESS)
            video_loader.pause()
        } else {
            icon_video_play.visibility = View.GONE
            if (resetBar) {
                resetBar = false
                video_loader.seekTo(startPos.toInt())
            }
            messageHandler.sendEmptyMessage(SHOW_PROGRESS)
            video_loader.start()
        }
    }

    fun cancel() {
        video_loader.stopPlayback()
        commandVideoListener?.cancelAction()
    }

    private fun onVideoPrepared(mp: MediaPlayer) {
        val videoWidth = mp.videoWidth
        val videoHeight = mp.videoHeight
        val videoProportion = videoWidth.toFloat() / videoHeight.toFloat()
        val screenWidth = layout_surface_view.width
        val screenHeight = layout_surface_view.height
        val screenProportion = screenWidth.toFloat() / screenHeight.toFloat()
        val lp = video_loader.layoutParams

        if (videoProportion > screenProportion) {
            lp.width = screenWidth
            lp.height = (screenWidth.toFloat() / videoProportion).toInt()
        } else {
            lp.width = (videoProportion * screenHeight.toFloat()).toInt()
            lp.height = screenHeight
        }
        video_loader.layoutParams = lp

        icon_video_play.visibility = View.VISIBLE

        duration = video_loader.duration.toFloat()
        setSeekBarPosition()

        setTimeFrames()

        videoListener?.onVideoPrepared()
    }

    private fun setSeekBarPosition() {
        when {
            duration >= maxDuration && maxDuration != -1 -> {
                startPos = duration / 2 - maxDuration / 2
                endPos = duration / 2 + maxDuration / 2
                timeLineBar.setThumbValue(0, (startPos * 100 / duration))
                timeLineBar.setThumbValue(1, (endPos * 100 / duration))
            }
            duration <= minDuration && minDuration != -1 -> {
                startPos = duration / 2 - minDuration / 2
                endPos = duration / 2 + minDuration / 2
                timeLineBar.setThumbValue(0, (startPos * 100 / duration))
                timeLineBar.setThumbValue(1, (endPos * 100 / duration))
            }
            else -> {
                startPos = 0f
                endPos = duration
            }
        }
        video_loader.seekTo(startPos.toInt())
        mTimeVideo = duration
        timeLineBar.initMaxWidth()
    }

    private fun setTimeFrames() {
        val seconds = context.getString(R.string.short_seconds)
        textTimeSelection.text = String.format(
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
                video_loader.seekTo(startPos.toInt())
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
        video_loader.pause()
        icon_video_play.visibility = View.VISIBLE
    }

    private fun onVideoCompleted() {
        video_loader.seekTo(startPos.toInt())
    }

    private fun notifyProgressUpdate(all: Boolean) {
        if (duration == 0f) return
        val position = video_loader.currentPosition
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
        if (video_loader == null) return
        if (time <= startPos && time <= endPos) handlerTop.visibility = View.GONE
        else handlerTop.visibility = View.VISIBLE
        if (time >= endPos) {
            messageHandler.removeMessages(SHOW_PROGRESS)
            video_loader.pause()
            icon_video_play.visibility = View.VISIBLE
            resetBar = true
            return
        }
        setProgressBarPosition(time)
    }

    fun setVideoInformationVisibility(visible: Boolean): VideoTrimmer {
        timeFrame.visibility = if (visible) View.VISIBLE else View.GONE
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
        video_loader.setVideoURI(fileUri)
        video_loader.requestFocus()
        timeLineView.setVideo(fileUri)
        return this
    }

    private class MessageHandler(view: VideoTrimmer) : Handler() {
        private val viewReference: WeakReference<VideoTrimmer> = WeakReference(view)
        override fun handleMessage(msg: Message) {
            val view = viewReference.get()
            if (view == null || view.video_loader == null) {
                return
            }
            view.notifyProgressUpdate(true)
            if (view.video_loader.isPlaying) {
                sendEmptyMessageDelayed(0, 10)
            }
        }
    }
}
