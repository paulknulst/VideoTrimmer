package com.video.trimcrop

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.video.editor.utils.FileUtils
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : PermActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        trimmerButton.setOnClickListener { pickFromGallery(REQUEST_VIDEO_TRIMMER) }
        cropperButton.setOnClickListener { pickFromGallery(REQUEST_VIDEO_CROPPER) }
    }

    private fun pickFromGallery(intentCode: Int) {
        setupPermissions {
            val intent = Intent()
            intent.setTypeAndNormalize("video/*")
            intent.action = Intent.ACTION_GET_CONTENT
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            startActivityForResult(
                Intent.createChooser(
                    intent,
                    getString(R.string.label_select_video)
                ), intentCode
            )
        }
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_VIDEO_TRIMMER) {
                val selectedUri = data!!.data
                if (selectedUri != null) {
                    startTrimActivity(selectedUri)
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        R.string.toast_cannot_retrieve_selected_video,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else if (requestCode == REQUEST_VIDEO_CROPPER) {
                val selectedUri = data!!.data
                if (selectedUri != null) {
                    startCropActivity(selectedUri)
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        R.string.toast_cannot_retrieve_selected_video,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun startTrimActivity(uri: Uri) {
        val intent = Intent(this, TrimmerActivity::class.java)
        intent.putExtra(EXTRA_VIDEO_PATH, FileUtils.getPath(this, uri))
        startActivity(intent)
    }

    private fun startCropActivity(uri: Uri) {
        val intent = Intent(this, CropperActivity::class.java)
        intent.putExtra(EXTRA_VIDEO_PATH, FileUtils.getPath(this, uri))
        startActivity(intent)
    }

    companion object {
        private const val REQUEST_VIDEO_TRIMMER = 0x01
        private const val REQUEST_VIDEO_CROPPER = 0x02
        internal const val EXTRA_VIDEO_PATH = "EXTRA_VIDEO_PATH"
    }

}
