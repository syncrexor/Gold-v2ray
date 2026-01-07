package me.syncrex.goldv2ray

import android.content.Context
import androidx.multidex.MultiDexApplication
import androidx.work.Configuration
import androidx.work.WorkManager
import com.tencent.mmkv.MMKV
import com.getkeepsafe.relinker.ReLinker
import me.syncrex.goldv2ray.AppConfig.ANG_PACKAGE
import me.syncrex.goldv2ray.handler.SettingsManager

class AngApplication : MultiDexApplication()
{
    companion object
    {
        lateinit var application: AngApplication
    }

    override fun attachBaseContext(base: Context?)
    {
        super.attachBaseContext(base)
        application = this
    }

    private val workManagerConfiguration: Configuration = Configuration.Builder()
        .setDefaultProcessName("${ANG_PACKAGE}:bg")
        .build()

    override fun onCreate()
    {
        super.onCreate()

        //GOLDV2RAY
        try {
            MMKV.initialize(this)
        } catch (e: UnsatisfiedLinkError) {
            MMKV.initialize(this, null, object : MMKV.LibLoader {
                override fun loadLibrary(libName: String?) {
                    ReLinker.loadLibrary(this@AngApplication, libName ?: "mmkv")
                }
            })
        }
        //GOLDV2RAY END

        SettingsManager.setNightMode()

        WorkManager.initialize(this, workManagerConfiguration)

        SettingsManager.initRoutingRulesets(this)

        es.dmoral.toasty.Toasty.Config.getInstance()
            .setGravity(android.view.Gravity.BOTTOM, 0, 200)
            .apply()
    }
}