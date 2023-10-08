package com.video.trimcrop

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import com.video.trimcrop.databinding.ProgressLoadingBinding


class VideoDialog(
    ctx: Context,
    private var message: String
) : Dialog(ctx) {
    private lateinit var binding: ProgressLoadingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.progress_loading)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setCancelable(false)
        setCanceledOnTouchOutside(false)

        binding.messageLabel.text = message
    }
}