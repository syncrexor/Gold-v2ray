package me.syncrex.goldv2ray.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.AssetManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import me.syncrex.goldv2ray.AngApplication
import me.syncrex.goldv2ray.AppConfig
import me.syncrex.goldv2ray.R
import me.syncrex.goldv2ray.dto.ProfileItem
import me.syncrex.goldv2ray.dto.ServersCache
import me.syncrex.goldv2ray.extension.serializable
import me.syncrex.goldv2ray.extension.toast
import me.syncrex.goldv2ray.handler.AngConfigManager
import me.syncrex.goldv2ray.util.MessageUtil
import me.syncrex.goldv2ray.handler.MmkvManager
import me.syncrex.goldv2ray.handler.SpeedtestManager
import me.syncrex.goldv2ray.util.Utils
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.syncrex.goldv2ray.extension.toastError
import me.syncrex.goldv2ray.extension.toastSuccess
import me.syncrex.goldv2ray.handler.SettingsManager
import java.util.Collections

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private var serverList = MmkvManager.decodeServerList()
    var subscriptionId: String = MmkvManager.decodeSettingsString(AppConfig.CACHE_SUBSCRIPTION_ID, "").orEmpty()

    var keywordFilter = ""
    val serversCache = mutableListOf<ServersCache>()
    val isRunning by lazy { MutableLiveData<Boolean>() }
    val updateListAction by lazy { MutableLiveData<Int>() }
    val updateTestResultAction by lazy { MutableLiveData<String>() }
    private val tcpingTestScope by lazy { CoroutineScope(Dispatchers.IO) }

    fun startListenBroadcast() {
        isRunning.value = false
        val mFilter = IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY)
        ContextCompat.registerReceiver(getApplication(), mMsgReceiver, mFilter, Utils.receiverFlags())
        MessageUtil.sendMsg2Service(getApplication(), AppConfig.MSG_REGISTER_CLIENT, "")
    }

    override fun onCleared() {
        getApplication<AngApplication>().unregisterReceiver(mMsgReceiver)
        tcpingTestScope.coroutineContext[Job]?.cancelChildren()
        SpeedtestManager.closeAllTcpSockets()
        Log.i(AppConfig.TAG, "Main ViewModel is cleared")
        super.onCleared()
    }

    fun reloadServerList() {
        serverList = MmkvManager.decodeServerList()
        updateCache()
        updateListAction.value = -1
    }

    fun showManualProfiles() {
        val allServers = MmkvManager.decodeServerList()
        serversCache.clear()
        for (guid in allServers) {
            val profile = MmkvManager.decodeServerConfig(guid) ?: continue
            if (profile.subscriptionId.isNullOrEmpty() && profile.subscriptionId != "-1") {
                serversCache.add(ServersCache(guid, profile))
            }
        }
        updateListAction.value = -1
    }


    fun removeServer(guid: String) {
        serverList.remove(guid)
        MmkvManager.removeServer(guid)
        val index = getPosition(guid)
        if (index >= 0) {
            serversCache.removeAt(index)
        }
    }

    fun swapServer(fromPosition: Int, toPosition: Int) {
        if (subscriptionId.isEmpty()) {
            Collections.swap(serverList, fromPosition, toPosition)
        } else {
            val fromPosition2 = serverList.indexOf(serversCache[fromPosition].guid)
            val toPosition2 = serverList.indexOf(serversCache[toPosition].guid)
            Collections.swap(serverList, fromPosition2, toPosition2)
        }
        Collections.swap(serversCache, fromPosition, toPosition)
        MmkvManager.encodeServerList(serverList)
    }

    //GOLDV2RAY
    @Synchronized
    fun updateCache() {
        //GOLDV2RAY
        if (subscriptionId == "__manual_profiles__") {
            return
        }
        //GOLDV2RAY END

        serversCache.clear()
        for (guid in serverList) {
            val profile = MmkvManager.decodeServerConfig(guid) ?: continue

            if (subscriptionId.isNotEmpty() && subscriptionId != profile.subscriptionId) {
                continue
            }

            if (keywordFilter.isEmpty() || profile.remarks.lowercase().contains(keywordFilter.lowercase())) {
                serversCache.add(ServersCache(guid, profile))
            }
        }
    }

    fun updateConfigViaSubAll(): Int {
        if (subscriptionId.isEmpty()) {
            return AngConfigManager.updateConfigViaSubAll()
        } else {
            val subItem = MmkvManager.decodeSubscription(subscriptionId) ?: return 0
            return AngConfigManager.updateConfigViaSub(Pair(subscriptionId, subItem))
        }
    }

    fun exportAllServer(): Int {
        val serverListCopy =
            if (subscriptionId.isEmpty() && keywordFilter.isEmpty()) {
                serverList
            } else {
                serversCache.map { it.guid }.toList()
            }

        val ret = AngConfigManager.shareNonCustomConfigsToClipboard(
            getApplication<AngApplication>(),
            serverListCopy
        )
        return ret
    }

    fun testAllTcping() {
        tcpingTestScope.coroutineContext[Job]?.cancelChildren()
        SpeedtestManager.closeAllTcpSockets()
        MmkvManager.clearAllTestDelayResults(serversCache.map { it.guid }.toList())
        //updateListAction.value = -1 // update all

        val serversCopy = serversCache.toList() // Create a copy of the list
        for (item in serversCopy) {
            item.profile.let { outbound ->
                val serverAddress = outbound.server
                val serverPort = outbound.serverPort
                if (serverAddress != null && serverPort != null) {
                    tcpingTestScope.launch {
                        val testResult = SpeedtestManager.tcping(serverAddress, serverPort.toInt())
                        launch(Dispatchers.Main) {
                            MmkvManager.encodeServerTestDelayMillis(item.guid, testResult)
                            updateListAction.value = getPosition(item.guid)
                        }
                    }
                }
            }
        }
    }

    //GOLDV2RAY
    fun testAllRealPing() {
        try {
            MessageUtil.sendMsg2TestService(getApplication(), AppConfig.MSG_MEASURE_CONFIG_CANCEL, "")
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Send cancel message failed", e)
        }
        MmkvManager.clearAllTestDelayResults(serversCache.map { it.guid }.toList())
        updateListAction.value = -1

        val serversCopy = serversCache.toList()
        viewModelScope.launch(Dispatchers.Default) {
            for (item in serversCopy) {
                try {
                    MessageUtil.sendMsg2TestService(getApplication(), AppConfig.MSG_MEASURE_CONFIG, item.guid)
                } catch (e: Exception) {
                    Log.e(AppConfig.TAG, "Send config message failed", e)
                }
            }
        }
    }

    fun testCurrentServerRealPing() {
        MessageUtil.sendMsg2Service(getApplication(), AppConfig.MSG_MEASURE_DELAY, "")
    }

    //GOLDV2RAY
    fun subscriptionIdChanged(id: String) {
        if (subscriptionId != id) {
            subscriptionId = id
            MmkvManager.encodeSettings(AppConfig.CACHE_SUBSCRIPTION_ID, subscriptionId)

            if (subscriptionId == "__manual_profiles__") {
                showManualProfiles()
            } else {
                reloadServerList()
            }
        } else {
            if (subscriptionId == "__manual_profiles__") {
                showManualProfiles()
            } else {
                reloadServerList()
            }
        }
    }

    fun getSubscriptions(context: Context): Pair<MutableList<String>?, MutableList<String>?> {
        val subscriptions = MmkvManager.decodeSubscriptions()
        if (subscriptionId.isNotEmpty()
            && !subscriptions.map { it.first }.contains(subscriptionId)
        ) {
            subscriptionIdChanged("")
        }
        if (subscriptions.isEmpty()) {
            return null to null
        }
        val listId = subscriptions.map { it.first }.toMutableList()
        listId.add(0, "")
        val listRemarks = subscriptions.map { it.second.remarks }.toMutableList()
        listRemarks.add(0, context.getString(R.string.all))

        return listId to listRemarks
    }

    fun getPosition(guid: String): Int {
        serversCache.forEachIndexed { index, it ->
            if (it.guid == guid)
                return index
        }
        return -1
    }

    fun removeDuplicateServer(): Int {
        val serversCacheCopy = mutableListOf<Pair<String, ProfileItem>>()
        for (it in serversCache) {
            val config = MmkvManager.decodeServerConfig(it.guid) ?: continue
            serversCacheCopy.add(Pair(it.guid, config))
        }

        val deleteServer = mutableListOf<String>()
        serversCacheCopy.forEachIndexed { index, it ->
            val outbound = it.second
            serversCacheCopy.forEachIndexed { index2, it2 ->
                if (index2 > index) {
                    val outbound2 = it2.second
                    if (outbound.equals(outbound2) && !deleteServer.contains(it2.first)) {
                        deleteServer.add(it2.first)
                    }
                }
            }
        }
        for (it in deleteServer) {
            MmkvManager.removeServer(it)
        }

        return deleteServer.count()
    }

    fun removeAllServer(): Int {
        val count =
            if (subscriptionId.isEmpty() && keywordFilter.isEmpty()) {
                MmkvManager.removeAllServer()
            } else {
                val serversCopy = serversCache.toList()
                for (item in serversCopy) {
                    MmkvManager.removeServer(item.guid)
                }
                serversCache.toList().count()
            }
        return count
    }

    fun removeInvalidServer(): Int {
        var count = 0
        if (subscriptionId.isEmpty() && keywordFilter.isEmpty()) {
            count += MmkvManager.removeInvalidServer("")
        } else {
            val serversCopy = serversCache.toList()
            for (item in serversCopy) {
                count += MmkvManager.removeInvalidServer(item.guid)
            }
        }
        return count
    }

    fun sortByTestResults() {
        data class ServerDelay(var guid: String, var testDelayMillis: Long)

        val serverDelays = mutableListOf<ServerDelay>()
        val serverList = MmkvManager.decodeServerList()
        serverList.forEach { key ->
            val delay = MmkvManager.decodeServerAffiliationInfo(key)?.testDelayMillis ?: 0L
            serverDelays.add(ServerDelay(key, if (delay <= 0L) 999999 else delay))
        }
        serverDelays.sortBy { it.testDelayMillis }

        serverDelays.forEach {
            serverList.remove(it.guid)
            serverList.add(it.guid)
        }

        MmkvManager.encodeServerList(serverList)
    }

    fun createIntelligentSelectionAll() {
        viewModelScope.launch(Dispatchers.IO) {
            val key = AngConfigManager.createIntelligentSelection(
                getApplication<AngApplication>(),
                serversCache.map { it.guid }.toList(),
                subscriptionId
            )

            launch(Dispatchers.Main) {
                if (key.isNullOrEmpty()) {
                    getApplication<AngApplication>().toastError(R.string.toast_failure)
                } else {
                    getApplication<AngApplication>().toastSuccess(R.string.toast_success)
                    MmkvManager.setSelectServer(key)
                    if (subscriptionId == "__manual_profiles__") {
                        showManualProfiles()
                    } else {
                        reloadServerList()
                    }
                }
            }
        }
    }

    //GOLDV2RAY
    fun createIntelligentSelectionAll(targetSubscriptionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            deleteOldSmartConnectionsForTab(targetSubscriptionId)
            val key = AngConfigManager.createIntelligentSelection(
                getApplication<AngApplication>(),
                serversCache.map { it.guid }.toList(),
                targetSubscriptionId
            )

            withContext(Dispatchers.Main) {
                if (key.isNullOrEmpty()) {
                    getApplication<AngApplication>().toastError(R.string.toast_failure)
                } else {
                    getApplication<AngApplication>().toastSuccess(R.string.smart_connection_updated)
                    MmkvManager.setSelectServer(key)

                    if (targetSubscriptionId.equals("__manual_profiles__")) {
                        showManualProfiles()
                    } else {
                        reloadServerList()
                    }
                }
            }
        }
    }

    fun initAssets(assets: AssetManager) {
        viewModelScope.launch(Dispatchers.Default) {
            SettingsManager.initAssets(getApplication<AngApplication>(), assets)
        }
    }

    fun filterConfig(keyword: String) {
        if (keyword == keywordFilter) {
            return
        }
        keywordFilter = keyword
        MmkvManager.encodeSettings(AppConfig.CACHE_KEYWORD_FILTER, keywordFilter)
        reloadServerList()
    }

    private val mMsgReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING -> {
                    isRunning.value = true
                }

                AppConfig.MSG_STATE_NOT_RUNNING -> {
                    isRunning.value = false
                }

                AppConfig.MSG_STATE_START_SUCCESS -> {
                    getApplication<AngApplication>().toast(R.string.toast_services_success)
                    isRunning.value = true
                }

                AppConfig.MSG_STATE_START_FAILURE -> {
                    getApplication<AngApplication>().toast(R.string.toast_services_failure)
                    isRunning.value = false
                }

                AppConfig.MSG_STATE_STOP_SUCCESS -> {
                    isRunning.value = false
                }

                AppConfig.MSG_MEASURE_DELAY_SUCCESS -> {
                    updateTestResultAction.value = intent.getStringExtra("content")
                }

                AppConfig.MSG_MEASURE_CONFIG_SUCCESS -> {
                    val resultPair = intent.serializable<Pair<String, Long>>("content") ?: return
                    MmkvManager.encodeServerTestDelayMillis(resultPair.first, resultPair.second)
                    updateListAction.value = getPosition(resultPair.first)
                }
            }
        }
    }

    //GOLDV2RAY
    fun hasServersInGroup(groupId: String): Boolean {
        val allGuids = MmkvManager.decodeServerList()
        return allGuids.any {
            MmkvManager.decodeServerConfig(it)?.subscriptionId == groupId
        }
    }

    //GOLDV2RAY
    private fun normalizeSubIdForStorage(subId: String?): String {
        val s = subId.orEmpty()
        return if (s == "__manual_profiles__") "" else s
    }
    fun deleteOldSmartConnectionsForTab(targetSubscriptionId: String) {
        val target = normalizeSubIdForStorage(targetSubscriptionId)

        val allGuids = MmkvManager.decodeServerList().toList()
        for (guid in allGuids) {
            val profile = MmkvManager.decodeServerConfig(guid) ?: continue

            val isSmart = (profile.remarks ?: "")
                .trim()
                .startsWith(AppConfig.SMART_MARKER)

            val profileSubId = normalizeSubIdForStorage(profile.subscriptionId)

            if (isSmart && profileSubId == target) {
                MmkvManager.removeServer(guid)
            }
        }
    }
}