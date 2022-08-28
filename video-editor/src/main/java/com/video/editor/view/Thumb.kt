package com.video.editor.view

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.video.editor.R
import java.util.*

class Thumb private constructor() {

    var index: Int = 0
        private set
    var value: Float = 0.toFloat()
    var pos: Float = 0.toFloat()
    var bitmap: Bitmap? = null
        private set(bitmap) {
            field = bitmap
            widthBitmap = bitmap?.width ?: 0
            heightBitmap = bitmap?.height ?: 0
        }
    var widthBitmap: Int = 0
        private set
    private var heightBitmap: Int = 0

    var lastTouchX: Float = 0.toFloat()

    init {
        value = 0f
        pos = 0f
    }

    companion object {
        const val LEFT = 0
        const val RIGHT = 1

        fun initThumbs(resources: Resources): List<Thumb> {
            val thumbs = Vector<Thumb>()
            for (i in 0..1) {
                val thumb = Thumb()
                thumb.index = i
                if (i == 0) {
                    val resImageLeft = R.drawable.seek_left_handle
                    thumb.bitmap = BitmapFactory.decodeResource(resources, resImageLeft)
                } else {
                    val resImageRight = R.drawable.seek_right_handle
                    thumb.bitmap = BitmapFactory.decodeResource(resources, resImageRight)
                }
                thumbs.add(thumb)
            }
            return thumbs
        }

        fun getWidthBitmap(thumbs: List<Thumb>): Int = thumbs[0].widthBitmap

        fun getHeightBitmap(thumbs: List<Thumb>): Int = thumbs[0].heightBitmap
    }
}
