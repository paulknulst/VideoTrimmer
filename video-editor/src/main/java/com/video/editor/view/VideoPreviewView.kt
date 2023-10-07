package com.video.editor.view

import android.content.ContentValues.TAG
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import android.util.LongSparseArray
import android.view.View
import com.video.editor.R
import com.video.editor.utils.BackgroundExecutor
import com.video.editor.utils.UiThreadExecutor
import kotlin.math.ceil

class VideoPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var videoUri: Uri? = null
    private var viewHeight: Int = 0
    private var bitmaps: LongSparseArray<Bitmap>? = null

    init {
        init()
    }

    private fun init() {
        viewHeight = context.resources.getDimensionPixelOffset(R.dimen.frames_video_height)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val minW = paddingLeft + paddingRight + suggestedMinimumWidth
        val w = resolveSizeAndState(minW, widthMeasureSpec, 1)
        val minH = paddingBottom + paddingTop + viewHeight
        val h = resolveSizeAndState(minH, heightMeasureSpec, 1)
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        if (w != oldW) createPreview(w)
    }

    private fun createPreview(viewWidth: Int) {
        BackgroundExecutor.execute(object : BackgroundExecutor.Task("", 0L, "") {
            override fun execute() {
                try {
                    val threshold = 11
                    val thumbnails = LongSparseArray<Bitmap>()
                    val mediaMetadataRetriever = MediaMetadataRetriever()
                    mediaMetadataRetriever.setDataSource(context, videoUri)
                    val videoLengthInMs = (Integer.parseInt(
                        mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ) * 1000).toLong()
                    val frameHeight = viewHeight
                    val initialBitmap = mediaMetadataRetriever.getFrameAtTime(
                        0,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )
                    val frameWidth =
                        ((initialBitmap!!.width.toFloat() / initialBitmap.height.toFloat()) * frameHeight.toFloat()).toInt()
                    var numThumbs = ceil((viewWidth.toFloat() / frameWidth)).toInt()
                    if (numThumbs < threshold) {
                        numThumbs = threshold
                    }
                    val cropWidth = viewWidth / threshold
                    val interval = videoLengthInMs / numThumbs
                    for (i in 0 until numThumbs) {
                        val bitmap = mediaMetadataRetriever.getFrameAtTime(
                            i * interval,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                        )
                        bitmap?.let {
                            var newBitmap: Bitmap
                            try {
                                newBitmap = Bitmap.createScaledBitmap(
                                    it,  // Use 'it' instead of 'bitmap'
                                    frameWidth,
                                    frameHeight,
                                    false
                                )
                                newBitmap = Bitmap.createBitmap(newBitmap, 0, 0, cropWidth, newBitmap.height)
                            } catch (e: Exception) {
                                Log.e(TAG, "error while create bitmap: $e")
                                return@let  // Skip to next iteration if an error occurs
                            }
                            thumbnails.put(i.toLong(), newBitmap)
                        }
                    }
                    mediaMetadataRetriever.release()
                    returnBitmaps(thumbnails)
                } catch (e: Throwable) {
                    Thread.getDefaultUncaughtExceptionHandler()
                        .uncaughtException(Thread.currentThread(), e)
                }
            }
        })
    }

    private fun returnBitmaps(thumbnailList: LongSparseArray<Bitmap>) {
        UiThreadExecutor.runTask("", Runnable {
            bitmaps = thumbnailList
            invalidate()
        }, 0L)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bitmaps != null) {
            canvas.save()
            var x = 0
            for (i in 0 until (bitmaps?.size() ?: 0)) {
                val bitmap = bitmaps?.get(i.toLong())
                if (bitmap != null) {
                    canvas.drawBitmap(bitmap, x.toFloat(), 0f, null)
                    x += bitmap.width
                }
            }
        }
    }

    fun setVideo(data: Uri) {
        videoUri = data
    }
}
