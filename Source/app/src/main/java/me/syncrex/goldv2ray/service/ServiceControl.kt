package me.syncrex.goldv2ray.service

import android.app.Service

interface ServiceControl {
    fun getService(): Service

    fun startService()

    fun stopService()

    fun vpnProtect(socket: Int): Boolean

    fun pauseService() //GOLDV2RAY
    fun resumeService() //GOLDV2RAY
}
