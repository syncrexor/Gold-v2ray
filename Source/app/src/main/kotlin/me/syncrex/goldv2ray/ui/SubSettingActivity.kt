package me.syncrex.goldv2ray.ui

import android.content.Intent
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuItem
import me.syncrex.goldv2ray.R
import android.os.Bundle
import android.text.Html
import me.syncrex.goldv2ray.databinding.ActivitySubSettingBinding
import me.syncrex.goldv2ray.dto.SubscriptionItem
import me.syncrex.goldv2ray.util.MmkvManager

class SubSettingActivity : BaseActivity() {
    private lateinit var binding: ActivitySubSettingBinding

    var subscriptions:List<Pair<String, SubscriptionItem>> = listOf()
    private val adapter by lazy { SubSettingRecyclerAdapter(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubSettingBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        //title = getString(R.string.title_sub_setting)
        supportActionBar?.title = (Html.fromHtml("<font color=\"#FFC107\">" + getString(R.string.title_sub_setting) + "</font>"))

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        subscriptions = MmkvManager.decodeSubscriptions()
        adapter.notifyDataSetChanged()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.action_sub_setting, menu)
        menu.findItem(R.id.del_config)?.isVisible = false
        menu.findItem(R.id.save_config)?.isVisible = false

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.add_config -> {
            startActivity(Intent(this, SubEditActivity::class.java))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
