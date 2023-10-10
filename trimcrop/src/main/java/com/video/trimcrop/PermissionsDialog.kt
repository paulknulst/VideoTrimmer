package com.video.trimcrop

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import com.video.trimcrop.databinding.DialogPermissionsBinding

class PermissionsDialog(var ctx: Context, var msg: String) : Dialog(ctx) {

    private var binding: DialogPermissionsBinding? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_permissions)

        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        binding?.message?.text = msg

        binding?.dismiss?.setOnClickListener {
            dismiss()
        }

        binding?.settings?.setOnClickListener {
            val i = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + BuildConfig.APPLICATION_ID)
            )
            ctx.startActivity(i)
            dismiss()
        }
    }
}