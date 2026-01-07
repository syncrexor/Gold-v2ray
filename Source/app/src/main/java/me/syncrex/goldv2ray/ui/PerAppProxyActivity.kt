package me.syncrex.goldv2ray.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.text.Html
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import me.syncrex.goldv2ray.AppConfig
import me.syncrex.goldv2ray.handler.SettingsManager
import me.syncrex.goldv2ray.R
import me.syncrex.goldv2ray.databinding.ActivityBypassListBinding
import me.syncrex.goldv2ray.dto.AppInfo
import me.syncrex.goldv2ray.extension.toast
import me.syncrex.goldv2ray.extension.v2RayApplication
import me.syncrex.goldv2ray.util.AppManagerUtil
import me.syncrex.goldv2ray.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.syncrex.goldv2ray.extension.toastSuccess
import me.syncrex.goldv2ray.handler.MmkvManager
import java.text.Collator
import me.syncrex.goldv2ray.util.HttpUtil
import es.dmoral.toasty.Toasty
import java.util.*

class PerAppProxyActivity : BaseActivity() {
    private val binding by lazy { ActivityBypassListBinding.inflate(layoutInflater) }

    private var adapter: PerAppProxyAdapter? = null
    private var appsAll: List<AppInfo>? = null

    @SuppressLint("CheckResult")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        //supportActionBar?.title = (Html.fromHtml("<font color=\"#FFC107\">" + getString(R.string.title_settings) + "</font>")) //GOLDV2RAY
        //addCustomDividerToRecyclerView(binding.recyclerView, this, R.drawable.custom_divider) //GOLDV2RAY
        //GOLDV2RAY
        supportActionBar?.let { actionBar ->
            val title = SpannableString(getString(R.string.per_app_proxy_settings))
            title.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, R.color.colorText)), 0, title.length, 0)
            title.setSpan(StyleSpan(Typeface.BOLD), 0, title.length, 0)
            actionBar.title = title
        }

        //val blacklist = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET) //GOLDV2RAY
        //addCustomDividerToRecyclerView(binding.recyclerView, this, R.drawable.custom_divider) //GOLDV2RAY
        lifecycleScope.launch {
            try {
                binding.pbWaiting.show()
                val blacklist =
                    MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET)
                val apps = withContext(Dispatchers.IO) {
                    //val appsList = AppManagerUtil.loadNetworkAppList(this@PerAppProxyActivity) //GOLDV2RAY
                    val appsList = loadNetworkAppList(this@PerAppProxyActivity) //GOLDV2RAY
                    if (blacklist != null) {
                        appsList.forEach { app ->
                            app.isSelected = if (blacklist.contains(app.packageName)) 1 else 0
                        }
                        appsList.sortedWith { p1, p2 ->
                            when {
                                p1.isSelected > p2.isSelected -> -1
                                p1.isSelected < p2.isSelected -> 1
                                p1.isSystemApp > p2.isSystemApp -> 1
                                p1.isSystemApp < p2.isSystemApp -> -1
                                p1.appName.lowercase() > p2.appName.lowercase() -> 1
                                p1.appName.lowercase() < p2.appName.lowercase() -> -1
                                p1.packageName > p2.packageName -> 1
                                p1.packageName < p2.packageName -> -1
                                else -> 0
                            }
                        }
                    } else {
                        val collator = Collator.getInstance()
                        appsList.sortedWith(compareBy(collator) { it.appName })
                    }
                }
                appsAll = apps
                adapter = PerAppProxyAdapter(this@PerAppProxyActivity, apps, blacklist)
                binding.recyclerView.adapter = adapter
                binding.pbWaiting.hide()
            } catch (e: Exception) {
                binding.pbWaiting.hide()
                Log.e(AppConfig.TAG, "Error loading apps", e)
            }

            binding.switchPerAppProxy.setOnCheckedChangeListener { _, isChecked ->
                MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY, isChecked)
            }

            binding.switchPerAppProxy.isChecked =
                MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY, false)

            binding.switchBypassApps.setOnCheckedChangeListener { _, isChecked ->
                MmkvManager.encodeSettings(AppConfig.PREF_BYPASS_APPS, isChecked)
            }

            binding.switchBypassApps.isChecked =
                MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS, false)

            binding.layoutSwitchBypassAppsTips.setOnClickListener {
                Toasty.info(this@PerAppProxyActivity, getString(R.string.summary_pref_per_app_proxy), Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        adapter?.let {
            MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY_SET, it.blacklist)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_bypass_list, menu)

        //GOLDV2RAY
        val searchItem = menu.findItem(R.id.search_view)
        val searchView = menu.findItem(R.id.search_view).actionView as SearchView
        val searchIcon = searchView.findViewById(androidx.appcompat.R.id.search_button) as ImageView
        searchIcon.setColorFilter(resources.getColor(R.color.colorIcon))
        for (i in 0 until menu.size()) {
            val menuItem = menu.getItem(i)
            val spannable = SpannableString(menuItem.title)
            spannable.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, R.color.colorText)), 0, spannable.length, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
            menuItem.title = spannable
        }

        if (searchItem != null) {
            val searchView = searchItem.actionView as SearchView
            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean = false

                override fun onQueryTextChange(newText: String?): Boolean {
                    filterProxyApp(newText.orEmpty())
                    return false
                }
            })
        }
        //GOLDV2RAY END

        return super.onCreateOptionsMenu(menu)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.select_all -> adapter?.let { it ->
            val pkgNames = it.apps.map { it.packageName }
            if (it.blacklist.containsAll(pkgNames)) {
                it.apps.forEach {
                    val packageName = it.packageName
                    adapter?.blacklist?.remove(packageName)
                }
            } else {
                it.apps.forEach {
                    val packageName = it.packageName
                    adapter?.blacklist?.add(packageName)
                }
            }
            it.notifyDataSetChanged()
            true
        } ?: false
        R.id.select_proxy_app -> {
            selectProxyApp()
            true
        }
        R.id.import_proxy_app -> {
            importProxyApp()
            true
        }
        R.id.export_proxy_app -> {
            exportProxyApp()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun selectProxyApp() {
        toast(R.string.msg_downloading_content)
        binding.pbWaiting.show()
        val url = AppConfig.ANDROID_PACKAGE_NAME_LIST_URL
        lifecycleScope.launch(Dispatchers.IO) {
            var content = HttpUtil.getUrlContent(url, 5000)
            if (content.isNullOrEmpty()) {
                val httpPort = SettingsManager.getHttpPort()
                content = HttpUtil.getUrlContent(url, 5000, httpPort) ?: ""
            }
            launch(Dispatchers.Main) {
                Log.i(AppConfig.TAG, content)
                selectProxyApp(content, true)
                toastSuccess(R.string.toast_success)
                binding.pbWaiting.hide()
            }
        }
    }

    private fun importProxyApp() {
        val content = Utils.getClipboard(applicationContext)
        if (TextUtils.isEmpty(content)) {
            return
        }
        selectProxyApp(content, false)
        toastSuccess(R.string.toast_success)
    }

    private fun exportProxyApp() {
        var lst = binding.switchBypassApps.isChecked.toString()

        adapter?.blacklist?.forEach block@{
            lst = lst + System.getProperty("line.separator") + it
        }
        Utils.setClipboard(applicationContext, lst)
        toastSuccess(R.string.toast_success)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun selectProxyApp(content: String, force: Boolean): Boolean {
        try {
            val proxyApps = if (TextUtils.isEmpty(content)) {
                Utils.readTextFromAssets(v2RayApplication, "proxy_packagename.txt")
            } else {
                content
            }
            if (TextUtils.isEmpty(proxyApps)) {
                return false
            }

            adapter?.blacklist?.clear()

            if (binding.switchBypassApps.isChecked) {
                adapter?.let { it ->
                    it.apps.forEach block@{
                        val packageName = it.packageName
                        Log.i(AppConfig.TAG, packageName)
                        if (!inProxyApps(proxyApps, packageName, force)) {
                            adapter?.blacklist?.add(packageName)
                            println(packageName)
                            return@block
                        }
                    }
                    it.notifyDataSetChanged()
                }
            } else {
                adapter?.let { it ->
                    it.apps.forEach block@{
                        val packageName = it.packageName
                        Log.i(AppConfig.TAG, packageName)
                        if (inProxyApps(proxyApps, packageName, force)) {
                            adapter?.blacklist?.add(packageName)
                            println(packageName)
                            return@block
                        }
                    }
                    it.notifyDataSetChanged()
                }
            }
        } catch (e: Exception) {

            Log.e(AppConfig.TAG, "Error selecting proxy app", e)
            return false
        }
        return true
    }

    private fun inProxyApps(proxyApps: String, packageName: String, force: Boolean): Boolean {
        if (force) {
            if (packageName == "com.google.android.webview") {
                return false
            }
            if (packageName.startsWith("com.google")) {
                return true
            }
        }

        return proxyApps.indexOf(packageName) >= 0
    }

    private fun filterProxyApp(content: String): Boolean {
        val apps = ArrayList<AppInfo>()

        val key = content.uppercase()
        if (key.isNotEmpty()) {
            appsAll?.forEach {
                if (it.appName.uppercase().indexOf(key) >= 0
                        || it.packageName.uppercase().indexOf(key) >= 0) {
                    apps.add(it)
                }
            }
        } else {
            appsAll?.forEach {
                apps.add(it)
            }
        }

        adapter = PerAppProxyAdapter(this, apps, adapter?.blacklist)
        binding.recyclerView.adapter = adapter
        refreshData()
        return true
    }

    @SuppressLint("NotifyDataSetChanged")
    fun refreshData() {
        adapter?.notifyDataSetChanged()
    }

    //GOLDV2RAY
    @SuppressLint("UseCompatLoadingForDrawables")
    fun loadNetworkAppList(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        val launchableApps = apps.filter { app ->
            pm.getLaunchIntentForPackage(app.packageName) != null
        }

        return launchableApps.map { app ->
            val appName = pm.getApplicationLabel(app).toString()
            val icon = try {
                pm.getApplicationIcon(app)
            } catch (e: Exception) {
                context.getDrawable(android.R.drawable.sym_def_app_icon)!!
            }

            val isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            AppInfo(
                appName = appName,
                packageName = app.packageName,
                appIcon = icon,
                isSystemApp = isSystemApp,
                isSelected = 0
            )
        }.sortedBy { it.appName.lowercase(Locale.getDefault()) }
    }
}
