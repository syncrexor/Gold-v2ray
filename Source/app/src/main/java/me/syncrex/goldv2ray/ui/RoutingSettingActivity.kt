package me.syncrex.goldv2ray.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import me.syncrex.goldv2ray.AppConfig
import me.syncrex.goldv2ray.R
import me.syncrex.goldv2ray.databinding.ActivityRoutingSettingBinding
import me.syncrex.goldv2ray.dto.RulesetItem
import me.syncrex.goldv2ray.extension.toast
import me.syncrex.goldv2ray.handler.MmkvManager
import me.syncrex.goldv2ray.handler.SettingsManager
import me.syncrex.goldv2ray.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.syncrex.goldv2ray.extension.toastError
import me.syncrex.goldv2ray.extension.toastSuccess
import me.syncrex.goldv2ray.helper.SimpleItemTouchHelperCallback
import me.syncrex.goldv2ray.util.JsonUtil

class RoutingSettingActivity : BaseActivity() {
    private val binding by lazy { ActivityRoutingSettingBinding.inflate(layoutInflater) }

    var rulesets: MutableList<RulesetItem> = mutableListOf()
    private val adapter by lazy { RoutingSettingRecyclerAdapter(this) }
    private var mItemTouchHelper: ItemTouchHelper? = null
    private val routing_domain_strategy: Array<out String> by lazy {
        resources.getStringArray(R.array.routing_domain_strategy)
    }
    private val preset_rulesets: Array<out String> by lazy {
        resources.getStringArray(R.array.preset_rulesets)
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            scanQRcodeForRulesets.launch(Intent(this, ScannerActivity::class.java))
        } else {
            toast(R.string.toast_permission_denied)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        title = getString(R.string.routing_settings_title)
        //GOLDV2RAY
        supportActionBar?.let { actionBar ->
            val title = SpannableString(getString(R.string.routing_settings_title))
            title.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, R.color.colorText)), 0, title.length, 0)
            title.setSpan(StyleSpan(Typeface.BOLD), 0, title.length, 0)
            actionBar.title = title
        }

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        //addCustomDividerToRecyclerView(binding.recyclerView, this, R.drawable.custom_divider) //GOLDV2RAY
        binding.recyclerView.adapter = adapter

        mItemTouchHelper = ItemTouchHelper(SimpleItemTouchHelperCallback(adapter))
        mItemTouchHelper?.attachToRecyclerView(binding.recyclerView)

        binding.tvDomainStrategySummary.text = getDomainStrategy()
        binding.layoutDomainStrategy.setOnClickListener {
            setDomainStrategy()
        }
    }

    private fun getDomainStrategy(): String {
        return MmkvManager.decodeSettingsString(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY) ?: routing_domain_strategy.first()
    }

    private fun setDomainStrategy() {
        android.app.AlertDialog.Builder(this).setItems(routing_domain_strategy.asList().toTypedArray()) { _, i ->
            try {
                val value = routing_domain_strategy[i]
                MmkvManager.encodeSettings(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY, value)
                binding.tvDomainStrategySummary.text = value
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "Failed to set domain strategy", e)
            }
        }.show()
    }

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_routing_setting, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.add_rule -> {
            startActivity(Intent(this, RoutingEditActivity::class.java))
            true
        }

        /*R.id.user_asset_setting -> {
            startActivity(Intent(this, UserAssetActivity::class.java))
            true
        }*/

        R.id.import_predefined_rulesets  -> {
            AlertDialog.Builder(this).setMessage(R.string.routing_settings_import_rulesets_tip)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    AlertDialog.Builder(this).setItems(preset_rulesets.asList().toTypedArray()) { _, i ->
                        try {
                            lifecycleScope.launch(Dispatchers.IO) {
                                SettingsManager.resetRoutingRulesetsFromPresets(this@RoutingSettingActivity, i)
                                launch(Dispatchers.Main) {
                                    refreshData()
                                    toastSuccess(R.string.toast_success)
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }.show()
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    //do noting
                }
                .show()
            true
        }

        R.id.import_rulesets_from_qrcode -> {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            true
        }

        R.id.import_rulesets_from_clipboard -> {
            AlertDialog.Builder(this).setMessage(R.string.routing_settings_import_rulesets_tip)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val clipboard = try {
                        Utils.getClipboard(this)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        toastError(R.string.toast_failure)
                        return@setPositiveButton
                    }
                    lifecycleScope.launch(Dispatchers.IO) {
                        val result = SettingsManager.resetRoutingRulesets(clipboard)
                        withContext(Dispatchers.Main) {
                            if (result) {
                                refreshData()
                                toastSuccess(R.string.toast_success)
                            } else {
                                toastError(R.string.toast_failure)
                            }
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    //do nothing
                }
                .show()
            true
        }

        R.id.export_rulesets_to_clipboard -> {
            val rulesetList = MmkvManager.decodeRoutingRulesets()
            if (rulesetList.isNullOrEmpty()) {
                toastError(R.string.toast_failure)
            } else {
                Utils.setClipboard(this, JsonUtil.toJson(rulesetList))
                toastSuccess(R.string.toast_success)
            }
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private val scanQRcodeForRulesets = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            importRulesetsFromQRcode(it.data?.getStringExtra("SCAN_RESULT"))
        }
    }

    private fun importRulesetsFromQRcode(qrcode: String?): Boolean {
        AlertDialog.Builder(this).setMessage(R.string.routing_settings_import_rulesets_tip)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val result = SettingsManager.resetRoutingRulesets(qrcode)
                    withContext(Dispatchers.Main) {
                        if (result) {
                            refreshData()
                            toastSuccess(R.string.toast_success)
                        } else {
                            toastError(R.string.toast_failure)
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                //do nothing
            }
            .show()
        return true
    }

    @SuppressLint("NotifyDataSetChanged")
    fun refreshData() {
        rulesets.clear()
        rulesets.addAll(MmkvManager.decodeRoutingRulesets() ?: mutableListOf())
        adapter.notifyDataSetChanged()
    }
}