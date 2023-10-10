package com.video.trimcrop

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

abstract class PermActivity : AppCompatActivity() {

    lateinit var doThis: () -> Unit

    protected fun setupPermissions(doSomething: () -> Unit) {
        doThis = doSomething
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (ContextCompat.checkSelfPermission(this, "android.permission.READ_MEDIA_VIDEO") != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add("android.permission.READ_MEDIA_VIDEO")
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                101
            )
        } else {
            doThis()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            101 -> {
                val allPermissionsGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                if (!allPermissionsGranted) {
                    PermissionsDialog(
                        this@PermActivity,
                        "Give Permissions"
                    ).show()
                } else {
                    doThis()
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

}
