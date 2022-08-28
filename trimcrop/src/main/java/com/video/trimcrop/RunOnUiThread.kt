package com.video.trimcrop

import android.content.Context
import android.util.Log
import org.jetbrains.anko.runOnUiThread

class RunOnUiThread(var context: Context?) {
    companion object {
        private const val TAG = "RunOnUiThread"
    }

    fun safely(dothis: () -> Unit) {
        if (context != null) {
            context?.runOnUiThread {
                try {
                    dothis.invoke()
                } catch (e: Exception) {
                    Log.e(
                        "$TAG  - ${context!!::class.java.canonicalName}", e.toString()
                    )
                    e.printStackTrace()
                }
            }
        }
    }
}