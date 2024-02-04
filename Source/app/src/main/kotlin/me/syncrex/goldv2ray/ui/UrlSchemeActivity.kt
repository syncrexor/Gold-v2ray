package me.syncrex.goldv2ray.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle

import me.syncrex.goldv2ray.AppConfig
import me.syncrex.goldv2ray.R
import me.syncrex.goldv2ray.databinding.ActivityLogcatBinding
import me.syncrex.goldv2ray.extension.toast
import me.syncrex.goldv2ray.util.AngConfigManager
import java.net.URLDecoder

class UrlSchemeActivity : BaseActivity() {
    private lateinit var binding: ActivityLogcatBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogcatBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        try {
            intent.apply {
                if (action == Intent.ACTION_SEND) {
                    if ("text/plain" == type) {
                        intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                            val uri = Uri.parse(it)
                            if (uri.scheme?.startsWith(AppConfig.HTTPS_PROTOCOL) == true || uri.scheme?.startsWith(
                                    AppConfig.HTTP_PROTOCOL
                                ) == true
                            ) {
                                val name = uri.getQueryParameter("name") ?: "Subscription"
                                importSubscription(it, name)
                            } else {
                                importConfig(it)
                            }
                        }
                    }
                } else if (action == Intent.ACTION_VIEW) {
                    when (data?.host) {
                        "install-config" -> {
                            val uri: Uri? = intent.data
                            val shareUrl: String = uri?.getQueryParameter("url")!!
                            toast(shareUrl)
                            importConfig(shareUrl)
                        }

                        "install-sub" -> {
                            val uri: Uri? = intent.data
                            val url = uri?.getQueryParameter("url")!!
                            val name = uri.getQueryParameter("name") ?: "Subscription"
                            importSubscription(url, name)
                        }

                        else -> {
                            toast(R.string.toast_failure)
                        }
                    }
                }

            }


            startActivity(Intent(this, MainActivity::class.java))
            finish()
        } catch (e: Exception) {
            
            e.printStackTrace()
        }
    }

    private fun importSubscription(url: String, name: String) {
        val decodedUrl = URLDecoder.decode(url, "UTF-8")

        val check = AngConfigManager.importSubscription(name, decodedUrl)
        if (check) toast(R.string.import_subscription_success) else toast(R.string.import_subscription_failure)
    }

    private fun importConfig(shareUrl: String) {
        val count = AngConfigManager.importBatchConfig(shareUrl, "", false)
        if (count > 0) {
            toast(R.string.toast_success)
        } else {
            toast(R.string.toast_failure)
        }
    }
}