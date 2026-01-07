package me.syncrex.goldv2ray.ui

import android.Manifest
import me.syncrex.goldv2ray.R
import me.syncrex.goldv2ray.handler.AngConfigManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import me.syncrex.goldv2ray.extension.toast
import android.content.Intent
import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.core.content.ContextCompat
import me.syncrex.goldv2ray.extension.toastError
import me.syncrex.goldv2ray.extension.toastSuccess

class ScScannerActivity : BaseActivity() {

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            scanQRCode.launch(Intent(this, ScannerActivity::class.java))
        } else {
            toast(R.string.toast_permission_denied)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_none)

        importQRcode()
    }

    fun importQRcode(): Boolean {
        requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        return true
    }

    private val scanQRCode = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            val scanResult = it.data?.getStringExtra("SCAN_RESULT").orEmpty()
            val (count, countSub) = AngConfigManager.importBatchConfig(scanResult, "", false)
            if (count + countSub > 0) {
                toastSuccess(R.string.toast_success)
            } else {
                toastError(R.string.toast_failure)
            }
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }
}
