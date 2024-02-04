package me.syncrex.goldv2ray.ui

import me.syncrex.goldv2ray.R
import me.syncrex.goldv2ray.util.Utils
import android.os.Bundle
import me.syncrex.goldv2ray.service.V2RayServiceManager

class ScSwitchActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        moveTaskToBack(true)

        setContentView(R.layout.activity_none)

        if (V2RayServiceManager.v2rayPoint.isRunning) {
            Utils.stopVService(this)
        } else {
            Utils.startVServiceFromToggle(this)
        }
        finish()
    }
}
