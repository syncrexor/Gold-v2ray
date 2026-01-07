package me.syncrex.goldv2ray.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.syncrex.goldv2ray.AppConfig

class ProcessService {
    private var process: Process? = null

    fun runProcess(context: Context, cmd: MutableList<String>) {
        Log.d(AppConfig.TAG, cmd.toString())

        try {
            val proBuilder = ProcessBuilder(cmd)
            proBuilder.redirectErrorStream(true)
            process = proBuilder
                .directory(context.filesDir)
                .start()

            CoroutineScope(Dispatchers.IO).launch {
                Thread.sleep(50L)
                Log.i(AppConfig.TAG, "runProcess check")
                process?.waitFor()
                Log.i(AppConfig.TAG, "runProcess exited")
            }
            Log.i(AppConfig.TAG, process.toString())

        } catch (e: Exception) {
            Log.e(AppConfig.TAG, e.toString())
        }
    }

    fun stopProcess() {
        try {
            Log.i(AppConfig.TAG, "runProcess destroy")
            process?.destroy()
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to destroy process", e)
        }
    }
}