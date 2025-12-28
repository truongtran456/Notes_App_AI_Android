package com.philkes.notallyx.presentation.activity.note

import android.content.ClipData
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philkes.notallyx.R
import com.philkes.notallyx.databinding.ActivityViewCanvasBinding
import com.philkes.notallyx.presentation.activity.LockedActivity
import com.philkes.notallyx.presentation.add
import com.philkes.notallyx.presentation.setCancelButton
import com.philkes.notallyx.utils.getUriForFile
import com.philkes.notallyx.utils.wrapWithChooser
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class ViewCanvasActivity : LockedActivity<ActivityViewCanvasBinding>() {
    
    private var canvasBitmapPath: String? = null
    private var canvasDeleted = false
    private lateinit var exportFileActivityResultLauncher: ActivityResultLauncher<Intent>
    
    companion object {
        const val EXTRA_CANVAS_BITMAP_PATH = "extra_canvas_bitmap_path"
        const val EXTRA_NOTE_ID = "extra_note_id"
        const val RESULT_DELETED = "result_deleted"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewCanvasBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        canvasBitmapPath = intent.getStringExtra(EXTRA_CANVAS_BITMAP_PATH)
        
        // Load and display bitmap
        canvasBitmapPath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(path)
                binding.CanvasImageView.setImageBitmap(bitmap)
            }
        }
        
        setupToolbar()
        
        exportFileActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.data?.let { uri -> writeCanvasToUri(uri) }
                }
            }
    }
    
    private fun setupToolbar() {
        binding.Toolbar.setNavigationOnClickListener { 
            finishWithResult()
        }
        
        binding.Toolbar.title = getString(R.string.drawing)
        
        binding.Toolbar.menu.apply {
            add(R.string.share, R.drawable.share) {
                shareCanvas()
            }
            add(R.string.save_to_device, R.drawable.save) {
                saveToDevice()
            }
            add(R.string.delete, R.drawable.delete) {
                deleteCanvas()
            }
        }
    }
    
    private fun shareCanvas() {
        canvasBitmapPath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                val uri = getUriForFile(file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri(null, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }.wrapWithChooser(this)
                startActivity(intent)
            }
        }
    }
    
    private fun saveToDevice() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = "image/png"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_TITLE, "NotallyX Drawing")
        }.wrapWithChooser(this)
        exportFileActivityResultLauncher.launch(intent)
    }
    
    private fun writeCanvasToUri(uri: Uri) {
        canvasBitmapPath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                try {
                    val output = contentResolver.openOutputStream(uri) as FileOutputStream
                    output.channel.truncate(0)
                    val input = FileInputStream(file)
                    input.copyTo(output)
                    input.close()
                    output.close()
                    Toast.makeText(this, R.string.saved_to_device, Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Failed to save", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun deleteCanvas() {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.delete_image_forever)
            .setCancelButton()
            .setPositiveButton(R.string.delete) { _, _ ->
                canvasDeleted = true
                finishWithResult()
            }
            .show()
    }
    
    private fun finishWithResult() {
        val resultIntent = Intent().apply {
            putExtra(RESULT_DELETED, canvasDeleted)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }
    
    override fun onBackPressed() {
        finishWithResult()
    }
}

