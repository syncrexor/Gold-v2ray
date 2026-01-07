package me.syncrex.goldv2ray.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Typeface
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuItem
import me.syncrex.goldv2ray.R
import android.os.Bundle
import android.text.Html
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.core.content.ContextCompat
import me.syncrex.goldv2ray.databinding.ActivitySubSettingBinding
import me.syncrex.goldv2ray.dto.SubscriptionItem
import me.syncrex.goldv2ray.handler.MmkvManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.syncrex.goldv2ray.helper.SimpleItemTouchHelperCallback
import me.syncrex.goldv2ray.handler.AngConfigManager
import me.syncrex.goldv2ray.extension.toastError
import me.syncrex.goldv2ray.extension.toastSuccess

class SubSettingActivity : BaseActivity() {
    private val binding by lazy { ActivitySubSettingBinding.inflate(layoutInflater) }

    var subscriptions: List<Pair<String, SubscriptionItem>> = listOf()
    private val adapter by lazy { SubSettingRecyclerAdapter(this) }
    private var mItemTouchHelper: ItemTouchHelper? = null

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

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        //addCustomDividerToRecyclerView(binding.recyclerView, this, R.drawable.custom_divider) //GOLDV2RAY
        binding.recyclerView.adapter = adapter

        mItemTouchHelper = ItemTouchHelper(SimpleItemTouchHelperCallback(adapter))
        mItemTouchHelper?.attachToRecyclerView(binding.recyclerView)
    }

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.action_sub_setting, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.add_config -> {
            startActivity(Intent(this, SubEditActivity::class.java))
            true
        }

        R.id.sub_update -> {
            binding.pbWaiting.show()

            lifecycleScope.launch(Dispatchers.IO) {
                val count = AngConfigManager.updateConfigViaSubAll()
                delay(500L)
                launch(Dispatchers.Main) {
                    if (count > 0) {
                        toastSuccess(R.string.toast_success)
                    } else {
                        toastError(R.string.toast_failure)
                    }
                    binding.pbWaiting.hide()
                }
            }

            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    //GOLDV2RAY
    @SuppressLint("NotifyDataSetChanged")
    fun refreshData() {
        //subscriptions = MmkvManager.decodeSubscriptions() //GOLDV2RAY
        subscriptions = MmkvManager.decodeSubscriptionsNoFree() //GOLDV2RAY
        adapter.notifyDataSetChanged()
    }
}