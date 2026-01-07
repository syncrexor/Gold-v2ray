package me.syncrex.goldv2ray.ui

import android.graphics.Typeface
import android.os.Bundle
import android.text.Html
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.lifecycleScope
import me.syncrex.goldv2ray.AppConfig.ANG_PACKAGE
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.recyclerview.widget.LinearLayoutManager
import me.syncrex.goldv2ray.R
import me.syncrex.goldv2ray.databinding.ActivityLogcatBinding
import me.syncrex.goldv2ray.extension.toast
import me.syncrex.goldv2ray.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.syncrex.goldv2ray.AppConfig
import me.syncrex.goldv2ray.extension.toastSuccess
import java.io.IOException
import java.util.LinkedHashSet

class LogcatActivity : BaseActivity(), SwipeRefreshLayout.OnRefreshListener {
    private val binding by lazy { ActivityLogcatBinding.inflate(layoutInflater) }
    var logsetsAll: MutableList<String> = mutableListOf()
    var logsets: MutableList<String> = mutableListOf()
    private val adapter by lazy { LogcatRecyclerAdapter(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        //supportActionBar?.title = (Html.fromHtml("<font color=\"#FFC107\">" + getString(R.string.title_logcat) + "</font>")) //GOLDV2RAY
        //GOLDV2RAY
        supportActionBar?.let { actionBar ->
            val title = SpannableString(getString(R.string.title_logcat))
            title.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, R.color.colorText)), 0, title.length, 0)
            title.setSpan(StyleSpan(Typeface.BOLD), 0, title.length, 0)
            actionBar.title = title
        }

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        addCustomDividerToRecyclerView(binding.recyclerView, this, R.drawable.custom_divider)
        binding.recyclerView.adapter = adapter
        binding.refreshLayout.setOnRefreshListener(this)
        logsets.add(getString(R.string.pull_down_to_refresh))
    }

    private fun getLogcat() {
        try {
            binding.refreshLayout.isRefreshing = true

            lifecycleScope.launch(Dispatchers.Default) {
                val lst = LinkedHashSet<String>()
                lst.add("logcat")
                lst.add("-d")
                lst.add("-v")
                lst.add("time")
                lst.add("-s")
                lst.add("GoLog,tun2socks,${ANG_PACKAGE},AndroidRuntime,System.err")

                val process = withContext(Dispatchers.IO) {
                    Runtime.getRuntime().exec(lst.toTypedArray())
                }

                val allText = process.inputStream.bufferedReader().use { it.readLines() }.reversed()

                launch(Dispatchers.Main) {
                    logsetsAll = allText.toMutableList()
                    logsets = allText.toMutableList()
                    adapter.notifyDataSetChanged()
                    binding.refreshLayout.isRefreshing = false
                }
            }
        } catch (e: IOException) {
            Log.e(AppConfig.TAG, "Failed to get logcat", e)
        }
    }

    private fun clearLogcat() {
        try {
            lifecycleScope.launch(Dispatchers.Default) {
                val lst = LinkedHashSet<String>()
                lst.add("logcat")
                lst.add("-c")

                withContext(Dispatchers.IO) {
                    val process = Runtime.getRuntime().exec(lst.toTypedArray())
                    process.waitFor()
                }

                launch(Dispatchers.Main) {
                    logsetsAll.clear()
                    logsets.clear()
                    adapter.notifyDataSetChanged()
                }
            }
        } catch (e: IOException) {
            Log.e(AppConfig.TAG, "Failed to clear logcat", e)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_logcat, menu)
        val searchItem = menu.findItem(R.id.search_view)

        if (searchItem != null) {
            val searchView = searchItem.actionView as SearchView

            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean = false
                override fun onQueryTextChange(newText: String?): Boolean {
                    filterLogs(newText)
                    return false
                }
            })

            searchView.setOnCloseListener {
                filterLogs("")
                false
            }
        }

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.copy_all -> {
            Utils.setClipboard(this, logsets.joinToString("\n"))
            toastSuccess(R.string.toast_success)
            true
        }

        R.id.clear_all -> {
            clearLogcat()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private fun filterLogs(content: String?): Boolean {
        val key = content?.trim()
        logsets = if (key.isNullOrEmpty()) {
            logsetsAll.toMutableList()
        } else {
            logsetsAll.filter { it.contains(key) }.toMutableList()
        }

        adapter?.notifyDataSetChanged()
        return true
    }

    override fun onRefresh() {
        getLogcat()
    }
}