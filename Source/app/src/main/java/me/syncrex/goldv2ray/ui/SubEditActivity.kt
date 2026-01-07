package me.syncrex.goldv2ray.ui

import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.syncrex.goldv2ray.R
import me.syncrex.goldv2ray.databinding.ActivitySubEditBinding
import me.syncrex.goldv2ray.dto.SubscriptionItem
import me.syncrex.goldv2ray.extension.toast
import me.syncrex.goldv2ray.handler.MmkvManager
import me.syncrex.goldv2ray.util.Utils
import me.syncrex.goldv2ray.extension.toastSuccess
import me.syncrex.goldv2ray.AppConfig

class SubEditActivity : BaseActivity() {
    private val binding by lazy {ActivitySubEditBinding.inflate(layoutInflater)}

    var del_config: MenuItem? = null
    var save_config: MenuItem? = null

    private val editSubId by lazy { intent.getStringExtra("subId").orEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        //supportActionBar?.title = (Html.fromHtml("<font color=\"#FFC107\">" + getString(R.string.title_sub_setting) + "</font>")) //GOLDV2RAY
        //GOLDV2RAY
        supportActionBar?.let { actionBar ->
            val title = SpannableString(getString(R.string.title_sub_setting))
            title.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, R.color.colorText)), 0, title.length, 0)
            title.setSpan(StyleSpan(Typeface.BOLD), 0, title.length, 0)
            actionBar.title = title
        }

        val subItem = MmkvManager.decodeSubscription(editSubId)
        if (subItem != null) {
            bindingServer(subItem)
        } else {
            clearServer()
        }
    }

    /**
     * bingding seleced server config
     */
    private fun bindingServer(subItem: SubscriptionItem): Boolean {
        binding.etRemarks.text = Utils.getEditable(subItem.remarks)
        binding.etUrl.text = Utils.getEditable(subItem.url)
        binding.etFilter.text = Utils.getEditable(subItem.filter)
        binding.etIntelligentSelectionFilter.text = Utils.getEditable(subItem.intelligentSelectionFilter)
        binding.chkEnable.isChecked = subItem.enabled
        binding.autoUpdateCheck.isChecked = subItem.autoUpdate
        binding.allowInsecureUrl.isChecked = subItem.allowInsecureUrl
        binding.etPreProfile.text = Utils.getEditable(subItem.prevProfile)
        binding.etNextProfile.text = Utils.getEditable(subItem.nextProfile)
        return true
    }

    /**
     * clear or init server config
     */
    private fun clearServer(): Boolean {
        binding.etRemarks.text = null
        binding.etUrl.text = null
        binding.etFilter.text = null
        binding.etIntelligentSelectionFilter.text = null
        binding.chkEnable.isChecked = true
        binding.etPreProfile.text = null
        binding.etNextProfile.text = null
        return true
    }

    /**
     * save server config
     */
    private fun saveServer(): Boolean {
        val subItem = MmkvManager.decodeSubscription(editSubId) ?: SubscriptionItem()

        subItem.remarks = binding.etRemarks.text.toString()
        subItem.url = binding.etUrl.text.toString()
        subItem.filter = binding.etFilter.text.toString()
        subItem.intelligentSelectionFilter = binding.etIntelligentSelectionFilter.text.toString()
        subItem.enabled = binding.chkEnable.isChecked
        subItem.autoUpdate = binding.autoUpdateCheck.isChecked
        subItem.prevProfile = binding.etPreProfile.text.toString()
        subItem.nextProfile = binding.etNextProfile.text.toString()
        subItem.allowInsecureUrl = binding.allowInsecureUrl.isChecked

        if (TextUtils.isEmpty(subItem.remarks)) {
            toast(R.string.sub_setting_remarks)
            return false
        }
        if (subItem.url.isNotEmpty()) {
            if (!Utils.isValidUrl(subItem.url)) {
                toast(R.string.toast_invalid_url)
                return false
            }

            if (!Utils.isValidSubUrl(subItem.url)) {
                toast(R.string.toast_insecure_url_protocol)
                if (!subItem.allowInsecureUrl) {
                    return false
                }
            }

            MmkvManager.encodeSubscription(editSubId, subItem)
            toastSuccess(R.string.toast_success)
            finish()
            return true
        }

        return false
    }

    /**
     * save server config
     */
    private fun deleteServer(): Boolean {
        if (editSubId.isNotEmpty()) {
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE) == true) {
                        AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                lifecycleScope.launch(Dispatchers.IO) {
                                    MmkvManager.removeSubscription(editSubId)
                                    launch(Dispatchers.Main) {
                                        finish()
                                    }
                                }
                            }
                            .setNegativeButton(android.R.string.cancel) { _, _ ->
                                // do nothing
                            }
                            .show()
            } else {
                lifecycleScope.launch(Dispatchers.IO) {
                    MmkvManager.removeSubscription(editSubId)
                    launch(Dispatchers.Main) {
                        finish()
                    }
                }
            }
        }
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.action_server, menu)
        del_config = menu.findItem(R.id.del_config)
        save_config = menu.findItem(R.id.save_config)

        if (editSubId.isEmpty()) {
            del_config?.isVisible = false
        }

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.del_config -> {
            deleteServer()
            true
        }
        R.id.save_config -> {
            saveServer()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

}
