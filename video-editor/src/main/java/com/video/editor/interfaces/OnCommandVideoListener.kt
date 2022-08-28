package com.video.editor.interfaces

import android.net.Uri

interface OnCommandVideoListener {
    fun onStarted()
    fun getResult(uri: Uri)
    fun cancelAction()
    fun onError(message: String)
}
