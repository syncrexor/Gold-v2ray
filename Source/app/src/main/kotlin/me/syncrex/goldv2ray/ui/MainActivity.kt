package me.syncrex.goldv2ray.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.Base64
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.adivery.sdk.Adivery
import com.adivery.sdk.AdiveryBannerAdView
import com.adivery.sdk.AdiveryListener
import com.adivery.sdk.BannerSize
import com.goodiebag.pinview.Pinview
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.initialization.InitializationStatus
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.tasks.Task
import com.google.android.material.navigation.NavigationView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.ump.ConsentForm
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.tbruyelle.rxpermissions.RxPermissions
import com.tencent.mmkv.MMKV
import ir.tapsell.plus.AdRequestCallback
import ir.tapsell.plus.AdShowListener
import ir.tapsell.plus.TapsellPlus
import ir.tapsell.plus.TapsellPlusBannerType
import ir.tapsell.plus.TapsellPlusInitListener
import ir.tapsell.plus.model.AdNetworkError
import ir.tapsell.plus.model.AdNetworks
import ir.tapsell.plus.model.TapsellPlusAdModel
import ir.tapsell.plus.model.TapsellPlusErrorModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.drakeet.support.toast.ToastCompat
import me.syncrex.goldv2ray.AppConfig
import me.syncrex.goldv2ray.AppConfig.ANG_PACKAGE
import me.syncrex.goldv2ray.BuildConfig
import me.syncrex.goldv2ray.R
import me.syncrex.goldv2ray.databinding.ActivityMainBinding
import me.syncrex.goldv2ray.dto.EConfigType
import me.syncrex.goldv2ray.extension.toast
import me.syncrex.goldv2ray.helper.SimpleItemTouchHelperCallback
import me.syncrex.goldv2ray.service.V2RayServiceManager
import me.syncrex.goldv2ray.util.AngConfigManager
import me.syncrex.goldv2ray.util.MmkvManager
import me.syncrex.goldv2ray.util.Utils
import me.syncrex.goldv2ray.viewmodel.MainViewModel
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.security.Key
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Objects
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class MainActivity : BaseActivity(), NavigationView.OnNavigationItemSelectedListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var consentInformation: ConsentInformation
    private lateinit var consentForm: ConsentForm
    private val adapter by lazy { MainRecyclerAdapter(this) }
    private val mainStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_MAIN, MMKV.MULTI_PROCESS_MODE) }
    private val settingsStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SETTING, MMKV.MULTI_PROCESS_MODE) }
    private val requestVpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            startV2Ray()
        }
    }
    private var mItemTouchHelper: ItemTouchHelper? = null
    val mainViewModel: MainViewModel by viewModels()

    private lateinit var aboutArea: ConstraintLayout
    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        title = getString(R.string.servers)
        setSupportActionBar(binding.toolbar)

        val toolbar: Toolbar = binding.toolbar
        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.customColorGold))

        val shp: SharedPreferences = getSharedPreferences("GOLD", Context.MODE_PRIVATE)
        AppConfig.LOCK_PROFILES = shp.getBoolean("SHARE_LOCK", false)
        AppConfig.STORE_MODE = shp.getBoolean("STORE_MODE", false)

        // Remote Configs
        AppConfig.ADS_BANNER_STATUS = shp.getBoolean("ADS_BANNER_STATUS", true)
        AppConfig.ADS_BANNER_TIMER_HOUR = shp.getInt("ADS_BANNER_TIMER_HOUR", 1)
        AppConfig.ADS_BANNER_TIMER_MINUTE = shp.getInt("ADS_BANNER_TIMER_MINUTE", 1)
        AppConfig.ADS_INTERSTITIAL_STATUS = shp.getBoolean("ADS_INTERSTITIAL_STATUS", true)
        AppConfig.ADS_INTERSTITIAL_SHOW_ON_TIME = shp.getInt("ADS_INTERSTITIAL_SHOW_ON_TIME", 15)
        AppConfig.ADS_SERVICE = shp.getString("ADS_SERVICE", "ADMOB").toString()
        AppConfig.FREE_SERVERS_A = shp.getString("FREE_SERVERS_A", "REPLACE_ME").toString()
        AppConfig.FREE_SERVERS_B = shp.getString("FREE_SERVERS_B", "REPLACE_ME").toString()
        AppConfig.FREE_SERVERS_C = shp.getString("FREE_SERVERS_C", "REPLACE_ME").toString()
        AppConfig.ADIVERY_ID_APP = shp.getString("ADIVERY_ID_APP", "REPLACE_ME").toString()
        AppConfig.ADIVERY_ID_BANNER = shp.getString("ADIVERY_ID_BANNER", "REPLACE_ME").toString()
        AppConfig.ADIVERY_ID_INTERSTITIAL = shp.getString("ADIVERY_ID_INTERSTITIAL", "REPLACE_ME").toString()
        AppConfig.TAPSELL_ID_APP = shp.getString("TAPSELL_ID_APP", "REPLACE_ME").toString()
        AppConfig.TAPSELL_ID_BANNER = shp.getString("TAPSELL_ID_BANNER", "REPLACE_ME").toString()
        AppConfig.TAPSELL_ID_INTERSTITIAL = shp.getString("TAPSELL_ID_INTERSTITIAL", "REPLACE_ME").toString()

        FirebaseApp.initializeApp(this)
        val mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()
        val configSettings = FirebaseRemoteConfigSettings.Builder().setMinimumFetchIntervalInSeconds(60).build()
        mFirebaseRemoteConfig.setConfigSettingsAsync(configSettings)
        mFirebaseRemoteConfig.setDefaultsAsync(R.xml.remote_configs)
        mFirebaseRemoteConfig.fetchAndActivate().addOnCompleteListener(this) { task: Task<Boolean?> ->
            if (task.isSuccessful) {
                val ADS_BANNER_STATUS = mFirebaseRemoteConfig.getBoolean("ADS_BANNER_STATUS")
                val ADS_BANNER_TIMER_HOUR = mFirebaseRemoteConfig.getLong("ADS_BANNER_TIMER_HOUR").toInt()
                val ADS_BANNER_TIMER_MINUTE = mFirebaseRemoteConfig.getLong("ADS_BANNER_TIMER_MINUTE").toInt()
                val ADS_INTERSTITIAL_STATUS = mFirebaseRemoteConfig.getBoolean("ADS_INTERSTITIAL_STATUS")
                val ADS_INTERSTITIAL_SHOW_ON_TIME = mFirebaseRemoteConfig.getLong("ADS_INTERSTITIAL_SHOW_ON_TIME").toInt()
                val ADS_SERVICE = mFirebaseRemoteConfig.getString("ADS_SERVICE")
                val FREE_SERVERS_A = mFirebaseRemoteConfig.getString("FREE_SERVERS_A")
                val FREE_SERVERS_B = mFirebaseRemoteConfig.getString("FREE_SERVERS_B")
                val FREE_SERVERS_C = mFirebaseRemoteConfig.getString("FREE_SERVERS_C")
                val ADIVERY_ID_APP = mFirebaseRemoteConfig.getString("ADIVERY_ID_APP")
                val ADIVERY_ID_BANNER = mFirebaseRemoteConfig.getString("ADIVERY_ID_BANNER")
                val ADIVERY_ID_INTERSTITIAL = mFirebaseRemoteConfig.getString("ADIVERY_ID_INTERSTITIAL")
                val TAPSELL_ID_APP = mFirebaseRemoteConfig.getString("TAPSELL_ID_APP")
                val TAPSELL_ID_BANNER = mFirebaseRemoteConfig.getString("TAPSELL_ID_BANNER")
                val TAPSELL_ID_INTERSTITIAL = mFirebaseRemoteConfig.getString("TAPSELL_ID_INTERSTITIAL")

                shp.edit().putBoolean("ADS_BANNER_STATUS", ADS_BANNER_STATUS).apply()
                shp.edit().putInt("ADS_BANNER_TIMER_HOUR", ADS_BANNER_TIMER_HOUR).apply()
                shp.edit().putInt("ADS_BANNER_TIMER_MINUTE", ADS_BANNER_TIMER_MINUTE).apply()
                shp.edit().putBoolean("ADS_INTERSTITIAL_STATUS", ADS_INTERSTITIAL_STATUS).apply()
                shp.edit().putInt("ADS_INTERSTITIAL_SHOW_ON_TIME", ADS_INTERSTITIAL_SHOW_ON_TIME).apply()
                shp.edit().putString("ADS_SERVICE", ADS_SERVICE).apply()
                shp.edit().putString("FREE_SERVERS_A", FREE_SERVERS_A).apply()
                shp.edit().putString("FREE_SERVERS_B", FREE_SERVERS_B).apply()
                shp.edit().putString("FREE_SERVERS_C", FREE_SERVERS_C).apply()
                shp.edit().putString("ADIVERY_ID_APP", ADIVERY_ID_APP).apply()
                shp.edit().putString("ADIVERY_ID_BANNER", ADIVERY_ID_BANNER).apply()
                shp.edit().putString("ADIVERY_ID_INTERSTITIAL", ADIVERY_ID_INTERSTITIAL).apply()
                shp.edit().putString("TAPSELL_ID_APP", TAPSELL_ID_APP).apply()
                shp.edit().putString("TAPSELL_ID_BANNER", TAPSELL_ID_BANNER).apply()
                shp.edit().putString("TAPSELL_ID_INTERSTITIAL", TAPSELL_ID_INTERSTITIAL).apply()

                AppConfig.ADS_BANNER_STATUS = ADS_BANNER_STATUS
                AppConfig.ADS_BANNER_TIMER_HOUR = ADS_BANNER_TIMER_HOUR
                AppConfig.ADS_BANNER_TIMER_MINUTE = ADS_BANNER_TIMER_MINUTE
                AppConfig.ADS_INTERSTITIAL_STATUS = ADS_INTERSTITIAL_STATUS
                AppConfig.ADS_INTERSTITIAL_SHOW_ON_TIME = ADS_INTERSTITIAL_SHOW_ON_TIME
                AppConfig.ADS_SERVICE = ADS_SERVICE
                AppConfig.FREE_SERVERS_A = FREE_SERVERS_A
                AppConfig.FREE_SERVERS_B = FREE_SERVERS_B
                AppConfig.FREE_SERVERS_C = FREE_SERVERS_C
                AppConfig.ADIVERY_ID_APP = ADIVERY_ID_APP
                AppConfig.ADIVERY_ID_BANNER = ADIVERY_ID_BANNER
                AppConfig.ADIVERY_ID_INTERSTITIAL = ADIVERY_ID_INTERSTITIAL
                AppConfig.TAPSELL_ID_APP = TAPSELL_ID_APP
                AppConfig.TAPSELL_ID_BANNER = TAPSELL_ID_BANNER
                AppConfig.TAPSELL_ID_INTERSTITIAL = TAPSELL_ID_INTERSTITIAL
            }
        }
        //

        // GDPR
        val params = ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(false).build()
        consentInformation = UserMessagingPlatform.getConsentInformation(this)
        consentInformation.requestConsentInfoUpdate(this, params, {
            if (consentInformation.isConsentFormAvailable()) {
                UserMessagingPlatform.loadConsentForm(this, { consentForm: ConsentForm ->
                    this.consentForm = consentForm
                    if (consentInformation.getConsentStatus() === ConsentInformation.ConsentStatus.REQUIRED ||
                        !shp.getBoolean("GDPR_Allow", true)
                    ) {
                        consentForm.show(this) { formError: FormError? ->
                            if (consentInformation.getConsentStatus() === ConsentInformation.ConsentStatus.OBTAINED) {
                                shp.edit().putBoolean("GDPR_Allow", true).apply()
                            } else {
                                shp.edit().putBoolean("GDPR_Allow", false).apply()
                            }
                        }
                    }
                }) { formError: FormError? -> }
            }
        }) { formError -> }
        //

        binding.fab.setOnClickListener {
            if (mainViewModel.isRunning.value == true) {
                Utils.stopVService(this)
            } else if (settingsStorage?.decodeString(AppConfig.PREF_MODE) ?: "VPN" == "VPN") {
                val intent = VpnService.prepare(this)
                if (intent == null) {
                    startV2Ray()
                    autoTest()
                } else {
                    requestVpnPermission.launch(intent)
                }
            } else {
                startV2Ray()
                autoTest()
            }
        }
        binding.layoutTest.setOnClickListener {
            if (mainViewModel.isRunning.value == true) {
                setTestState(getString(R.string.connection_test_testing))
                mainViewModel.testCurrentServerRealPing()
            } else {
//                tv_test_state.text = getString(R.string.connection_test_fail)
            }
        }

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        val callback = SimpleItemTouchHelperCallback(adapter)
        mItemTouchHelper = ItemTouchHelper(callback)
        mItemTouchHelper?.attachToRecyclerView(binding.recyclerView)

        // List scrolls
        binding.recyclerView.apply {
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    scrollListener(dy)
                    if((!recyclerView.canScrollVertically(1) && dy > 0) ||
                        (!recyclerView.canScrollVertically(-1) && dy < 0))
                    {
                        goUpState(false)
                        goDownState(false)
                    }
                }
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                }
            })
        }

        val toggle = ActionBarDrawerToggle(
                this, binding.drawerLayout, binding.toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        binding.navView.setNavigationItemSelectedListener(this)
        //"v${BuildConfig.VERSION_NAME} (${SpeedtestUtil.getLibVersion()})".also { binding.version.text = it }

        setupViewModel()
        copyAssets()
        migrateLegacy()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RxPermissions(this)
                .request(Manifest.permission.POST_NOTIFICATIONS)
                .subscribe {
                    if (!it)
                        toast(R.string.toast_permission_denied)
                }
        }

        //val sharedPref: SharedPreferences = getSharedPreferences("GOLD", Context.MODE_PRIVATE)
        //if(sharedPref.getBoolean("AutoPing", true))
        //    Handler(Looper.getMainLooper()).postDelayed({
        //        mainViewModel.testAllTcping()
        //    }, 1000)
        aboutArea = findViewById(R.id.about_area)
        aboutArea.setOnClickListener {it.visibility = View.GONE}
        val aboutVersion = findViewById<TextView>(R.id.about_version)
        if (aboutVersion != null) {
            try {
                //aboutVersion.text = "v " + packageManager.getPackageInfo(packageName, 0).versionName
                aboutVersion.text = "v " + BuildConfig.VERSION_NAME
            } catch (e: PackageManager.NameNotFoundException) {
                
                aboutVersion.visibility = View.INVISIBLE
            }
        }
        
        val appRateOnRun = 30
        if (!shp.getBoolean("appRateShown", false)) {
            var appRunsCounter: Int = shp.getInt("appRunsCounter", 0)
            appRunsCounter++
            shp.edit().putInt("appRunsCounter", appRunsCounter).apply()
            if (appRunsCounter == appRateOnRun) {
                val rateDialog = AlertDialog.Builder(this, R.style.DialogStyle).create()
                rateDialog.setTitle(getString(R.string.support_us))
                rateDialog.setMessage(getString(R.string.support_tip))
                rateDialog.setButton(
                    AlertDialog.BUTTON_POSITIVE, getString(R.string.rate_app)
                ) { dialog: DialogInterface?, ids: Int ->
                    rate_app()
                    shp.edit().putBoolean("appRateShown", true).apply()
                }
                rateDialog.setButton(
                    AlertDialog.BUTTON_NEGATIVE, getString(R.string.later)
                ) { dialog: DialogInterface, ids: Int -> dialog.cancel() }
                rateDialog.setButton(
                    AlertDialog.BUTTON_NEUTRAL, getString(R.string.dont_show_again)
                ) { dialog: DialogInterface?, ids: Int ->
                    shp.edit().putBoolean("appRateShown", true).apply()
                }
                rateDialog.show()
            } else if (appRunsCounter > appRateOnRun) {
                shp.edit().putInt("appRunsCounter", 0).apply()
            }
        }

        // Scroll buttons
        val listUp:ImageButton = findViewById(R.id.list_go_up)
        val listDown:ImageButton = findViewById(R.id.list_go_down)
        listUp.setOnClickListener { v -> listGoUp() }
        listDown.setOnClickListener { v -> listGoDown() }

        // Admob
        MobileAds.initialize(this) { initializationStatus: InitializationStatus? -> }

        // Adivery
        Adivery.configure(getApplication() , AppConfig.ADIVERY_ID_APP)

        // Tapsell
        TapsellPlus.initialize(this, AppConfig.TAPSELL_ID_APP, object : TapsellPlusInitListener {
            override fun onInitializeSuccess(adNetworks: AdNetworks) {}
            override fun onInitializeFailed(adNetworks: AdNetworks, adNetworkError: AdNetworkError) {}
        })

        // Ads
        bannerShow()
        interstitialShow()

        // Share Lock
        val lock_area_close:ImageView = findViewById(R.id.lock_area_close)
        lock_area_close.setOnClickListener { shareLockHide() }
        val lock_area_cancel:Button = findViewById(R.id.lock_area_cancel)
        lock_area_cancel.setOnClickListener { shareLockHide() }
        val lock_area_confirm:Button = findViewById(R.id.lock_area_confirm)
        lock_area_confirm.setOnClickListener {
            lockMode(false)
        }
        lock_area_confirm.setOnLongClickListener{
            lockMode(true)
            true
        }

        // Share Unlock
        val unlock_area_close:ImageView = findViewById(R.id.unlock_area_close)
        unlock_area_close.setOnClickListener { shareUnlockHide() }
        val unlock_area_cancel:Button = findViewById(R.id.unlock_area_cancel)
        unlock_area_cancel.setOnClickListener { shareUnlockHide() }
        val unlock_area_confirm:Button = findViewById(R.id.unlock_area_confirm)
        unlock_area_confirm.setOnClickListener {
            val unlock_code: Pinview = findViewById(R.id.unlock_code)
            if (unlock_code.value.length < AppConfig.PIN_CODE_LENGHT) {
                dialogAlert(
                    getString(R.string.pin_code_error),
                    getString(R.string.enter_the_pin_codes_completely)
                )
                unlock_code.clearValue()
            } else {
                val pinCode = decryptPin(shp.getString("PIN_CODE", ""), AppConfig.ENCRYPTION_SECRET_KEY)
                if(unlock_code.value != pinCode){
                    dialogAlert(
                        getString(R.string.pin_code_error),
                        getString(R.string.the_pin_code_is_incorrect)
                    )
                    unlock_code.clearValue()
                }
                else{
                    shp.edit().putBoolean("SHARE_LOCK", false).apply()
                    shp.edit().putBoolean("STORE_MODE", false).apply()
                    AppConfig.LOCK_PROFILES = false
                    AppConfig.STORE_MODE = false

                    adapter.notifyDataSetChanged()

                    lockSharingChTitle(false)
                    dialogAlert(
                        getString(R.string.congratulations),
                        getString(R.string.unlock_sharing_active_tip)
                    )
                    shareUnlockHide()
                }
            }
        }

        // First Run
        if(shp.getBoolean("FIRST_RUN", true)){
            shp.edit().putBoolean("FIRST_RUN", false).apply()

            val settingsStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SETTING, MMKV.MULTI_PROCESS_MODE) }
            settingsStorage?.encode(AppConfig.PREF_START_SCAN_IMMEDIATE, true)
            settingsStorage?.encode(AppConfig.PREF_ALLOW_INSECURE, true)
            settingsStorage?.encode(AppConfig.PREF_PREFER_IPV6, true)
            settingsStorage?.encode(AppConfig.PREF_SPEED_ENABLED, true)
            settingsStorage?.encode(AppConfig.PREF_CONFIRM_REMOVE, true)

            fsuDownloader()
        }
    }

    fun autoTest(){
        Observable.timer(1000, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                try {
                    setTestState(getString(R.string.connection_test_testing))
                    mainViewModel.testCurrentServerRealPing()
                }
                catch (e: Exception){
                    e.printStackTrace()
                }
            }
    }

    private fun encryptPin(value: String, secretKey: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        val key = generateKey(secretKey)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encryptedValue = cipher.doFinal(value.toByteArray())
        return Base64.encodeToString(encryptedValue, Base64.DEFAULT)
    }

    private fun decryptPin(value: String?, secretKey: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        val key = generateKey(secretKey)
        cipher.init(Cipher.DECRYPT_MODE, key)
        val decodedValue = Base64.decode(value, Base64.DEFAULT)
        val decryptedValue = cipher.doFinal(decodedValue)
        return String(decryptedValue)
    }

    private fun generateKey(secretKey: String): Key {
        return SecretKeySpec(secretKey.toByteArray(), "AES")
    }

    private fun dialogAlert(title: String, message: String){
        val builder = AlertDialog.Builder(this, R.style.DialogStyle)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton(getString(R.string.got_it)) { dialog, which ->
            dialog.dismiss()
        }
        val dialog = builder.create()
        dialog.show()
    }

    private fun shareLockShow(){
        val about_area:ConstraintLayout = findViewById(R.id.about_area)
        about_area.visibility = View.GONE

        val lock_code: Pinview = findViewById(R.id.lock_code)
        lock_code.clearValue()

        val lock_code_repeat: Pinview = findViewById(R.id.lock_code_repeat)
        lock_code_repeat.clearValue()

        val lock_area:ConstraintLayout = findViewById(R.id.lock_area)
        lock_area.visibility = View.VISIBLE
    }

    private fun shareLockHide(){
        val lock_area:ConstraintLayout = findViewById(R.id.lock_area)
        lock_area.visibility = View.GONE
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }

    private fun shareUnlockShow(){
        val about_area:ConstraintLayout = findViewById(R.id.about_area)
        about_area.visibility = View.GONE

        val unlock_code: Pinview = findViewById(R.id.unlock_code)
        unlock_code.clearValue()

        val unlock_area:ConstraintLayout = findViewById(R.id.unlock_area)
        unlock_area.visibility = View.VISIBLE
    }

    private fun shareUnlockHide(){
        val unlock_area:ConstraintLayout = findViewById(R.id.unlock_area)
        unlock_area.visibility = View.GONE
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }

    private fun adsAllowToShow(gotAd: String?): Boolean {
        val shp = getSharedPreferences("GOLD", MODE_PRIVATE)
        var allowShowAd = false
        try {
            @SuppressLint("SimpleDateFormat") val dateTimeFormat =
                SimpleDateFormat("yyyy/MM/dd-HH:mm:ss", Locale.US)
            val latestTime = shp.getString(gotAd, null)
            val currentTime: String = dateTimeFormat.format(Date(System.currentTimeMillis()))
            if (latestTime != null) {
                val latestShowTime: Date = dateTimeFormat.parse(latestTime)
                val timesPlus: Int
                timesPlus = AppConfig.ADS_BANNER_TIMER_HOUR * 3600000 + AppConfig.ADS_BANNER_TIMER_MINUTE * 60000
                Objects.requireNonNull(latestShowTime).setTime(latestShowTime.getTime() + timesPlus)
                val timeForCompare: Date = dateTimeFormat.parse(currentTime)
                if (Objects.requireNonNull(timeForCompare).compareTo(latestShowTime) >= 0) {
                    allowShowAd = true
                    shp.edit().putString(gotAd, currentTime).apply()
                }
            } else shp.edit().putString(gotAd, currentTime).apply()
        } catch (e: java.lang.Exception) {
            allowShowAd = false
            e.printStackTrace()
        }
        return allowShowAd
    }

    private fun bannerShow(){
        if(AppConfig.ADS_BANNER_STATUS && adsAllowToShow("Ads_Banner_Latest_Show_Time")) {
            if (AppConfig.ADS_SERVICE.equals("ADMOB")) {
                if(getSharedPreferences("GOLD", MODE_PRIVATE).getBoolean("GDPR_Allow" , false)){
                    val container = findViewById<View>(R.id.ads_container_admob)
                    val adsBanner = AdView(this)
                    val request: AdRequest = AdRequest.Builder().build()
                    adsBanner.setAdSize(AdSize.BANNER)
                    adsBanner.setAdUnitId(AppConfig.ADMOB_ID_BANNER)
                    (container as RelativeLayout).addView(adsBanner)
                    adsBanner.loadAd(request)
                }
            }
            if (AppConfig.ADS_SERVICE.equals("ADIVERY")) {
                val adivery_container =
                    findViewById<AdiveryBannerAdView>(R.id.ads_container_adivery)
                adivery_container.setBannerSize(BannerSize.BANNER)
                adivery_container.setPlacementId(AppConfig.ADIVERY_ID_BANNER)
                adivery_container.loadAd()
            }
            if (AppConfig.ADS_SERVICE.equals("TAPSELL")) {
                TapsellPlus.requestStandardBannerAd(
                    this,
                    AppConfig.TAPSELL_ID_BANNER,
                    TapsellPlusBannerType.BANNER_320x50,
                    object : AdRequestCallback() {
                        override fun response(tapsellPlusAdModel: TapsellPlusAdModel) {
                            super.response(tapsellPlusAdModel)
                            TapsellPlus.showStandardBannerAd(this@MainActivity,
                                tapsellPlusAdModel.responseId,
                                findViewById<ViewGroup>(R.id.ads_container_tapsell),
                                object : AdShowListener() {
                                    override fun onOpened(tapsellPlusAdModel: TapsellPlusAdModel) {
                                        super.onOpened(tapsellPlusAdModel)
                                    }

                                    override fun onError(tapsellPlusErrorModel: TapsellPlusErrorModel) {
                                        super.onError(tapsellPlusErrorModel)
                                    }
                                })
                        }

                        override fun error(message: String) {}
                    })
            }
        }
    }

    private fun interstitialShow(){
        if (AppConfig.ADS_INTERSTITIAL_STATUS) {
            val shp = getSharedPreferences("GOLD", MODE_PRIVATE)
            var timerCounter = shp.getInt("Ads_Interstitial_Latest_Count", 0)
            timerCounter += 1
            shp.edit().putInt("Ads_Interstitial_Latest_Count", timerCounter).apply()
            if (timerCounter == AppConfig.ADS_INTERSTITIAL_SHOW_ON_TIME - 1) {
                val ad_tips: View = findViewById(R.id.ad_tips)
                ad_tips.visibility = View.VISIBLE
                Handler().postDelayed({
                    runOnUiThread {
                        val ad_tips: View = findViewById(R.id.ad_tips)
                        val fadeOutAnimation: Animation = AlphaAnimation(1.0f, 0.0f)
                        fadeOutAnimation.duration = 1000
                        fadeOutAnimation.setAnimationListener(object : Animation.AnimationListener {
                            override fun onAnimationStart(animation: Animation) {}
                            override fun onAnimationEnd(animation: Animation) {
                                ad_tips.visibility = View.GONE
                            }

                            override fun onAnimationRepeat(animation: Animation) {}
                        })
                        ad_tips.startAnimation(fadeOutAnimation)
                    }
                }, 6 * 1000)
            }
            if (timerCounter >= AppConfig.ADS_INTERSTITIAL_SHOW_ON_TIME) {
                shp.edit().putInt("Ads_Interstitial_Latest_Count", 0).apply()
                if (adsAllowToShow("Ads_Interstitial_Latest_Show_Time")) {
                    if (AppConfig.ADS_SERVICE == "ADMOB") {
                        if(shp.getBoolean("GDPR_Allow" , false)) {
                            var adsInterstitial: InterstitialAd?
                            val request: AdRequest = AdRequest.Builder().build()
                            InterstitialAd.load(this,
                                AppConfig.ADMOB_ID_INTERSTITIAL,
                                request,
                                object : InterstitialAdLoadCallback() {
                                    override fun onAdLoaded(interstitialAd: InterstitialAd) {
                                        adsInterstitial = interstitialAd
                                        adsInterstitial?.show(this@MainActivity)
                                    }

                                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                                        adsInterstitial = null
                                    }
                                })
                        }
                    }
                    if (AppConfig.ADS_SERVICE == "ADIVERY") {
                        Adivery.prepareInterstitialAd(this, AppConfig.ADIVERY_ID_INTERSTITIAL)
                        Adivery.addGlobalListener(object : AdiveryListener() {
                            override fun onInterstitialAdLoaded(placementId: String) {
                                if (Adivery.isLoaded(AppConfig.ADIVERY_ID_INTERSTITIAL))
                                    Adivery.showAd(AppConfig.ADIVERY_ID_INTERSTITIAL)
                            }
                        })
                    }
                    if (AppConfig.ADS_SERVICE.equals("TAPSELL")) {
                        TapsellPlus.requestInterstitialAd(
                            this,
                            AppConfig.TAPSELL_ID_INTERSTITIAL,
                            object : AdRequestCallback() {
                                override fun response(tapsellPlusAdModel: TapsellPlusAdModel) {
                                    super.response(tapsellPlusAdModel)
                                    TapsellPlus.showInterstitialAd(
                                        this@MainActivity,
                                        tapsellPlusAdModel.responseId,
                                        object : AdShowListener() {
                                            override fun onOpened(tapsellPlusAdModel: TapsellPlusAdModel) {
                                                super.onOpened(tapsellPlusAdModel)
                                            }

                                            override fun onClosed(tapsellPlusAdModel: TapsellPlusAdModel) {
                                                super.onClosed(tapsellPlusAdModel)
                                            }

                                            override fun onError(tapsellPlusErrorModel: TapsellPlusErrorModel) {
                                                super.onError(tapsellPlusErrorModel)
                                            }
                                        })
                                }

                                override fun error(message: String) {}
                            })
                    }
                }
            }
        }
    }

    private fun setupViewModel() {
        mainViewModel.updateListAction.observe(this) { index ->
            if (index >= 0) {
                adapter.notifyItemChanged(index)
            } else {
                adapter.notifyDataSetChanged()
            }
        }
        mainViewModel.updateTestResultAction.observe(this) { setTestState(it) }
        mainViewModel.isRunning.observe(this) { isRunning ->
            adapter.isRunning = isRunning
            if (isRunning) {
                if (!Utils.getDarkModeStatus(this)) {
                    binding.fab.setImageResource(R.drawable.ic_stat_name)
                }
                binding.fab.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.customColorGold))
                setTestState(getString(R.string.connection_connected))
                binding.layoutTest.isFocusable = true
            } else {
                if (!Utils.getDarkModeStatus(this)) {
                    binding.fab.setImageResource(R.drawable.ic_stat_name)
                }
                binding.fab.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.color_fab_grey))
                setTestState(getString(R.string.connection_not_connected))
                binding.layoutTest.isFocusable = false
            }
            hideCircle()
        }
        mainViewModel.startListenBroadcast()
    }

    private fun copyAssets() {
        val extFolder = Utils.userAssetPath(this)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val geo = arrayOf("geosite.dat", "geoip.dat")
                assets.list("")
                        ?.filter { geo.contains(it) }
                        ?.filter { !File(extFolder, it).exists() }
                        ?.forEach {
                            val target = File(extFolder, it)
                            assets.open(it).use { input ->
                                FileOutputStream(target).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            Log.i(ANG_PACKAGE, "Copied from apk assets folder to ${target.absolutePath}")
                        }
            } catch (e: Exception) {
                
                Log.e(ANG_PACKAGE, "asset copy failed", e)
            }
        }
    }

    private fun migrateLegacy() {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = AngConfigManager.migrateLegacyConfig(this@MainActivity)
            if (result != null) {
                launch(Dispatchers.Main) {
                    if (result) {
                        toast(getString(R.string.migration_success))
                        mainViewModel.reloadServerList()
                    } else {
                        toast(getString(R.string.migration_fail))
                    }
                }
            }
        }
    }

    fun startV2Ray() {
        if (mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER).isNullOrEmpty()) {
            return
        }
        showCircle()
//        toast(R.string.toast_services_start)
        V2RayServiceManager.startV2Ray(this)
        hideCircle()
    }

    fun restartV2Ray() {
        if (mainViewModel.isRunning.value == true) {
            Utils.stopVService(this)
        }
        Observable.timer(500, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    startV2Ray()
                }
    }

    public override fun onResume() {
        super.onResume()
        mainViewModel.reloadServerList()
    }

    public override fun onPause() {
        super.onPause()
    }

    private fun findCheckBoxInChildren(viewGroup: ViewGroup): CheckBox? {
        for (i in 0 until viewGroup.childCount) {
            val childView = viewGroup.getChildAt(i)
            if (childView is CheckBox) {
                return childView
            } else if (childView is ViewGroup) {
                val checkBox = findCheckBoxInChildren(childView)
                if (checkBox != null) {
                    return checkBox
                }
            }
        }
        return null
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        //val sharedPref: SharedPreferences = getSharedPreferences("GOLD", Context.MODE_PRIVATE)
        //val autoPingItem = menu.findItem(R.id.auto_ping_all)
        //if (autoPingItem != null && autoPingItem.isCheckable) {
        //    val checkBox = CheckBox(this)
        //    val latestStatus = sharedPref.getBoolean("AutoPing", false)
        //    autoPingItem.setChecked(latestStatus)
        //    autoPingItem.setActionView(checkBox)
        //    if(latestStatus)
        //        mainViewModel.testAllTcping()
        //}

        for (i in 0 until menu.size()) {
            val menuItem = menu.getItem(i)
            val spannable = SpannableString(menuItem.title)
            spannable.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, R.color.colorText)), 0, spannable.length, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
            menuItem.title = spannable
        }

        val yourSubMenu = menu.findItem(R.id.menu_add).subMenu
        if (yourSubMenu != null) {
            for (i in 0 until yourSubMenu.size()) {
                val item = yourSubMenu.getItem(i)
                val spannable = SpannableString(item.title)
                spannable.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, R.color.colorText)), 0, spannable.length, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
                item.title = spannable
            }
        }

        val shp: SharedPreferences = getSharedPreferences("GOLD", Context.MODE_PRIVATE)
        val lockMode = shp.getBoolean("SHARE_LOCK", false)
        lockSharingChTitle(lockMode)

        return true
    }

    private fun lockSharingChTitle(status: Boolean){
        val navigationView: NavigationView = findViewById(R.id.nav_view)
        if(status) navigationView.menu.findItem(R.id.share_lock)?.setTitle(resources.getString(R.string.unlock_sharing))
        else navigationView.menu.findItem(R.id.share_lock)?.setTitle(resources.getString(R.string.share_lock))
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.import_qrcode -> {
            importQRcode(true)
            true
        }
        R.id.import_clipboard -> {
            importClipboard()
            true
        }
        R.id.import_manually_vmess -> {
            importManually(EConfigType.VMESS.value)
            true
        }
        R.id.import_manually_vless -> {
            importManually(EConfigType.VLESS.value)
            true
        }
        R.id.import_manually_ss -> {
            importManually(EConfigType.SHADOWSOCKS.value)
            true
        }
        R.id.import_manually_socks -> {
            importManually(EConfigType.SOCKS.value)
            true
        }
        R.id.import_manually_trojan -> {
            importManually(EConfigType.TROJAN.value)
            true
        }
        R.id.import_config_custom_clipboard -> {
            importConfigCustomClipboard()
            true
        }
        R.id.import_config_custom_local -> {
            importConfigCustomLocal()
            true
        }
        R.id.import_config_custom_url -> {
            importConfigCustomUrlClipboard()
            true
        }
        R.id.import_config_custom_url_scan -> {
            importQRcode(false)
            true
        }

//        R.id.sub_setting -> {
//            startActivity<SubSettingActivity>()
//            true
//        }

        R.id.sub_update -> {
            importConfigViaSub()
            true
        }

        R.id.export_all -> {
            if (AngConfigManager.shareNonCustomConfigsToClipboard(this, mainViewModel.serverList) == 0) {
                toast(R.string.toast_success)
            } else {
                toast(R.string.toast_failure)
            }
            true
        }

        R.id.ping_all -> {
            if (mainViewModel.isRunning.value == true) {
                mainViewModel.testAllRealPing()
            }
            else{
                toast(R.string.connect_to_a_server_first)
            }
            true
        }

        //R.id.auto_ping_all -> {
        //    val sharedPref: SharedPreferences = getSharedPreferences("GOLD", Context.MODE_PRIVATE)
        //    item.isChecked = !item.isChecked
        //    if (item.isChecked) {
        //        mainViewModel.testAllRealPing()
        //    }
        //    with(sharedPref.edit()) {
        //        putBoolean("AutoPing", item.isChecked)
        //        apply()
        //    }
        //    true
        //}

        //R.id.real_ping_all -> {
        //    mainViewModel.testAllRealPing()
        //    true
        //}

        R.id.service_restart -> {
            restartV2Ray()
            true
        }

        R.id.del_all_config -> {
            AlertDialog.Builder(this, R.style.DialogStyle).setMessage(R.string.del_config_comfirm)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        MmkvManager.removeAllServer()
                        mainViewModel.reloadServerList()
                    }
                    .show()
            true
        }
        R.id.del_duplicate_config-> {
            AlertDialog.Builder(this, R.style.DialogStyle).setMessage(R.string.del_config_comfirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    mainViewModel.removeDuplicateServer()
                }
                .show()
            true
        }
        R.id.del_invalid_config -> {
            AlertDialog.Builder(this, R.style.DialogStyle).setMessage(R.string.del_config_comfirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    MmkvManager.removeInvalidServer()
                    mainViewModel.reloadServerList()
                }
                .show()
            true
        }
        R.id.sort_by_test_results -> {
            if (mainViewModel.isRunning.value == true) {
                MmkvManager.sortByTestResults()
                mainViewModel.reloadServerList()
            }
            else{
                toast(R.string.connect_to_a_server_first_and_test)
            }
            true
        }
        R.id.filter_config -> {
            mainViewModel.filterConfig(this)
            true
        }
        R.id.free_servers_update-> {
            fsuDownloader()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private fun importManually(createConfigType : Int) {
        startActivity(
            Intent()
                .putExtra("createConfigType", createConfigType)
                .putExtra("subscriptionId", mainViewModel.subscriptionId)
                .setClass(this, ServerActivity::class.java)
        )
    }

    /**
     * import config from qrcode
     */
    fun importQRcode(forConfig: Boolean): Boolean {
//        try {
//            startActivityForResult(Intent("com.google.zxing.client.android.SCAN")
//                    .addCategory(Intent.CATEGORY_DEFAULT)
//                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP), requestCode)
//        } catch (e: Exception) {
        RxPermissions(this)
                .request(Manifest.permission.CAMERA)
                .subscribe {
                    if (it)
                        if (forConfig)
                            scanQRCodeForConfig.launch(Intent(this, ScannerActivity::class.java))
                        else
                            scanQRCodeForUrlToCustomConfig.launch(Intent(this, ScannerActivity::class.java))
                    else
                        toast(R.string.toast_permission_denied)
                }
//        }
        return true
    }

    private val scanQRCodeForConfig = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            importBatchConfig(it.data?.getStringExtra("SCAN_RESULT"))
        }
    }

    private val scanQRCodeForUrlToCustomConfig = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            importConfigCustomUrl(it.data?.getStringExtra("SCAN_RESULT"))
        }
    }

    /**
     * import config from clipboard
     */
    fun importClipboard()
            : Boolean {
        try {
            val clipboard = Utils.getClipboard(this)
            importBatchConfig(clipboard)
        } catch (e: Exception) {
            
            e.printStackTrace()
            return false
        }
        return true
    }

    fun importBatchConfig(server: String?, subid: String = "") {
        val subid2 = if(subid.isNullOrEmpty()){
            mainViewModel.subscriptionId
        }else{
            subid
        }
        val append = subid.isNullOrEmpty()

        var count = AngConfigManager.importBatchConfig(server, subid2, append)
        if (count <= 0) {
            count = AngConfigManager.importBatchConfig(Utils.decode(server!!), subid2, append)
        }
        if (count > 0) {
            toast(R.string.toast_success)
            mainViewModel.reloadServerList()
        } else {
            toast(R.string.toast_failure)
        }
    }

    fun importConfigCustomClipboard()
            : Boolean {
        try {
            val configText = Utils.getClipboard(this)
            if (TextUtils.isEmpty(configText)) {
                toast(R.string.toast_none_data_clipboard)
                return false
            }
            importCustomizeConfig(configText)
            return true
        } catch (e: Exception) {
            
            e.printStackTrace()
            return false
        }
    }

    /**
     * import config from local config file
     */
    fun importConfigCustomLocal(): Boolean {
        try {
            showFileChooser()
        } catch (e: Exception) {
            
            e.printStackTrace()
            return false
        }
        return true
    }

    fun importConfigCustomUrlClipboard()
            : Boolean {
        try {
            val url = Utils.getClipboard(this)
            if (TextUtils.isEmpty(url)) {
                toast(R.string.toast_none_data_clipboard)
                return false
            }
            return importConfigCustomUrl(url)
        } catch (e: Exception) {
            
            e.printStackTrace()
            return false
        }
    }

    /**
     * import config from url
     */
    fun importConfigCustomUrl(url: String?): Boolean {
        try {
            if (!Utils.isValidUrl(url)) {
                toast(R.string.toast_invalid_url)
                return false
            }
            lifecycleScope.launch(Dispatchers.IO) {
                val configText = try {
                    Utils.getUrlContentWithCustomUserAgent(url)
                } catch (e: Exception) {
                    
                    e.printStackTrace()
                    ""
                }
                launch(Dispatchers.Main) {
                    importCustomizeConfig(configText)
                }
            }
        } catch (e: Exception) {
            
            e.printStackTrace()
            return false
        }
        return true
    }

    /**
     * import config from sub
     */
    fun importConfigViaSub()
            : Boolean {
        try {
            toast(R.string.title_sub_update)
            MmkvManager.decodeSubscriptions().forEach {
                if (TextUtils.isEmpty(it.first)
                        || TextUtils.isEmpty(it.second.remarks)
                        || TextUtils.isEmpty(it.second.url)
                ) {
                    return@forEach
                }
                if (!it.second.enabled) {
                    return@forEach
                }
                val url = Utils.idnToASCII(it.second.url)
                if (!Utils.isValidUrl(url)) {
                    return@forEach
                }
                Log.d(ANG_PACKAGE, url)
                lifecycleScope.launch(Dispatchers.IO) {
                    val configText = try {
                        Utils.getUrlContentWithCustomUserAgent(url)
                    } catch (e: Exception) {
                        
                        e.printStackTrace()
                        launch(Dispatchers.Main) {
                            toast("\"" + it.second.remarks + "\" " + getString(R.string.toast_failure))
                        }
                        return@launch
                    }
                    launch(Dispatchers.Main) {
                        importBatchConfig(configText, it.first)
                    }
                }
            }
        } catch (e: Exception) {
            
            e.printStackTrace()
            return false
        }
        return true
    }

    /**
     * show file chooser
     */
    private fun showFileChooser() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)

        try {
            chooseFileForCustomConfig.launch(Intent.createChooser(intent, getString(R.string.title_file_chooser)))
        } catch (ex: ActivityNotFoundException) {
            toast(R.string.toast_require_file_manager)
        }
    }

    private val chooseFileForCustomConfig = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val uri = it.data?.data
        if (it.resultCode == RESULT_OK && uri != null) {
            readContentFromUri(uri)
        }
    }

    /**
     * read content from uri
     */
    private fun readContentFromUri(uri: Uri) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        RxPermissions(this)
                .request(permission)
                .subscribe {
                    if (it) {
                        try {
                            contentResolver.openInputStream(uri).use { input ->
                                importCustomizeConfig(input?.bufferedReader()?.readText())
                            }
                        } catch (e: Exception) {
                            
                            e.printStackTrace()
                        }
                    } else
                        toast(R.string.toast_permission_denied)
                }
    }

    /**
     * import customize config
     */
    fun importCustomizeConfig(server: String?) {
        try {
            if (server == null || TextUtils.isEmpty(server)) {
                toast(R.string.toast_none_data)
                return
            }
            mainViewModel.appendCustomConfigServer(server)
            mainViewModel.reloadServerList()
            toast(R.string.toast_success)
            //adapter.notifyItemInserted(mainViewModel.serverList.lastIndex)
        } catch (e: Exception) {
            
            ToastCompat.makeText(this, "${getString(R.string.toast_malformed_josn)} ${e.cause?.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
            return
        }
    }

    fun setTestState(content: String?) {
        binding.tvTestState.text = content
    }

//    val mConnection = object : ServiceConnection {
//        override fun onServiceDisconnected(name: ComponentName?) {
//        }
//
//        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
//            sendMsg(AppConfig.MSG_REGISTER_CLIENT, "")
//        }
//    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            moveTaskToBack(false)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }


    fun showCircle() {
        binding.fabProgressCircle.show()
    }

    fun hideCircle() {
        try {
            Observable.timer(300, TimeUnit.MILLISECONDS)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe {
                        try {
                            if (binding.fabProgressCircle.isShown) {
                                binding.fabProgressCircle.hide()
                            }
                        } catch (e: Exception) {
                            Log.w(ANG_PACKAGE, e)
                        }
                    }
        } catch (e: Exception) {
            
            Log.d(ANG_PACKAGE, e.toString())
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val lock_area:ConstraintLayout = findViewById(R.id.lock_area)
        val unlock_area:ConstraintLayout = findViewById(R.id.unlock_area)
        if(lock_area.visibility == View.VISIBLE || unlock_area.visibility == View.VISIBLE){
            shareLockHide()
            shareUnlockHide()
        }
        else if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            //super.onBackPressed()
            onBackPressedDispatcher.onBackPressed()
        }
        //super.onBackPressed()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        when (item.itemId) {
            //R.id.server_profile -> activityClass = MainActivity::class.java
            R.id.sub_setting -> {
                startActivity(Intent(this, SubSettingActivity::class.java))
            }
            R.id.settings -> {
                startActivity(Intent(this, SettingsActivity::class.java)
                        .putExtra("isRunning", mainViewModel.isRunning.value == true))
            }
            R.id.user_asset_setting -> {
                startActivity(Intent(this, UserAssetActivity::class.java))
            }
            R.id.feedback -> {
                //Utils.openUri(this, AppConfig.goldV2rayIssues)
                val selectorIntent = Intent(Intent.ACTION_SENDTO)
                selectorIntent.setData(Uri.parse("mailto:"))
                val emailIntent = Intent(Intent.ACTION_SEND)
                emailIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf(AppConfig.FEEDBACK_MAIL))
                emailIntent.putExtra(
                    Intent.EXTRA_SUBJECT,
                    getString(R.string.title_pref_feedback) + ": " + getString(R.string.app_name)
                )
                emailIntent.selector = selectorIntent
                startActivity(Intent.createChooser(emailIntent, getString(R.string.title_pref_feedback)))
            }
            /*R.id.promotion -> {
                Utils.openUri(this, "${Utils.decode(AppConfig.promotionUrl)}?t=${System.currentTimeMillis()}")
            }*/
            R.id.logcat -> {
                startActivity(Intent(this, LogcatActivity::class.java))
            }
            R.id.privacy_policy-> {
                Utils.openUri(this, AppConfig.GOLDV2RAY_PRIVACYPOLICY)
            }

            R.id.other_apps-> {
                try{
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppConfig.GOOGLEPLAY_URL)))
                }
                catch (e: Exception){
                    
                    Toast.makeText(this, R.string.error_occurred, Toast.LENGTH_SHORT).show()
                }
            }
            R.id.about-> {
                shareLockHide()
                shareUnlockHide()
                aboutArea.visibility = View.VISIBLE
            }
            R.id.share_app-> {
                val packageName = packageName
                val appAddress = "https://play.google.com/store/apps/details?id=$packageName"
                try {
                    val shareIntent = Intent(Intent.ACTION_SEND)
                    shareIntent.setType("text/plain")
                    shareIntent.putExtra(Intent.EXTRA_TEXT, appAddress)
                    startActivity(Intent.createChooser(shareIntent,getString(R.string.share_app) + ": "))
                } catch (e: java.lang.Exception) {
                    
                    Toast.makeText(this, getString(R.string.error_occurred), Toast.LENGTH_LONG).show()
                }
            }
            R.id.rate_app-> {
                rate_app()
            }
            R.id.share_lock-> {
                val shp: SharedPreferences = getSharedPreferences("GOLD", Context.MODE_PRIVATE)
                val lockMode = shp.getBoolean("SHARE_LOCK", false)
                if(lockMode){
                    item.setTitle(resources.getString(R.string.unlock_sharing))
                    shareUnlockShow()
                }
                else{
                    item.setTitle(resources.getString(R.string.share_lock))
                    shareLockShow()
                }
            }
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    fun rate_app() {
        val packageName = packageName
        try {
            val uri = Uri.parse("market://details?id=$packageName")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NO_HISTORY or
                        Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK
            )
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            val url = "https://play.google.com/store/apps/details?id=$packageName"
            val webIntent = Intent(Intent.ACTION_VIEW)
            webIntent.setData(Uri.parse(url))
            startActivity(webIntent)
        }
    }

    // Free servers update
    private var fsuInProgress = false
    private fun fsuDownloader() {
        if(AppConfig.STORE_MODE){
            toast(getString(R.string.this_feature_is_currently_unavailable))
            return
        }
        if (!fsuInProgress) {
            fsuInProgress = true
            val fsu_progress: LinearProgressIndicator = findViewById(R.id.fsu_progress)
            fsu_progress.visibility = View.VISIBLE
            lifecycleScope.launch {
                val result: String? = withContext(Dispatchers.IO) {
                    try {
                        val deferredServerA = async { fsuFile(AppConfig.FREE_SERVERS_A) }
                        val deferredServerB = async { fsuFile(AppConfig.FREE_SERVERS_B) }
                        val deferredServerC = async { fsuFile(AppConfig.FREE_SERVERS_C) }
                        val contents = awaitAll(deferredServerA, deferredServerB, deferredServerC)
                        contents.firstOrNull { it != null }
                    } catch (e: Exception) {
                        null
                    }
                }
                result?.let {
                    // success
                    val pw_area: ConstraintLayout = findViewById(R.id.please_wait_area)
                    pw_area.visibility = View.VISIBLE

                    toast(getString(R.string.free_servers_updated_successfully))
                    Handler().postDelayed({
                        runOnUiThread {
                            try {
                                importBatchConfig(result)

                                val fsu_tips: View = findViewById(R.id.fsu_tips)
                                fsu_progress.visibility = View.GONE
                                fsuInProgress = false
                                fsu_tips.visibility = View.VISIBLE
                                val pw_area: ConstraintLayout = findViewById(R.id.please_wait_area)
                                pw_area.visibility = View.GONE

                                Handler().postDelayed({
                                    runOnUiThread {
                                        try {
                                            val fsu_tips: View = findViewById(R.id.fsu_tips)
                                            val fadeOutAnimation: Animation = AlphaAnimation(1.0f, 0.0f)
                                            fadeOutAnimation.duration = 1000
                                            fadeOutAnimation.setAnimationListener(object : Animation.AnimationListener {
                                                override fun onAnimationStart(animation: Animation) {}
                                                override fun onAnimationEnd(animation: Animation) {
                                                    fsu_tips.visibility = View.GONE
                                                }
                                                override fun onAnimationRepeat(animation: Animation) {}
                                            })
                                            fsu_tips.startAnimation(fadeOutAnimation)
                                        } catch (e:Exception) {dialogAlert(getString(R.string.update_failed), getString(R.string.update_failed_tip))}
                                    }
                                }, 6 * 1000)
                            } catch (e:Exception) {dialogAlert(getString(R.string.update_failed), getString(R.string.update_failed_tip))}
                        }
                    }, 2 * 1000)
                } ?: run {
                    // failed
                    fsu_progress.visibility = View.GONE
                    val pw_area: ConstraintLayout = findViewById(R.id.please_wait_area)
                    pw_area.visibility = View.GONE
                    fsuInProgress = false
                    dialogAlert(getString(R.string.update_failed), getString(R.string.update_failed_tip))
                }
            }
        }
    }
    private suspend fun fsuFile(url: String): String? {
        return try {
            val content = URL(url).readText()
            content
        } catch (e: IOException) {
            null
        }
    }

    // Scroll buttons
    var scrollCounter = 0
    var scrollLatestStatus = "goDown"
    var countToDetectScroll = 25
    fun scrollListener(scrollY: Int) {
        val scrollCurrentStatus: String
        scrollCurrentStatus = if (scrollY > 0) "goDown" else "goUp"
        if (scrollLatestStatus != scrollCurrentStatus) {
            scrollCounter = 0
            scrollLatestStatus = scrollCurrentStatus
        }
        scrollCounter++
        if (scrollCounter >= countToDetectScroll) if (scrollCurrentStatus == "goDown") goDownState(
            true
        ) else goUpState(true)
    }
    fun goUpState(visibility: Boolean) {
        val listUp:ImageButton = findViewById(R.id.list_go_up)
        val listDown:ImageButton = findViewById(R.id.list_go_down)
        scrollCounter = 0
        if (visibility) {
            if (listUp.getVisibility() !== View.VISIBLE) {
                if (listDown.getVisibility() === View.VISIBLE)
                    goDownState(false)
                val fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in)
                listUp.startAnimation(fadeInAnimation)
                listUp.setVisibility(View.VISIBLE)
            }
        } else if (listUp.getVisibility() === View.VISIBLE) {
            val fadeOutAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_out)
            listUp.startAnimation(fadeOutAnimation)
            listUp.setVisibility(View.GONE)
        }
    }
    fun goDownState(visibility: Boolean) {
        val listUp:ImageButton = findViewById(R.id.list_go_up)
        val listDown:ImageButton = findViewById(R.id.list_go_down)
        scrollCounter = 0
        if (visibility) {
            if (listDown.getVisibility() !== View.VISIBLE) {
                if (listUp.getVisibility() === View.VISIBLE)
                    goUpState(false)
                val fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in)
                listDown.startAnimation(fadeInAnimation)
                listDown.setVisibility(View.VISIBLE)
            }
        } else if (listDown.getVisibility() === View.VISIBLE) {
            val fadeOutAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_out)
            listDown.startAnimation(fadeOutAnimation)
            listDown.setVisibility(View.GONE)
        }
    }
    fun listGoUp() {
        binding.recyclerView.scrollToPosition(0)
        goUpState(false)
        goDownState(false)
    }
    fun listGoDown() {
        binding.recyclerView.scrollToPosition(
            Objects.requireNonNull(binding.recyclerView.getAdapter()).getItemCount() - 1
        )
        goUpState(false)
        goDownState(false)
    }

    private fun lockMode(storeMode: Boolean){
        val shp: SharedPreferences = getSharedPreferences("GOLD", Context.MODE_PRIVATE)
        val lock_code: Pinview = findViewById(R.id.lock_code)
        val lock_code_repeat: Pinview = findViewById(R.id.lock_code_repeat)
        if(lock_code.value.length < AppConfig.PIN_CODE_LENGHT || lock_code_repeat.value.length < AppConfig.PIN_CODE_LENGHT){
            dialogAlert(getString(R.string.pin_code_error), getString(R.string.enter_the_pin_codes_completely))
            lock_code.clearValue()
            lock_code_repeat.clearValue()
        }
        else if(lock_code.value != lock_code_repeat.value){
            dialogAlert(getString(R.string.pin_code_error), getString(R.string.pin_codes_do_not_match))
            lock_code.clearValue()
            lock_code_repeat.clearValue()
        }
        else if(!storeMode){
            shp.edit().putBoolean("SHARE_LOCK", true).apply()
            shp.edit().putString("PIN_CODE", encryptPin(lock_code.value, AppConfig.ENCRYPTION_SECRET_KEY)).apply()
            AppConfig.LOCK_PROFILES = true

            adapter.notifyDataSetChanged()

            lockSharingChTitle(true)
            dialogAlert(getString(R.string.congratulations), getString(R.string.lock_sharing_active_tip))
            shareLockHide()
        }
        else if(storeMode){
            shp.edit().putBoolean("SHARE_LOCK", true).apply()
            shp.edit().putBoolean("STORE_MODE", true).apply()
            shp.edit().putString("PIN_CODE", encryptPin(lock_code.value, AppConfig.ENCRYPTION_SECRET_KEY)).apply()
            AppConfig.LOCK_PROFILES = true
            AppConfig.STORE_MODE = true

            adapter.notifyDataSetChanged()

            lockSharingChTitle(true)
            dialogAlert(getString(R.string.congratulations), getString(R.string.store_mode_active_tip))
            shareLockHide()
        }
    }
}