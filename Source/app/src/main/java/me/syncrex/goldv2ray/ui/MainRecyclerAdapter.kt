package me.syncrex.goldv2ray.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import android.widget.TextView
import android.widget.Toast
import android.widget.ArrayAdapter
import android.widget.ImageView
import me.syncrex.goldv2ray.AppConfig
import me.syncrex.goldv2ray.R
import me.syncrex.goldv2ray.databinding.ItemQrcodeBinding
import me.syncrex.goldv2ray.databinding.ItemRecyclerFooterBinding
import me.syncrex.goldv2ray.databinding.ItemRecyclerMainBinding
import me.syncrex.goldv2ray.dto.EConfigType
import me.syncrex.goldv2ray.extension.toast
import me.syncrex.goldv2ray.helper.ItemTouchHelperAdapter
import me.syncrex.goldv2ray.helper.ItemTouchHelperViewHolder
import me.syncrex.goldv2ray.handler.V2RayServiceManager
import me.syncrex.goldv2ray.handler.AngConfigManager
import me.syncrex.goldv2ray.handler.MmkvManager
import me.syncrex.goldv2ray.extension.toastError
import me.syncrex.goldv2ray.extension.toastSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.syncrex.goldv2ray.AngApplication.Companion.application
import kotlinx.coroutines.Dispatchers
import me.syncrex.goldv2ray.util.Utils

class MainRecyclerAdapter(val activity: MainActivity) : RecyclerView.Adapter<MainRecyclerAdapter.BaseViewHolder>()
        , ItemTouchHelperAdapter {
    companion object {
        private const val VIEW_TYPE_ITEM = 1
        private const val VIEW_TYPE_FOOTER = 2
    }

    private var mActivity: MainActivity = activity
    private val share_method: Array<out String> by lazy {
        mActivity.resources.getStringArray(R.array.share_method)
    }
    var isRunning = false

    override fun getItemCount() = mActivity.mainViewModel.serversCache.size + 1

    //GOLDV2RAY
    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        if (holder is MainViewHolder) {
            val guid = mActivity.mainViewModel.serversCache[position].guid
            val profile = mActivity.mainViewModel.serversCache[position].profile
            val aff = MmkvManager.decodeServerAffiliationInfo(guid)

            //GOLDV2RAY
            holder.itemMainBinding.infoContainer.setBackgroundResource(R.drawable.background_list_deselected)
            holder.itemMainBinding.tvCounter.text = (position+1).toString()
            //holder.itemMainBinding.tvName.text = profile.remarks
            holder.itemMainBinding.tvName.text = Utils.stripSmartMarker(profile.remarks)
            holder.itemMainBinding.tvTestResult.text = aff?.getTestDelayString().orEmpty()
            if (aff?.testDelayMillis ?: 0L < 0L) {
                holder.itemMainBinding.tvTestResult.text = mActivity.getString(R.string.toast_failure)
                holder.itemMainBinding.tvTestResult.setTextColor(ContextCompat.getColor(mActivity, R.color.colorPingBad))
            } else {
                holder.itemMainBinding.tvTestResult.setTextColor(ContextCompat.getColor(mActivity, R.color.colorPingGood))
            }
            if (guid == MmkvManager.getSelectServer()) {
                holder.itemMainBinding.layoutIndicator.setBackgroundResource(R.color.colorBaseDark)
                holder.itemMainBinding.infoContainer.setBackgroundResource(R.drawable.background_list_selected)
            } else {
                if (aff?.testDelayMillis ?: 0L < 0L)
                    holder.itemMainBinding.layoutIndicator.setBackgroundResource(R.color.colorPingBad)
                else if (aff?.testDelayMillis ?: 0L > 0L)
                    holder.itemMainBinding.layoutIndicator.setBackgroundResource(R.color.colorPingGood)
                else
                    holder.itemMainBinding.layoutIndicator.setBackgroundResource(R.color.colorBackgroundDarker)
            }

            //GOLDV2RAY
            holder.itemMainBinding.tvSubscription.text =
                if (mActivity.mainViewModel.subscriptionId.isEmpty()) {
                    when (profile.subscriptionId) {
                        "__wifi__" -> mActivity.getString(R.string.wifi_tab)
                        "__sim1__" -> mActivity.getString(R.string.sim1_tab)
                        "__sim2__" -> mActivity.getString(R.string.sim2_tab)
                        null, "" -> mActivity.getString(R.string.user)
                        else -> MmkvManager.decodeSubscription(profile.subscriptionId)?.remarks.orEmpty()
                    }
                } else {
                    ""
                }

            var shareOptions = share_method.asList()
            when (profile.configType) {
                EConfigType.CUSTOM -> {
                    holder.itemMainBinding.tvType.text = mActivity.getString(R.string.server_customize_config)
                    shareOptions = shareOptions.takeLast(1)
                }
                else -> {
                    holder.itemMainBinding.tvType.text = profile.configType.name
                }
            }
            // 隐藏主页服务器地址为xxx:xxx:***/xxx.xxx.xxx.***
            val strState = "${
                profile.server?.let {
                    if (it.contains(":"))
                        it.split(":").take(2).joinToString(":", postfix = ":***")
                    else
                        it.split('.').dropLast(1).joinToString(".", postfix = ".***")
                }
            } : ${profile.serverPort}"
            holder.itemMainBinding.tvStatistics.text = strState

            //GOLDV2RAY
            if(!AppConfig.LOCK_PROFILES) {
                holder.itemMainBinding.layoutShare.visibility = View.VISIBLE
                holder.itemMainBinding.layoutShare.setOnClickListener {
                    AlertDialog.Builder(mActivity)
                        .setItems(shareOptions.toTypedArray()) { _, i ->
                            try {
                                when (i) {
                                    0 -> {
                                        if (profile.configType == EConfigType.CUSTOM) {
                                            shareFullContent(guid)
                                        } else {
                                            val ivBinding = ItemQrcodeBinding.inflate(LayoutInflater.from(mActivity))
                                            ivBinding.ivQcode.setImageBitmap(AngConfigManager.share2QRCode(guid))

                                            AlertDialog.Builder(mActivity)
                                                .setView(ivBinding.root)
                                                .setPositiveButton("OK") { _, _ -> }
                                                .show()
                                        }
                                    }

                                    1 -> {
                                        if (AngConfigManager.share2Clipboard(mActivity, guid) == 0) {
                                            mActivity.toastSuccess(R.string.toast_success)
                                        } else {
                                            mActivity.toastError(R.string.toast_failure)
                                        }
                                    }

                                    2 -> shareFullContent(guid)
                                    else -> mActivity.toast("else")
                                }
                            } catch (e: Exception) {
                                Log.e(AppConfig.TAG, "Error when sharing server", e)
                            }
                        }
                        .show()
                }

                //GOLDV2RAY
                holder.itemMainBinding.layoutEdit.visibility = View.VISIBLE
                holder.itemMainBinding.layoutEdit.setOnClickListener {
                    val intent = Intent().putExtra("guid", guid)
                        .putExtra("isRunning", isRunning)
                        .putExtra("createConfigType", profile.configType.value)
                    if (profile.configType == EConfigType.CUSTOM) {
                        mActivity.startActivity(
                            intent.setClass(
                                mActivity,
                                ServerCustomConfigActivity::class.java
                            )
                        )
                    } else {
                        mActivity.startActivity(
                            intent.setClass(
                                mActivity,
                                ServerActivity::class.java
                            )
                        )
                    }
                }

                //GOLDV2RAY
                holder.itemMainBinding.layoutRemove.visibility = View.VISIBLE
                holder.itemMainBinding.layoutRemove.setOnClickListener {
                    if (guid != MmkvManager.getSelectServer()) {
                        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE) == true) {
                            AlertDialog.Builder(mActivity).setMessage(R.string.del_config_comfirm)
                                .setPositiveButton(android.R.string.ok) { _, _ ->
                                    removeServer(guid, position)
                                }
                                .setNegativeButton(android.R.string.cancel) {_, _ ->
                                    //do noting
                                }
                                .show()
                        } else {
                            removeServer(guid, position)
                        }
                    } else {
                        application.toast(R.string.toast_action_not_allowed)
                    }
                }
            }
            else{
                holder.itemMainBinding.layoutShare.visibility = View.GONE
                holder.itemMainBinding.layoutEdit.visibility = View.GONE
                holder.itemMainBinding.layoutRemove.visibility = View.GONE
            }

            holder.itemMainBinding.infoContainer.setOnClickListener {
                val selected = MmkvManager.getSelectServer()
                if (guid != selected) {
                    MmkvManager.setSelectServer(guid)
                    if (guid == MmkvManager.getSelectServer()) {
                        notifyItemChanged(mActivity.mainViewModel.getPosition(selected.orEmpty()))
                    }
                    notifyItemChanged(mActivity.mainViewModel.getPosition(guid))
                    if (isRunning) {
                        mActivity.showCircle()
                        V2RayServiceManager.stopVService(mActivity)
                        mActivity.lifecycleScope.launch {
                            try {
                                delay(500)
                                V2RayServiceManager.startVService(mActivity)
                                mActivity.hideCircle()
                            } catch (e: Exception) {
                                Log.e(AppConfig.TAG, "Failed to restart V2Ray service", e)
                            }
                        }
                        mActivity.autoTest()
                    }
                }
            }

            //GOLDV2RAY
            holder.itemMainBinding.infoContainer.setOnLongClickListener {
                val context = holder.itemView.context
                val currentGroup = profile.subscriptionId ?: ""

                val groupOptions = listOf(
                    Triple("__wifi__", R.drawable.ic_wifi, context.getString(R.string.copy_to_wifi)),
                    Triple("__sim1__", R.drawable.ic_sim1, context.getString(R.string.copy_to_sim1)),
                    Triple("__sim2__", R.drawable.ic_sim2, context.getString(R.string.copy_to_sim2))
                )

                val availableGroups = groupOptions.filter { it.first != currentGroup }

                if (availableGroups.isEmpty()) {
                    Toast.makeText(context, context.getString(R.string.server_already_in_this_group), Toast.LENGTH_SHORT).show()
                    return@setOnLongClickListener true
                }

                val adapter = object : ArrayAdapter<Triple<String, Int, String>>(
                    context,
                    R.layout.item_dialog_category,
                    availableGroups
                ) {
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val inflater = LayoutInflater.from(context)
                        val view = convertView ?: inflater.inflate(R.layout.item_dialog_category, parent, false)

                        val icon = ContextCompat.getDrawable(context, getItem(position)!!.second)
                        val title = getItem(position)!!.third

                        view.findViewById<ImageView>(R.id.item_icon).setImageDrawable(icon)
                        view.findViewById<TextView>(R.id.item_title).text = title

                        return view
                    }
                }

                val serverCache = mActivity.mainViewModel.serversCache[position]
                val serverProfile = serverCache.profile
                val serverGuid = serverCache.guid
                AlertDialog.Builder(context)
                    .setTitle(R.string.select_target_group)
                    .setAdapter(adapter) { _, which ->
                        val selectedGroupId = availableGroups[which].first
                        (context as? MainActivity)?.moveServerToGroup(serverGuid, serverProfile, selectedGroupId)
                    }
                    .show()

                true
            }
            //GOLDV2RAY END
        }
        //if (holder is FooterViewHolder) {
        //    //if (activity?.defaultDPreference?.getPrefBoolean(AppConfig.PREF_INAPP_BUY_IS_PREMIUM, false)) {
        //    if (true) {
        //        holder.itemFooterBinding.layoutEdit.visibility = View.INVISIBLE
        //    } else {
        //        holder.itemFooterBinding.layoutEdit.setOnClickListener {
        //            Utils.openUri(mActivity, "${Utils.decode(AppConfig.promotionUrl)}?t=${System.currentTimeMillis()}")
        //        }
        //    }
        //}
    }

    private fun shareFullContent(guid: String) {
        mActivity.lifecycleScope.launch(Dispatchers.IO) {
            val result = AngConfigManager.shareFullContent2Clipboard(mActivity, guid)
            launch(Dispatchers.Main) {
                if (result == 0) {
                    mActivity.toastSuccess(R.string.toast_success)
                } else {
                    mActivity.toastError(R.string.toast_failure)
                }
            }
        }
    }

    private  fun removeServer(guid: String,position:Int) {
        mActivity.mainViewModel.removeServer(guid)
        notifyItemRemoved(position)
        notifyItemRangeChanged(position, mActivity.mainViewModel.serversCache.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        return when (viewType) {
            VIEW_TYPE_ITEM ->
                MainViewHolder(ItemRecyclerMainBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            else ->
                FooterViewHolder(ItemRecyclerFooterBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == mActivity.mainViewModel.serversCache.size) {
            VIEW_TYPE_FOOTER
        } else {
            VIEW_TYPE_ITEM
        }
    }

    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun onItemSelected() {
            itemView.setBackgroundColor(Color.LTGRAY)
        }

        fun onItemClear() {
            itemView.setBackgroundColor(0)
        }
    }

    class MainViewHolder(val itemMainBinding: ItemRecyclerMainBinding) :
            BaseViewHolder(itemMainBinding.root), ItemTouchHelperViewHolder

    class FooterViewHolder(val itemFooterBinding: ItemRecyclerFooterBinding) :
            BaseViewHolder(itemFooterBinding.root)

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        mActivity.mainViewModel.swapServer(fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    override fun onItemMoveCompleted() {
        // do nothing
    }

    override fun onItemDismiss(position: Int) {
    }
}
