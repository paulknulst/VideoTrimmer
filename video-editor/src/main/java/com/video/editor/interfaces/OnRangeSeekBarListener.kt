package com.video.editor.interfaces

import com.video.editor.view.RangeSeekBarView

interface OnRangeSeekBarListener {
    fun onCreate(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float)
    fun onSeek(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float)
    fun onSeekStart(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float)
    fun onSeekStop(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float)
}