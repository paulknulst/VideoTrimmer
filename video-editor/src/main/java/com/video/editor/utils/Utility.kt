package com.video.editor.utils

import java.util.*

object Utility {

    fun convertTimestampToString(timeInMs: Float): String {
        val totalSeconds = (timeInMs / 1000).toInt()
        val seconds = totalSeconds % 60
        val minutes = totalSeconds / 60 % 60
        val hours = totalSeconds / 3600
        val formatter = Formatter()
        return if (hours > 0) {
            formatter.format("%d:%02d:%02d", hours, minutes, seconds).toString()
        } else {
            formatter.format("%02d:%02d", minutes, seconds).toString()
        }
    }
}
