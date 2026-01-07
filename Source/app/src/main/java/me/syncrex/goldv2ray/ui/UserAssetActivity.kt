package me.syncrex.goldv2ray.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import me.syncrex.goldv2ray.AppConfig
import me.syncrex.goldv2ray.R
import me.syncrex.goldv2ray.databinding.ActivityUserAssetBinding
import me.syncrex.goldv2ray.databinding.ItemRecyclerUserAssetBinding
import me.syncrex.goldv2ray.dto.AssetUrlItem
import me.syncrex.goldv2ray.extension.toTrafficString
import me.syncrex.goldv2ray.extension.toast
import me.syncrex.goldv2ray.handler.MmkvManager
import me.syncrex.goldv2ray.util.Utils
import me.syncrex.goldv2ray.extension.toastSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.syncrex.goldv2ray.extension.concatUrl
import me.syncrex.goldv2ray.handler.SettingsManager
import me.syncrex.goldv2ray.util.HttpUtil
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.text.DateFormat
import java.util.Date

class UserAssetActivity : BaseActivity() {
    private val binding by lazy { ActivityUserAssetBinding.inflate(layoutInflater) }

    val extDir by lazy { File(Utils.userAssetPath(this)) }
    val geofiles = arrayOf("geosite.dat", "geoip.dat")
    val builtInGeoFiles = arrayOf("geosite.dat", "geoip.dat")

    private val requestStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "*/*"
            intent.addCategory(Intent.CATEGORY_OPENABLE)

            try {
                chooseFile.launch(
                    Intent.createChooser(
                        intent,
                        getString(R.string.title_file_chooser)
                    )
                )
            } catch (ex: android.content.ActivityNotFoundException) {
                toast(R.string.toast_require_file_manager)
            }
        } else {
            toast(R.string.toast_permission_denied)
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            scanQRCodeForAssetURL.launch(Intent(this, ScannerActivity::class.java))
        } else {
            toast(R.string.toast_permission_denied)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        //supportActionBar?.title = (Html.fromHtml("<font color=\"#FFC107\">" + getString(R.string.title_user_asset_setting) + "</font>")) //GOLDV2RAY
        //GOLDV2RAY
        supportActionBar?.let { actionBar ->
            val title = SpannableString(getString(R.string.title_user_asset_setting))
            title.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, R.color.colorText)), 0, title.length, 0)
            title.setSpan(StyleSpan(Typeface.BOLD), 0, title.length, 0)
            actionBar.title = title
        }

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        //addCustomDividerToRecyclerView(binding.recyclerView, this, R.drawable.custom_divider) //GOLDV2RAY
        binding.recyclerView.adapter = UserAssetAdapter()

        binding.tvGeoFilesSourcesSummary.text = getGeoFilesSources()
        binding.layoutGeoFilesSources.setOnClickListener {
            setGeoFilesSources()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.recyclerView.adapter?.notifyDataSetChanged()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_asset, menu)
        return super.onCreateOptionsMenu(menu)
    }

    // Use when to streamline the option selection
    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.add_file -> showFileChooser().let { true }
        R.id.add_url -> startActivity(Intent(this, UserAssetUrlActivity::class.java)).let { true }
        R.id.add_qrcode -> importAssetFromQRcode().let { true }
        R.id.download_file -> downloadGeoFiles().let { true }

        else -> super.onOptionsItemSelected(item)
    }

    private fun getGeoFilesSources(): String {
        return MmkvManager.decodeSettingsString(AppConfig.PREF_GEO_FILES_SOURCES) ?: AppConfig.GEO_FILES_SOURCES.first()
    }

    private fun setGeoFilesSources() {
        //GOLDV2RAY
        val dialog = AlertDialog.Builder(this)
            .setItems(AppConfig.GEO_FILES_SOURCES.toTypedArray()) { _, i ->
                try {
                    val value = AppConfig.GEO_FILES_SOURCES[i]
                    MmkvManager.encodeSettings(AppConfig.PREF_GEO_FILES_SOURCES, value)
                    binding.tvGeoFilesSourcesSummary.text = value
                } catch (e: Exception) {
                    Log.e(AppConfig.TAG, "Failed to set geo files sources", e)
                }
            }
            .show()
        dialog.listView?.let {
            it.setDivider(null)
            it.setSelector(android.R.color.transparent)
            for (i in 0 until it.childCount) {
                (it.getChildAt(i) as? TextView)?.setTextColor(ContextCompat.getColor(this, R.color.colorText))
            }
        }
    }

    private fun showFileChooser() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        requestStoragePermissionLauncher.launch(permission)
    }

    val chooseFile = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == RESULT_OK && uri != null) {
            val assetId = Utils.getUuid()
            runCatching {
                val assetItem = AssetUrlItem(
                    getCursorName(uri) ?: uri.toString(),
                    "file"
                )
                val assetList = MmkvManager.decodeAssetUrls()
                if (assetList.any { it.second.remarks == assetItem.remarks && it.first != assetId }) {
                    toast(R.string.msg_remark_is_duplicate)
                } else {
                    MmkvManager.encodeAsset(assetId, assetItem)
                    copyFile(uri)
                }
            }.onFailure {
                toast(R.string.toast_asset_copy_failed)
                MmkvManager.removeAssetUrl(assetId)
            }
        }
    }

    private fun copyFile(uri: Uri): String {
        val targetFile = File(extDir, getCursorName(uri) ?: uri.toString())
        contentResolver.openInputStream(uri).use { inputStream ->
            targetFile.outputStream().use { fileOut ->
                inputStream?.copyTo(fileOut)
                toastSuccess(R.string.toast_success)
                binding.recyclerView.adapter?.notifyDataSetChanged()
            }
        }
        return targetFile.path
    }

    private fun getCursorName(uri: Uri): String? = try {
        contentResolver.query(uri, null, null, null, null)?.let { cursor ->
            cursor.run {
                if (moveToFirst()) getString(getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                else null
            }.also { cursor.close() }
        }
    } catch (e: Exception) {

        Log.e(AppConfig.TAG, "Failed to get cursor name", e)
        null
    }

    private fun importAssetFromQRcode(): Boolean {
        requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        return true
    }

    private val scanQRCodeForAssetURL = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            importAsset(it.data?.getStringExtra("SCAN_RESULT"))
        }
    }

    private fun importAsset(url: String?): Boolean {
        try {
            if (!Utils.isValidUrl(url)) {
                toast(R.string.toast_invalid_url)
                return false
            }
            // Send URL to UserAssetUrlActivity for Processing
            startActivity(
                Intent(this, UserAssetUrlActivity::class.java)
                    .putExtra(UserAssetUrlActivity.ASSET_URL_QRCODE, url)
            )
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to import asset from URL", e)
            return false
        }
        return true
    }

    private fun downloadGeoFiles() {
        binding.pbWaiting.show()

        toast(R.string.msg_downloading_content)

        val httpPort = SettingsManager.getHttpPort()

        var assets = MmkvManager.decodeAssetUrls()
        assets = addBuiltInGeoItems(assets)

        var resultCount = 0
        lifecycleScope.launch(Dispatchers.IO) {
            assets.forEach {
                try {
                    var result = downloadGeo(it.second, 15000, httpPort)
                    if (!result) {
                        result = downloadGeo(it.second, 15000, 0)
                    }
                    if (result)
                        resultCount++
                } catch (e: Exception) {
                    Log.e(AppConfig.TAG, "Failed to download geo file: ${it.second.remarks}", e)
                }
            }
            withContext(Dispatchers.Main) {
                if (resultCount > 0) {
                    toast(getString(R.string.title_update_config_count, resultCount))
                    binding.recyclerView.adapter?.notifyDataSetChanged()
                    binding.pbWaiting.hide()
                } else {
                    toast(getString(R.string.toast_failure))
                    binding.pbWaiting.hide()
                }
            }
        }
    }

    private fun downloadGeo(item: AssetUrlItem, timeout: Int, httpPort: Int): Boolean {
        val targetTemp = File(extDir, item.remarks + "_temp")
        val target = File(extDir, item.remarks)
        Log.i(AppConfig.TAG, "Downloading geo file: ${item.remarks} from ${item.url}")

        val conn = HttpUtil.createProxyConnection(item.url, httpPort, timeout, timeout, needStream = true) ?: return false
        try {
            val inputStream = conn.inputStream
            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                FileOutputStream(targetTemp).use { output ->
                    inputStream.copyTo(output)
                }

                targetTemp.renameTo(target)
            }
            return true
        } catch (e: Exception) {

            Log.e(AppConfig.TAG, "Failed to download geo file: ${item.remarks}", e)
            return false
        } finally {
            conn.disconnect()
        }
    }

    private fun addBuiltInGeoItems(assets: List<Pair<String, AssetUrlItem>>): List<Pair<String, AssetUrlItem>> {
        val list = mutableListOf<Pair<String, AssetUrlItem>>()
        builtInGeoFiles
            .filter { geoFile -> assets.none { it.second.remarks == geoFile } }
            .forEach {
                list.add(
                    Utils.getUuid() to AssetUrlItem(
                    it,
                        String.format(AppConfig.GITHUB_DOWNLOAD_URL, getGeoFilesSources()).concatUrl(it),
                        locked = true
                ))
            }

        return list + assets
    }

    fun initAssets() {
        lifecycleScope.launch(Dispatchers.Default) {
            SettingsManager.initAssets(this@UserAssetActivity, assets)
            withContext(Dispatchers.Main) {
                binding.recyclerView.adapter?.notifyDataSetChanged()
            }
        }
    }

    inner class UserAssetAdapter : RecyclerView.Adapter<UserAssetViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserAssetViewHolder {
            return UserAssetViewHolder(
                ItemRecyclerUserAssetBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false)
            )
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: UserAssetViewHolder, position: Int) {
            var assets = MmkvManager.decodeAssetUrls()
            assets = addBuiltInGeoItems(assets)
            val item = assets.getOrNull(position) ?: return
//            file with name == item.second.remarks
            val file = extDir.listFiles()?.find { it.name == item.second.remarks }

            holder.itemUserAssetBinding.assetName.text = item.second.remarks

            if (file != null) {
                val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
                holder.itemUserAssetBinding.assetProperties.text =
                    "${file.length().toTrafficString()}  •  ${dateFormat.format(Date(file.lastModified()))}"
            } else {
                holder.itemUserAssetBinding.assetProperties.text = getString(R.string.msg_file_not_found)
            }

            if (item.second.locked == true) {
                holder.itemUserAssetBinding.layoutEdit.visibility = GONE
                //holder.itemUserAssetBinding.layoutRemove.visibility = GONE
            } else {
                holder.itemUserAssetBinding.layoutEdit.visibility = item.second.url.let { if (it == "file") GONE else VISIBLE }
                //holder.itemUserAssetBinding.layoutRemove.visibility = VISIBLE
            }
            holder.itemUserAssetBinding.layoutEdit.setOnClickListener {
                val intent = Intent(this@UserAssetActivity, UserAssetUrlActivity::class.java)
                intent.putExtra("assetId", item.first)
                startActivity(intent)
            }
            holder.itemUserAssetBinding.layoutRemove.setOnClickListener {
                //GOLDV2RAY
                val dialog = AlertDialog.Builder(this@UserAssetActivity)
                    .setMessage(R.string.del_config_comfirm)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        file?.delete()
                        MmkvManager.removeAssetUrl(item.first)
                        initAssets()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .create()
                dialog.show()
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(ContextCompat.getColor(this@UserAssetActivity, R.color.colorText))
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(ContextCompat.getColor(this@UserAssetActivity, R.color.colorText))
            }
        }

        override fun getItemCount(): Int {
            var assets = MmkvManager.decodeAssetUrls()
            assets = addBuiltInGeoItems(assets)
            return assets.size
        }
    }

    class UserAssetViewHolder(val itemUserAssetBinding: ItemRecyclerUserAssetBinding) :
        RecyclerView.ViewHolder(itemUserAssetBinding.root)
}
