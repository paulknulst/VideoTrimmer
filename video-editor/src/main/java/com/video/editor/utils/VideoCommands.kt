package com.video.editor.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.github.hiteshsondhi88.libffmpeg.ExecuteBinaryResponseHandler
import com.github.hiteshsondhi88.libffmpeg.FFmpeg
import com.github.hiteshsondhi88.libffmpeg.FFmpegLoadBinaryResponseHandler
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException
import com.video.editor.interfaces.OnCommandVideoListener

class VideoCommands(private var ctx: Context) {
    companion object {
        private const val TAG = "VideoCommands"
    }

    fun trimVideo(
        startPos: String,
        endPos: String,
        input: String,
        output: String,
        outputFileUri: Uri,
        listener: OnCommandVideoListener?
    ) {
        val ffmpeg = FFmpeg.getInstance(ctx)
        ffmpeg.loadBinary(object : FFmpegLoadBinaryResponseHandler {
            override fun onFinish() {
                Log.d("FFmpeg", "onFinish")
            }

            override fun onSuccess() {
                val command = arrayOf(
                    "-y",
                    "-i",
                    input,
                    "-ss",
                    startPos,
                    "-to",
                    endPos,
                    "-c",
                    "copy",
                    output
                )
                try {
                    ffmpeg.execute(command, object : ExecuteBinaryResponseHandler() {
                        override fun onSuccess(message: String?) {
                            super.onSuccess(message)
                        }

                        override fun onProgress(message: String?) {
                            super.onProgress(message)
                        }

                        override fun onFailure(message: String?) {
                            super.onFailure(message)
                            listener?.onError(message.toString())
                        }

                        override fun onStart() {
                            super.onStart()
                        }

                        override fun onFinish() {
                            super.onFinish()
                            listener?.getResult(outputFileUri)
                        }
                    })
                } catch (e: FFmpegCommandAlreadyRunningException) {
                    listener?.onError(e.toString())
                }
            }

            override fun onFailure() {
                listener?.onError("Failed")
            }

            override fun onStart() {
            }
        })
        listener?.onStarted()
    }

    fun cropVideo(
        w: Int,
        h: Int,
        x: Int,
        y: Int,
        input: String,
        output: String,
        outputFileUri: Uri,
        listener: OnCommandVideoListener?
    ) {
        val ffmpeg = FFmpeg.getInstance(ctx)
        ffmpeg.loadBinary(object : FFmpegLoadBinaryResponseHandler {
            override fun onFinish() {
                Log.d("FFmpeg", "onFinish")
            }

            override fun onSuccess() {
                val command = arrayOf(
                    "-i",
                    input,
                    "-filter:v",
                    "crop=$w:$h:$x:$y",
                    "-threads",
                    "5",
                    "-preset",
                    "ultrafast",
                    "-strict",
                    "-2",
                    "-c:a",
                    "copy",
                    output
                )
                try {
                    ffmpeg.execute(command, object : ExecuteBinaryResponseHandler() {
                        override fun onSuccess(message: String?) {
                            super.onSuccess(message)
                        }

                        override fun onFailure(message: String?) {
                            super.onFailure(message)
                            listener?.onError(message.toString())
                        }

                        override fun onFinish() {
                            super.onFinish()
                            listener?.getResult(outputFileUri)
                        }
                    })
                } catch (e: FFmpegCommandAlreadyRunningException) {
                    listener?.onError(e.toString())
                }
            }

            override fun onFailure() {
                Log.d("FFmpegLoad", "onFailure")
                listener?.onError("Failed")
            }

            override fun onStart() {
            }
        })
        listener?.onStarted()
    }

}