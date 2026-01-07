package me.syncrex.goldv2ray.ui

import android.Manifest
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import androidx.core.content.ContextCompat
import me.syncrex.goldv2ray.AppConfig
import me.syncrex.goldv2ray.R
import me.syncrex.goldv2ray.extension.toast
import me.syncrex.goldv2ray.util.QRCodeDecoder
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanCustomCode
import io.github.g00fy2.quickie.config.ScannerConfig
import me.syncrex.goldv2ray.handler.MmkvManager

class ScannerActivity : BaseActivity(){

    private val scanQrCode = registerForActivityResult(ScanCustomCode(), ::handleResult)
    private val chooseFile = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val uri = it.data?.data
        if (it.resultCode == RESULT_OK && uri != null) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                val text = QRCodeDecoder.syncDecodeQRCode(bitmap)
                if (text.isNullOrEmpty()) {
                    toast(R.string.toast_decoding_failed)
                } else {
                    finished(text)
                }
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "Failed to decode QR code from file", e)
                toast(R.string.toast_decoding_failed)
            }
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                showFileChooser()
            } else {
                toast(R.string.toast_permission_denied)
            }
        }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //GOLDV2RAY
        supportActionBar?.let { actionBar ->
            val title = SpannableString(getString(R.string.menu_item_scan_qrcode))
            title.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, R.color.colorText)), 0, title.length, 0)
            title.setSpan(StyleSpan(Typeface.BOLD), 0, title.length, 0)
            actionBar.title = title
        }

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_START_SCAN_IMMEDIATE) == true) {
            launchScan()
        }
    }

    private fun launchScan(){
        scanQrCode.launch(
            ScannerConfig.build {
                setHapticSuccessFeedback(true) // enable (default) or disable haptic feedback when a barcode was detected
                setShowTorchToggle(true) // show or hide (default) torch/flashlight toggle button
                setShowCloseButton(true) // show or hide (default) close button
            }
        )
    }

    private fun handleResult(result: QRResult) {
        if (result is QRResult.QRSuccess ) {
            finished(result.content.rawValue.orEmpty())
        } else {
            finish()
        }
    }

    private fun finished(text: String) {
        val intent = Intent()
        intent.putExtra("SCAN_RESULT", text)
        setResult(AppCompatActivity.RESULT_OK, intent)
        finish()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_scanner, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.scan_code -> {
            launchScan()
            true
        }
        R.id.select_photo -> {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
                showFileChooser()
            } else {
                requestPermissionLauncher.launch(permission)
            }
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun showFileChooser() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        //intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)

        try {
            chooseFile.launch(Intent.createChooser(intent, getString(R.string.title_file_chooser)))
        } catch (ex: android.content.ActivityNotFoundException) {
            toast(R.string.toast_require_file_manager)
        }
    }
}
