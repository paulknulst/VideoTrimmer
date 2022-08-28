package com.video.editor.interfaces

interface OnProgressVideoListener {
    fun updateProgress(time: Float, max: Float, scale: Float)
}