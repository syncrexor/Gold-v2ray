package me.syncrex.goldv2ray.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentSender
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.Visibility
import com.applovin.sdk.AppLovinPrivacySettings
import com.appnext.ads.interstitial.Interstitial
import com.appnext.banners.BannerAdRequest
import com.appnext.banners.BannerListener
import com.appnext.banners.BannerView
import com.appnext.base.Appnext
import com.appnext.core.AppnextAdCreativeType
import com.appnext.core.AppnextError
import com.appnext.core.callbacks.OnAdLoaded
import com.goodiebag.pinview.Pinview
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.initialization.InitializationStatus
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.tasks.Task
import com.google.android.material.navigation.NavigationView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.tabs.TabLayout
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.ump.ConsentForm
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.mbridge.msdk.MBridgeConstans
import com.mbridge.msdk.out.MBridgeSDKFactory
import com.unity3d.ads.metadata.MetaData
import com.unity3d.mediation.LevelPlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.syncrex.goldv2ray.AppConfig
import me.syncrex.goldv2ray.AppConfig.ANG_PACKAGE
import me.syncrex.goldv2ray.AppConfig.VPN
import me.syncrex.goldv2ray.BuildConfig
import me.syncrex.goldv2ray.R
import me.syncrex.goldv2ray.databinding.ActivityMainBinding
import me.syncrex.goldv2ray.databinding.LayoutProgressBinding
import me.syncrex.goldv2ray.dto.AssetUrlItem
import me.syncrex.goldv2ray.dto.EConfigType
import me.syncrex.goldv2ray.dto.ProfileItem
import me.syncrex.goldv2ray.dto.ServersCache
import me.syncrex.goldv2ray.dto.SubscriptionItem
import me.syncrex.goldv2ray.extension.toast
import me.syncrex.goldv2ray.extension.toastError
import me.syncrex.goldv2ray.handler.AngConfigManager
import me.syncrex.goldv2ray.handler.MigrateManager
import me.syncrex.goldv2ray.handler.MmkvManager
import me.syncrex.goldv2ray.handler.V2RayServiceManager
import me.syncrex.goldv2ray.util.Utils
import me.syncrex.goldv2ray.viewmodel.MainViewModel
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Objects
import java.util.Timer
import java.util.concurrent.TimeUnit
import kotlin.concurrent.timerTask

class MainActivity : BaseActivity(), NavigationView.OnNavigationItemSelectedListener {
    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }
    private lateinit var consentInformation: ConsentInformation
    private lateinit var consentForm: ConsentForm
    private val adapter by lazy { MainRecyclerAdapter(this) }
    private val requestVpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            startV2Ray()
        }
    }

    //GOLDV2RAY
    private lateinit var testProgressBar: LinearProgressIndicator
    private var selectedSubscriptionIdAtTestStart: String? = null
    private var testingServerList: List<ServersCache>? = null
    private var newStart = true
    private var adsTimerCounter = 0
    private var autoSmartConnectionReq = false
    private var smartConnectionTargetSubscriptionId: String? = null

    private val tabGroupListener = object : TabLayout.OnTabSelectedListener {
        override fun onTabSelected(tab: TabLayout.Tab?) {
            val tag = tab?.tag as? String ?: return

            if (tag != selectedSubscriptionIdAtTestStart) {
                testArea?.visibility = View.GONE
            } else {
                testArea?.visibility = View.GONE
                if (!testFinishedAlert) {
                    testArea?.visibility = View.VISIBLE
                }
            }
            if (tag == "manual_profiles") {
                mainViewModel.subscriptionIdChanged("__manual_profiles__")
            } else {
                mainViewModel.subscriptionIdChanged(tag)
            }
        }

        override fun onTabUnselected(tab: TabLayout.Tab?) {}
        override fun onTabReselected(tab: TabLayout.Tab?) {}
    }
    //GOLDV2RAY END

    private var mItemTouchHelper: ItemTouchHelper? = null
    val mainViewModel: MainViewModel by viewModels()

    // register activity result for requesting permission
    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                when (pendingAction) {
                    Action.IMPORT_QR_CODE_CONFIG ->
                        scanQRCodeForConfig.launch(Intent(this, ScannerActivity::class.java))
                    Action.READ_CONTENT_FROM_URI ->
                        chooseFileForCustomConfig.launch(Intent.createChooser(Intent(Intent.ACTION_GET_CONTENT).apply {
                            type = "*/*"
                            addCategory(Intent.CATEGORY_OPENABLE)
                        }, getString(R.string.title_file_chooser)))

                    Action.POST_NOTIFICATIONS -> {}
                    else -> {}
                }
            } else {
                toast(R.string.toast_permission_denied)
            }
            pendingAction = Action.NONE
        }

    private var pendingAction: Action = Action.NONE

    enum class Action {
        NONE,
        IMPORT_QR_CODE_CONFIG,
        READ_CONTENT_FROM_URI,
        POST_NOTIFICATIONS
    }

    private val chooseFileForCustomConfig = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val uri = it.data?.data
        if (it.resultCode == RESULT_OK && uri != null) {
            readContentFromUri(uri)
        }
    }

    private val scanQRCodeForConfig = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            importBatchConfig(it.data?.getStringExtra("SCAN_RESULT"))
        }
    }

    private lateinit var testCounter: TextView
    private lateinit var testOk: TextView
    private lateinit var testFailure: TextView
    private var testResultTimer: Timer? = null
    private var testArea: ConstraintLayout? = null
    private var test_gif: ProgressBar? = null
    //private var adsBannerClose: ImageView? = null
    //private var adsBannerIsClosed: Boolean = false

    @SuppressLint("NotifyDataSetChanged", "RestrictedApi", "SetTextI18n", "WrongConstant")
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        //GOLDV2RAY
        setSupportActionBar(binding.toolbar)
        supportActionBar?.let { actionBar ->
            val title = SpannableString(getString(R.string.servers))
            title.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, R.color.colorText)), 0, title.length, 0)
            title.setSpan(StyleSpan(Typeface.BOLD), 0, title.length, 0)
            actionBar.title = title
        }

        val shp: SharedPreferences = getSharedPreferences("GOLD", Context.MODE_PRIVATE)
        AppConfig.LOCK_PROFILES = shp.getBoolean("SHARE_LOCK", false)
        AppConfig.STORE_MODE = shp.getBoolean("STORE_MODE", false)

        // Test Result
        testCounter = findViewById(R.id.test_counter)
        testOk = findViewById(R.id.test_ok)
        testFailure = findViewById(R.id.test_failure)
        testProgressBar = findViewById(R.id.test_progress_bar)

        // check admob availability
        val checkAdMobAvailability = Thread {
            try {
                val client = OkHttpClient.Builder()
                    .callTimeout(5, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder()
                    .url("https://googleads.g.doubleclick.net/pagead/id")
                    .head()
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    Log.d("ADebug", "AdMobCheck: AdMob reachable, enabling ADMOB")
                    AppConfig.ADS_SERVICE = "ADMOB"
                    shp.edit().putString("ADS_SERVICE", "ADMOB").apply()
                } else {
                    Log.d(
                        "ADebug",
                        "AdMobCheck: AdMob NOT reachable (code=${response.code}), enabling APPNEXT"
                    )
                    AppConfig.ADS_SERVICE = "APPNEXT"
                    shp.edit().putString("ADS_SERVICE", "APPNEXT").apply()
                }

            } catch (e: Exception) {
                Log.d("ADebug", "AdMobCheck: exception -> enabling APPNEXT : $e")
                AppConfig.ADS_SERVICE = "APPNEXT"
                shp.edit().putString("ADS_SERVICE", "APPNEXT").apply()
            }
        }
        checkAdMobAvailability.start()
        //GOLDV2RAY END

        binding.fab.setOnClickListener {
            onFabClick()
        }
        binding.layoutTest.setOnClickListener {
            if (mainViewModel.isRunning.value == true) {
                setTestState(getString(R.string.connection_test_testing))
                mainViewModel.testCurrentServerRealPing()
            } else {
                setTestState(getString(R.string.connection_test_fail))
            }
        }

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        //addCustomDividerToRecyclerView(binding.recyclerView, this, R.drawable.custom_divider) //GOLDV2RAY
        binding.recyclerView.adapter = adapter

        mItemTouchHelper?.attachToRecyclerView(binding.recyclerView)

        //GOLDV2RAY
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
        val listUp:ImageButton = findViewById(R.id.list_go_up)
        val listDown:ImageButton = findViewById(R.id.list_go_down)
        listUp.setOnClickListener { v -> listGoUp() }
        listDown.setOnClickListener { v -> listGoDown() }
        //GOLDV2RAY END

        val toggle = ActionBarDrawerToggle(
                this, binding.drawerLayout, binding.toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        binding.navView.setNavigationItemSelectedListener(this)

        initGroupTab()
        setupViewModel()
        mainViewModel.initAssets(assets) //GOLDV2RAY
        migrateLegacy()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                pendingAction = Action.POST_NOTIFICATIONS
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        //GOLDV2RAY
        // app rate req
        val appRateOnRun = 30
        if (!shp.getBoolean("appRateShown", false)) {
            var appRunsCounter: Int = shp.getInt("appRunsCounter", 0)
            appRunsCounter++
            shp.edit().putInt("appRunsCounter", appRunsCounter).apply()
            if (appRunsCounter == appRateOnRun) {
                val rateDialog = AlertDialog.Builder(this).create()
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

        // Ads
        findViewById<View>(R.id.main_ads_area).visibility = View.GONE
        adsTimerCounter = shp.getInt("Ads_Interstitial_Latest_Count", 0)
        adsTimerCounter += 1
        shp.edit().putInt("Ads_Interstitial_Latest_Count", adsTimerCounter).apply()
        if (adsTimerCounter == AppConfig.ADS_INTERSTITIAL_SHOW_ON_TIME - 1) {
            val ad_tips: View = findViewById(R.id.ad_tips)
            ad_tips.visibility = View.VISIBLE
            Handler().postDelayed({
                runOnUiThread {
                    try {
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
                    } catch (_: Throwable) {}
                }
            }, 6 * 1000)
        }
        //adsBannerClose = findViewById(R.id.main_ads_close)
        //adsBannerClose?.setOnClickListener {
        //    bannerHide()
        //    adsBannerIsClosed = true
        //}
        // Adivery
        //Adivery.configure(getApplication() , AppConfig.ADIVERY_ID_APP)
        // Tapsell
        //TapsellPlus.initialize(this, AppConfig.TAPSELL_ID_APP, object : TapsellPlusInitListener {
        //    override fun onInitializeSuccess(adNetworks: AdNetworks) {}
        //    override fun onInitializeFailed(adNetworks: AdNetworks, adNetworkError: AdNetworkError) {}
        //})

        // AppNext
        if (shp.getString("ADS_SERVICE", "Admob") == "APPNEXT") {
            Log.d("ADebug", "ADS_SERVICE = " + "APPNEXT")
            Appnext.init(applicationContext)
            bannerShow()
            //interstitialShow()
        } else {
            // Admob
            Log.d("ADebug", "ADS_SERVICE = " + "Admob")
            Handler(Looper.getMainLooper()).post {
                MobileAds.initialize(this, object : OnInitializationCompleteListener {
                    override fun onInitializationComplete(initializationStatus: InitializationStatus) {
                        // GDPR
                        val gdprMetaData = MetaData(this@MainActivity) // Unity GDPR
                        val ccpaMetaData = MetaData(this@MainActivity) // Unity CCPA
                        var mintegralGDPR_CCPA = MBridgeSDKFactory.getMBridgeSDK() // Mintegral
                        val params =
                            ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(false)
                                .build()
                        consentInformation =
                            UserMessagingPlatform.getConsentInformation(this@MainActivity)
                        consentInformation.requestConsentInfoUpdate(this@MainActivity, params, {
                            if (consentInformation.isConsentFormAvailable()) {
                                UserMessagingPlatform.loadConsentForm(
                                    this@MainActivity,
                                    { consentForm: ConsentForm ->
                                        this@MainActivity.consentForm = consentForm
                                        if (consentInformation.getConsentStatus() === ConsentInformation.ConsentStatus.REQUIRED
                                        ) {
                                            consentForm.show(this@MainActivity) { formError: FormError? ->
                                                if (consentInformation.getConsentStatus() === ConsentInformation.ConsentStatus.OBTAINED) {

                                                    try {
                                                        gdprMetaData.set("gdpr.consent", true)
                                                        gdprMetaData.commit()
                                                        ccpaMetaData.set("privacy.consent", true)
                                                        ccpaMetaData.commit()
                                                    } catch (_: Throwable) {
                                                    }

                                                    try {
                                                        LevelPlay.setMetaData(
                                                            "do_not_sell",
                                                            "false"
                                                        )
                                                    } catch (_: Throwable) {
                                                    }

                                                    try {
                                                        AppLovinPrivacySettings.setHasUserConsent(
                                                            true,
                                                            this@MainActivity
                                                        )
                                                        AppLovinPrivacySettings.setDoNotSell(
                                                            false,
                                                            this@MainActivity
                                                        )
                                                    } catch (_: Throwable) {
                                                    }

                                                    try {
                                                        mintegralGDPR_CCPA.setConsentStatus(
                                                            this@MainActivity,
                                                            MBridgeConstans.IS_SWITCH_ON
                                                        )
                                                        mintegralGDPR_CCPA.setDoNotTrackStatus(false)
                                                    } catch (_: Throwable) {
                                                    }

                                                    Handler(Looper.getMainLooper()).postDelayed({
                                                        loadNativeAds()
                                                        bannerShow()
                                                        interstitialShow()
                                                    }, 2000)
                                                } else {

                                                    try {
                                                        gdprMetaData.set("gdpr.consent", false)
                                                        gdprMetaData.commit()
                                                        ccpaMetaData.set("privacy.consent", false)
                                                        ccpaMetaData.commit()
                                                    } catch (_: Throwable) {
                                                    }

                                                    try {
                                                        LevelPlay.setMetaData("do_not_sell", "true")
                                                    } catch (_: Throwable) {
                                                    }

                                                    try {
                                                        AppLovinPrivacySettings.setHasUserConsent(
                                                            false,
                                                            this@MainActivity
                                                        )
                                                        AppLovinPrivacySettings.setDoNotSell(
                                                            true,
                                                            this@MainActivity
                                                        )
                                                    } catch (_: Throwable) {
                                                    }

                                                    try {
                                                        mintegralGDPR_CCPA.setConsentStatus(
                                                            this@MainActivity,
                                                            MBridgeConstans.IS_SWITCH_OFF
                                                        )
                                                        mintegralGDPR_CCPA.setDoNotTrackStatus(true)
                                                    } catch (_: Throwable) {
                                                    }
                                                }
                                            }
                                        } else {

                                            try {
                                                gdprMetaData.set("gdpr.consent", true)
                                                gdprMetaData.commit()
                                                ccpaMetaData.set("privacy.consent", true)
                                                ccpaMetaData.commit()
                                            } catch (_: Throwable) {
                                            }

                                            try {
                                                LevelPlay.setMetaData("do_not_sell", "false")
                                            } catch (_: Throwable) {
                                            }

                                            try {
                                                AppLovinPrivacySettings.setHasUserConsent(
                                                    true,
                                                    this@MainActivity
                                                )
                                                AppLovinPrivacySettings.setDoNotSell(
                                                    false,
                                                    this@MainActivity
                                                )
                                            } catch (_: Throwable) {
                                            }

                                            try {
                                                mintegralGDPR_CCPA.setConsentStatus(
                                                    this@MainActivity,
                                                    MBridgeConstans.IS_SWITCH_ON
                                                )
                                                mintegralGDPR_CCPA.setDoNotTrackStatus(false)
                                            } catch (_: Throwable) {
                                            }

                                            Handler(Looper.getMainLooper()).postDelayed({
                                                loadNativeAds()
                                                bannerShow()
                                                interstitialShow()
                                            }, 2000)
                                        }
                                    }) { formError: FormError? -> }
                            } else {
                                Handler(Looper.getMainLooper()).postDelayed({
                                    loadNativeAds()
                                    bannerShow()
                                    interstitialShow()
                                }, 2000)
                            }
                        }) { formError -> }
                    }
                })
            }
        }

        // Test Result
        val test_result_close:ImageView = findViewById(R.id.test_result_close)
        test_result_close.setOnClickListener { testResultHide() }
        val test_result_dim:View = findViewById(R.id.test_result_dim)
        test_result_dim.setOnClickListener { testResultHide() }
        val test_result_cancel:Button = findViewById(R.id.test_result_cancel)
        test_result_cancel.setOnClickListener { testResultHide() }
        val test_result_confirm:Button = findViewById(R.id.test_result_confirm)
        test_result_confirm.setOnClickListener {
            testResultHide()
            sortByResult()
        }

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
            if (unlock_code.value.length < AppConfig.PIN_CODE_LENGTH) {
                dialogAlert(
                    getString(R.string.pin_code_error),
                    getString(R.string.enter_the_pin_codes_completely)
                )
                unlock_code.clearValue()
            } else {
                //val pinCode = decryptPin(shp.getString("PIN_CODE", ""), AppConfig.ENCRYPTION_SECRET_KEY)
                val pinCode = shp.getString("PIN_CODE", "")
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
                    addGoldV2raySubServers()
                }
            }
        }

        // Tutorial
        val tutorial_confirm = findViewById<Button>(R.id.tutorial_confirm)
        tutorial_confirm.setOnClickListener { user_guides(false) }

        // First Run
        if(shp.getBoolean("FIRST_RUN", true)){
            shp.edit().putBoolean("FIRST_RUN", false).apply()

            //user_guides(true)

            MmkvManager.setScanImmediate(true)
            MmkvManager.setAllowInsecure(true)
            MmkvManager.setPreferIpv6(true)
            MmkvManager.setSpeedEnable(true)
            MmkvManager.setConfirmRemove(true)

            // add sub servers
            addGoldV2raySubServers()

            //fsuDownloader()
            downloadGeoFiles()
        }
        downloadGeoFilesCheck(20)

        // Test Result
        test_gif = findViewById(R.id.test_gif)
        testArea = findViewById(R.id.test_area)
        testArea?.visibility = View.GONE
        val testClose = findViewById<ImageView>(R.id.test_close)
        testClose.setOnClickListener{
            try {
                testArea?.visibility = View.GONE
                testResultTimer?.cancel()
            }
            catch (e:Exception){
                e.printStackTrace()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val lock_area:ConstraintLayout = findViewById(R.id.lock_area)
                val unlock_area:ConstraintLayout = findViewById(R.id.unlock_area)
                val tutorial_area:ConstraintLayout = findViewById(R.id.tutorial_area)

                if(lock_area.isVisible || unlock_area.isVisible){
                    shareLockHide()
                    shareUnlockHide()
                }

                else if (tutorial_area.isVisible) {
                    tutorial_area.visibility = View.GONE
                }

                else if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                }

                else {
                    moveTaskToBack(false)
                }
            }
        })

        // access to other apps request
        if(shp.getBoolean("ALLOW_OTHER_APPS", false) == false) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.perapp_dialog_title))
                .setMessage(getString(R.string.perapp_dialog_message))
                .setPositiveButton(getString(R.string.perapp_dialog_accept)) { dialog, _ ->
                    shp.edit().putBoolean("ALLOW_OTHER_APPS", true).apply()
                    dialog.dismiss()
                }
                .setNegativeButton(getString(R.string.perapp_dialog_denied)) { dialog, _ ->
                    finish()
                }
                .setCancelable(false)
                .show()
        }

        // auto hide keyboard
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)

        // check app update
        try {
            val appUpdateManager = AppUpdateManagerFactory.create(this)
            appUpdateManager.registerListener { state ->
                if (state.installStatus() == InstallStatus.DOWNLOADED)
                    appUpdateManager.completeUpdate()
            }
            val appUpdateInfoTask = appUpdateManager.appUpdateInfo
            appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
                try {
                    if(appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE))
                        appUpdateManager.startUpdateFlowForResult(appUpdateInfo , this , AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build() , 9999)
                } catch(e: IntentSender.SendIntentException) {}
            }.addOnFailureListener { e -> }
        } catch(e: Exception) {}
        //GOLDV2RAY END
    }

    //GOLDV2RAY
    private fun addGoldV2raySubServers(){
        if (!BuildConfig.IS_MOD) {
            val editSubId = "-1"
            val subItem = MmkvManager.decodeSubscription(editSubId) ?: SubscriptionItem()
            subItem.remarks = getString(R.string.free)
            subItem.url = "https://raw.githubusercontent.com/syncrexor/Gold-v2ray/refs/heads/master/free_servers.txt"
            subItem.enabled = true
            subItem.autoUpdate = true
            MmkvManager.encodeSubscription(editSubId, subItem)
        }
    }

    //GOLDV2RAY
    private fun delGoldV2raySubServers(){
        if (!BuildConfig.IS_MOD) {
            val editSubId = "-1"
            MmkvManager.removeSubscription(editSubId)
        }
    }

    //GOLDV2RAY
    private fun onFabClick(){
        if (mainViewModel.isRunning.value == true) {
            V2RayServiceManager.stopVService(this)
        //} else if ((MmkvManager.decodeSettingsString(AppConfig.PREF_MODE) ?: "VPN") == "VPN") {
        } else if ((MmkvManager.decodeSettingsString(AppConfig.PREF_MODE) ?: VPN) == VPN) {
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

    //GOLDV2RAY
    private fun normalizeTabTagToSubscriptionId(tabTag: String?): String {
        return when (tabTag) {
            null -> ""
            "manual_profiles" -> "__manual_profiles__"
            else -> tabTag
        }
    }

    //GOLDV2RAY
    private var testFinishedAlert = false
    private fun startTestResultTimer() {
        val selectedId = selectedSubscriptionIdAtTestStart
        val serversToTest = testingServerList ?: return

        testFinishedAlert = false
        testArea?.visibility = View.VISIBLE
        test_gif?.visibility = View.VISIBLE

        testResultTimer?.cancel()
        testResultTimer = Timer()
        testResultTimer?.schedule(timerTask {
            runOnUiThread {
                AppConfig.NUMBER_OF_OK_SERVERS = 0
                AppConfig.NUMBER_OF_FAILURE_SERVERS = 0
                AppConfig.NUMBER_OF_ALL_SERVERS = serversToTest.size
                AppConfig.NUMBER_OF_PASSED_SERVERS = 0

                for (server in serversToTest) {
                    val effectiveSelectedId = if (selectedId == "manual_profiles") null else selectedId
                    if (!effectiveSelectedId.isNullOrEmpty() && server.profile.subscriptionId != selectedId) continue

                    try {
                        val guid = server.guid
                        val aff = MmkvManager.decodeServerAffiliationInfo(guid)
                        val delay = aff?.testDelayMillis

                        if (delay != null) {
                            when {
                                delay < 0L -> AppConfig.NUMBER_OF_FAILURE_SERVERS++
                                delay > 0L -> AppConfig.NUMBER_OF_OK_SERVERS++
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                AppConfig.NUMBER_OF_PASSED_SERVERS = AppConfig.NUMBER_OF_OK_SERVERS + AppConfig.NUMBER_OF_FAILURE_SERVERS

                testCounter.text = "${AppConfig.NUMBER_OF_PASSED_SERVERS}/${AppConfig.NUMBER_OF_ALL_SERVERS}"
                val progressPercent = if (AppConfig.NUMBER_OF_ALL_SERVERS > 0) (AppConfig.NUMBER_OF_PASSED_SERVERS * 100 / AppConfig.NUMBER_OF_ALL_SERVERS)
                else 0
                testProgressBar.progress = progressPercent

                testOk.text = AppConfig.NUMBER_OF_OK_SERVERS.toString()
                testFailure.text = AppConfig.NUMBER_OF_FAILURE_SERVERS.toString()

                try {
                    if (
                        AppConfig.NUMBER_OF_ALL_SERVERS > 0 &&
                        AppConfig.NUMBER_OF_PASSED_SERVERS == AppConfig.NUMBER_OF_ALL_SERVERS &&
                        !testFinishedAlert
                    ) {
                        testFinishedAlert = true
                        test_gif?.visibility = View.INVISIBLE
                        testResultShow()
                        testingServerList = null
                        testResultTimer?.cancel()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }, 0, 250)
    }
    //GOLDV2RAY END

    //GOLDV2RAY
    private fun user_guides(status: Boolean) {
        val tutorial_area = findViewById<ConstraintLayout>(R.id.tutorial_area)

        if (status)
            tutorial_area.visibility = View.VISIBLE
        else
            tutorial_area.visibility = View.GONE
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
            //GOLDV2RAY
            if (isRunning) {
                if (!Utils.getDarkModeStatus(this)) {
                    binding.fab.setImageResource(R.drawable.ic_stat_name)
                }
                binding.fab.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.colorBase))
                setTestState(getString(R.string.connection_connected))
                binding.layoutTest.isFocusable = true
                //
                //bannerShow()
                if(newStart) newStart = false
                else interstitialShow()
                //
            } else {
                if (!Utils.getDarkModeStatus(this)) {
                    binding.fab.setImageResource(R.drawable.ic_stat_name)
                }
                binding.fab.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.colorButtonBackgroundLight))
                setTestState(getString(R.string.connection_not_connected))
                binding.layoutTest.isFocusable = false
            }
            hideCircle()
            //GOLDV2RAY END
        }
        mainViewModel.startListenBroadcast()
    }

    private fun migrateLegacy() {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = MigrateManager.migrateServerConfig2Profile()
            launch(Dispatchers.Main) {
                if (result) {
                    toast(getString(R.string.migration_success))
                    mainViewModel.reloadServerList()
                } else {
                    //toast(getString(R.string.migration_fail))
                }
            }
        }
    }

    //GOLDV2RAY
    private fun initGroupTab() {
        // get current tab index
        val currentTabIndex = binding.tabGroup.selectedTabPosition

        // reset tab position
        if (mainViewModel.subscriptionId == "-1" ||
            mainViewModel.subscriptionId == "__wifi__" ||
            mainViewModel.subscriptionId == "__sim1__" ||
            mainViewModel.subscriptionId == "__sim2__") {
            mainViewModel.subscriptionIdChanged("")
        }

        binding.tabGroup.removeOnTabSelectedListener(tabGroupListener)
        binding.tabGroup.removeAllTabs()
        binding.tabGroup.isVisible = false

        val (listId, listRemarks) = mainViewModel.getSubscriptions(this)
        if (listId == null || listRemarks == null) return

        val allTabTags = mutableListOf<String>()

        // Add All tab first
        val allIndex = listId.indexOf("")
        if (allIndex != -1 && allIndex < listRemarks.size) {
            val tab = binding.tabGroup.newTab()
            val title = try {
                listRemarks[allIndex] ?: "All"
            } catch (e: Exception) {
                Log.e("GoldV2ray", "Invalid tab title at index $allIndex", e)
                "All"
            }
            tab.text = title
            tab.tag = ""
            binding.tabGroup.addTab(tab)
            allTabTags.add("")
        }

        // Add User tab second
        val userTab = binding.tabGroup.newTab()
        userTab.text = getString(R.string.user)
        userTab.tag = "manual_profiles"
        binding.tabGroup.addTab(userTab)
        allTabTags.add("manual_profiles")

        // Add other subscription tabs (except All)
        for (i in listRemarks.indices) {
            if (i == allIndex) continue
            val tag = listId[i]
            val tab = binding.tabGroup.newTab()
            try {
                tab.text = listRemarks[i]
            } catch (e: Exception) {
                tab.text = "SUB"
            }
            tab.tag = tag
            binding.tabGroup.addTab(tab)
            allTabTags.add(tag)
        }

        // Add Wi-Fi / SIM tabs
        val groups = listOf(
            "__wifi__" to getString(R.string.wifi_tab),
            "__sim1__" to getString(R.string.sim1_tab),
            "__sim2__" to getString(R.string.sim2_tab)
        )
        for ((tag, title) in groups) {
            if (mainViewModel.hasServersInGroup(tag)) {
                val tab = binding.tabGroup.newTab()
                tab.text = title
                tab.tag = tag
                binding.tabGroup.addTab(tab)
                allTabTags.add(tag)
            }
        }

        // go to latest tab selected
        val finalTabIndex = if (currentTabIndex in 0 until binding.tabGroup.tabCount) {
            currentTabIndex
        } else {
            0
        }

        binding.tabGroup.addOnTabSelectedListener(tabGroupListener)
        binding.tabGroup.selectTab(binding.tabGroup.getTabAt(finalTabIndex))
        binding.tabGroup.isVisible = true
    }
    //GOLDV2RAY END

    fun startV2Ray() {
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            toast(R.string.title_file_chooser)
            return
        }

        //GOLDV2RAY
        try {
            V2RayServiceManager.startVService(this)
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to start VPN service", e)
            toastError(getString(R.string.error_occurred))
        }

        showCircle() //GOLDV2RAY
        hideCircle() //GOLDV2RAY
    }

    fun restartV2Ray() {
        if (mainViewModel.isRunning.value == true) {
            V2RayServiceManager.stopVService(this)
        }
        lifecycleScope.launch {
            delay(500)
            startV2Ray()
        }
    }

    public override fun onResume() {
        super.onResume()
        mainViewModel.reloadServerList()

        //GOLDV2RAY
        // hide keyboard
        try {
            val inputMethodManager = getSystemService(InputMethodManager::class.java)
            inputMethodManager?.let { imm ->
                currentFocus?.windowToken?.let { token ->
                    imm.hideSoftInputFromWindow(token, 0)
                }
            }
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        } catch (e: Exception) {
            //
        }

        // hide keyboard with delay
        lifecycleScope.launch {
            delay(1200)
            try{
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                currentFocus?.let {
                    imm.hideSoftInputFromWindow(it.windowToken, 0)
                    it.clearFocus()
                }
            } catch (e: Exception){
                //
            }
        }
        lifecycleScope.launch {
            delay(1700)
            try{
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                currentFocus?.let {
                    imm.hideSoftInputFromWindow(it.windowToken, 0)
                    it.clearFocus()
                }
            } catch (e: Exception){
                //
            }
        }
        //GOLDV2RAY END
    }

    public override fun onPause() {
        super.onPause()
    }

    //GOLDV2RAY
    override fun onDestroy() {
        super.onDestroy()
        testResultTimer?.cancel()
    }

    //GOLDV2RAY
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        if (BuildConfig.IS_MOD) {
            menu.findItem(R.id.free_servers_update)?.apply {
                isVisible = false
                isEnabled = false
            }
            menu.findItem(R.id.user_guides)?.apply {
                isVisible = false
                isEnabled = false
            }
        }

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
        return super.onCreateOptionsMenu(menu)
    }
    //GOLDV2RAY END

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

        R.id.import_local -> {
            importConfigLocal()
            true
        }

        R.id.import_manually_vmess -> {
            importManually(EConfigType.VMESS.value, "VMESS") //GOLDV2RAY
            true
        }

        R.id.import_manually_vless -> {
            importManually(EConfigType.VLESS.value, "VLESS") //GOLDV2RAY
            true
        }

        R.id.import_manually_ss -> {
            importManually(EConfigType.SHADOWSOCKS.value, "SHADOWSOCKS") //GOLDV2RAY
            true
        }

        R.id.import_manually_socks -> {
            importManually(EConfigType.SOCKS.value, "SOCKS") //GOLDV2RAY
            true
        }

        R.id.import_manually_http -> {
            importManually(EConfigType.HTTP.value, "HTTP") //GOLDV2RAY
            true
        }

        R.id.import_manually_trojan -> {
            importManually(EConfigType.TROJAN.value, "TROJAN") //GOLDV2RAY
            true
        }

        R.id.import_manually_wireguard -> {
            importManually(EConfigType.WIREGUARD.value, "WIREGUARD") //GOLDV2RAY
            true
        }

        R.id.import_manually_hysteria2 -> {
            importManually(EConfigType.HYSTERIA2.value, "HYSTERIA2") //GOLDV2RAY
            true
        }

        //GOLDV2RAY
        R.id.user_guides -> {
            user_guides(true)
            true
        }

        //GOLDV2RAY
        R.id.export_all -> {
            if(AppConfig.LOCK_PROFILES || AppConfig.STORE_MODE){
                toast(getString(R.string.this_feature_is_currently_unavailable))
            }
            else {
                mainViewModel.exportAllServer()
            }
            true
        }

        //GOLDV2RAY
        R.id.magic_test -> {
            getServersTest(true)
            true
        }

        R.id.intelligent_selection_all -> {
            getSmartConnection()
            true
        }

        //GOLDV2RAY
        R.id.testing_servers -> {
            getServersTest(false)
            true
        }

        R.id.smart_test -> {
            getServersTest(true)
            true
        }

        R.id.service_restart -> {
            restartV2Ray()
            true
        }

        //GOLDV2RAY
        R.id.del_all_config -> {
            if(AppConfig.LOCK_PROFILES || AppConfig.STORE_MODE){
                toast(getString(R.string.this_feature_is_currently_unavailable))
            }
            else {
                AlertDialog.Builder(this)
                    .setMessage(R.string.del_config_comfirm)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        mainViewModel.removeAllServer()
                        mainViewModel.reloadServerList()
                        listGoUp()
                    }
                    .setNegativeButton(android.R.string.cancel) {_, _ ->
                        //do noting
                    }
                    .show()
            }
            true
        }

        //GOLDV2RAY
        R.id.del_duplicate_config-> {
            if(AppConfig.LOCK_PROFILES || AppConfig.STORE_MODE){
                toast(getString(R.string.this_feature_is_currently_unavailable))
            }
            else {
                AlertDialog.Builder(this)
                    .setMessage(R.string.del_config_comfirm)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        mainViewModel.removeDuplicateServer()
                        mainViewModel.reloadServerList()
                        listGoUp()
                    }
                    .setNegativeButton(android.R.string.cancel) {_, _ ->
                        //do noting
                    }
                    .show()
            }
            true
        }

        //GOLDV2RAY
        R.id.del_invalid_config -> {
            if(AppConfig.LOCK_PROFILES || AppConfig.STORE_MODE){
                toast(getString(R.string.this_feature_is_currently_unavailable))
            }
            else {
                AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        mainViewModel.removeInvalidServer()
                        mainViewModel.reloadServerList()
                    }
                    .setNegativeButton(android.R.string.cancel) {_, _ ->
                        //do noting
                    }
                    .show()
            }
            true
        }

        R.id.sort_by_test_results -> {
            sortByResult()
            true
        }

        //GOLDV2RAY
        R.id.free_servers_update-> {
            fsuDownloader()
            true
        }

        R.id.sub_update -> {
            importConfigViaSub()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private fun importManually(createConfigType : Int, title : String) {
        startActivity(
            Intent()
                .putExtra("createConfigType", createConfigType)
                .putExtra("subscriptionId", mainViewModel.subscriptionId)
                .putExtra("pageTitle", title) //GOLDV2RAY
                .setClass(this, ServerActivity::class.java)
        )
    }

    //GOLDV2RAY
    private fun importQRcode(forConfig: Boolean): Boolean {
        val permission = Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            if (forConfig) {
                scanQRCodeForConfig.launch(Intent(this, ScannerActivity::class.java))
            } else {
            }
        } else {
            requestPermissionLauncher.launch(permission)
        }

        return true
    }

    private fun importClipboard()
            : Boolean {
        try {
            val clipboard = Utils.getClipboard(this)
            importBatchConfig(clipboard)
        } catch (e: Exception) {

            Log.e(AppConfig.TAG, "Failed to import config from clipboard", e)
            return false
        }
        return true
    }

    //GOLDV2RAY
    private fun importBatchConfig(server: String?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_progress, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .show()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val subId = when (mainViewModel.subscriptionId) {
                    "__manual_profiles__" -> ""
                    else -> mainViewModel.subscriptionId
                }
                val (count, countSub) = AngConfigManager.importBatchConfig(server, subId, true)
                delay(500L)
                withContext(Dispatchers.Main) {
                    when {
                        count > 0 -> {
                            toast(getString(R.string.title_import_config_count, count))
                            if (mainViewModel.subscriptionId == "__manual_profiles__") {
                                mainViewModel.showManualProfiles()
                            } else {
                                mainViewModel.reloadServerList()
                            }
                        }
                        countSub > 0 -> initGroupTab()
                        else -> toastError(R.string.toast_failure)
                    }
                    dialog.dismiss()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    toastError(R.string.toast_failure)
                    dialog.dismiss()
                }
                Log.e(AppConfig.TAG, "Failed to import batch config", e)
            }
        }
    }
    //GOLDV2RAY END

    private fun importConfigLocal(): Boolean {
        try {
            showFileChooser()
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to import config from local file", e)
            return false
        }
        return true
    }

    //GOLDV2RAY
    private fun importConfigViaSub() : Boolean {
        val dialog = AlertDialog.Builder(this)
            .setView(LayoutProgressBinding.inflate(layoutInflater).root)
            .setCancelable(false)
            .show()

        lifecycleScope.launch(Dispatchers.IO) {
            val count = mainViewModel.updateConfigViaSubAll()
            delay(500L)
            launch(Dispatchers.Main) {
                if (count > 0) {
                    toast(getString(R.string.title_update_config_count, count))
                    mainViewModel.reloadServerList()
                } else {
                    toast(R.string.sub_update_error)
                }
                dialog.dismiss()
            }
        }
        return true
    }

    private fun showFileChooser() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)

        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            pendingAction = Action.READ_CONTENT_FROM_URI
            chooseFileForCustomConfig.launch(Intent.createChooser(intent, getString(R.string.title_file_chooser)))
        } else {
            requestPermissionLauncher.launch(permission)
        }
    }

    private fun readContentFromUri(uri: Uri) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
                try {
                    contentResolver.openInputStream(uri).use { input ->
                        importBatchConfig(input?.bufferedReader()?.readText())
                    }
                } catch (e: Exception) {
                    Log.e(AppConfig.TAG, "Failed to read content from URI", e)
                }
            } else {
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    private fun setTestState(content: String?) {
        binding.tvTestState.text = content
    }

    //GOLDV2RAY
    fun showCircle() {
        binding.fabProgressCircle.show()
    }

    //GOLDV2RAY
    fun hideCircle() {
        try {
            CoroutineScope(Dispatchers.Main).launch {
                delay(300)
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

    //GOLDV2RAY
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.sub_setting -> {
                startActivity(Intent(this, SubSettingActivity::class.java))
            }

            R.id.routing_setting -> {
                startActivity(Intent(this, RoutingSettingActivity::class.java))
            }

            R.id.settings -> {
                startActivity(Intent(this, SettingsActivity::class.java)
                        .putExtra("isRunning", mainViewModel.isRunning.value == true))
            }

            R.id.user_asset_setting -> {
                startActivity(Intent(this, UserAssetActivity::class.java))
            }

            R.id.per_app_proxy_settings -> {
                startActivity(Intent(this, PerAppProxyActivity::class.java))
            }

            R.id.logcat -> {
                startActivity(Intent(this, LogcatActivity::class.java))
            }

            R.id.about-> {
                shareLockHide()
                shareUnlockHide()
                //aboutArea.visibility = View.VISIBLE
                startActivity(Intent(this, About::class.java))
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
    //GOLDV2RAY END

    //GOLDV2RAY
    private fun sortByResult() {
        if (mainViewModel.isRunning.value == true) {
            mainViewModel.sortByTestResults()
            mainViewModel.reloadServerList()
        }
        else{
            toast(R.string.connect_to_a_server_first_and_test)
        }
    }

    //GOLDV2RAY
    fun autoTest() {
        CoroutineScope(Dispatchers.Main).launch {
            delay(2000)
            try {
                setTestState(getString(R.string.connection_test_testing))
                mainViewModel.testCurrentServerRealPing()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    //GOLDV2RAY
    private fun dialogAlert(title: String, message: String){
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton(getString(R.string.got_it)) { dialog, which ->
            dialog.dismiss()
        }
        val dialog = builder.create()
        dialog.show()
    }

    //GOLDV2RAY
    @SuppressLint("SetTextI18n")
    private fun testResultShow(){
        val test_result_area:ConstraintLayout = findViewById(R.id.test_result_area)
        test_result_area.visibility = View.VISIBLE

        val test_result_value_active:TextView = findViewById(R.id.test_result_value_active)
        test_result_value_active.setText(AppConfig.NUMBER_OF_OK_SERVERS.toString())

        val test_result_value_failure:TextView = findViewById(R.id.test_result_value_failure)
        test_result_value_failure.setText(AppConfig.NUMBER_OF_FAILURE_SERVERS.toString())

        val test_result_value_servers:TextView = findViewById(R.id.test_result_value_servers)
        test_result_value_servers.setText(AppConfig.NUMBER_OF_PASSED_SERVERS.toString() + "/" + AppConfig.NUMBER_OF_ALL_SERVERS.toString())
    }

    //GOLDV2RAY
    private fun testResultHide() {
        val test_result_area: ConstraintLayout = findViewById(R.id.test_result_area)
        test_result_area.visibility = View.GONE

        if (autoSmartConnectionReq) {
            autoSmartConnectionReq = false

            sortByResult()

            val targetSubId = smartConnectionTargetSubscriptionId
            smartConnectionTargetSubscriptionId = null
            getSmartConnection(targetSubId)

            binding.recyclerView.post {
                try {
                    switchToTabByTag(selectedSubscriptionIdAtTestStart)
                    binding.recyclerView.scrollToPosition(0)
                    onFabClick()
                    lifecycleScope.launch {
                        delay(300)
                        onFabClick()
                    }
                } catch (e: Exception) {
                    toast(R.string.connect_to_a_server_first)
                }
            }
        }
    }

    //GOLDV2RAY
    private fun switchToTabByTag(tag: String?) {
        if (tag == null) return

        for (i in 0 until binding.tabGroup.tabCount) {
            val tab = binding.tabGroup.getTabAt(i)
            if (tab?.tag == tag) {
                tab.select()
                break
            }
        }
    }

    //GOLDV2RAY
    fun getSmartConnection(targetSubscriptionId: String? = null) {
        if (MmkvManager.decodeSettingsString(AppConfig.PREF_OUTBOUND_DOMAIN_RESOLVE_METHOD, "1") != "0") {
            toast(getString(R.string.pre_resolving_domain))
        }

        val subId = targetSubscriptionId ?: mainViewModel.subscriptionId
        mainViewModel.createIntelligentSelectionAll(subId)
    }

    //GOLDV2RAY
    private fun shareLockShow(){
        val lock_code: Pinview = findViewById(R.id.lock_code)
        lock_code.clearValue()

        val lock_code_repeat: Pinview = findViewById(R.id.lock_code_repeat)
        lock_code_repeat.clearValue()

        val lock_area:ConstraintLayout = findViewById(R.id.lock_area)
        lock_area.visibility = View.VISIBLE
    }

    //GOLDV2RAY
    private fun shareLockHide(){
        val lock_area:ConstraintLayout = findViewById(R.id.lock_area)
        lock_area.visibility = View.GONE
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }

    //GOLDV2RAY
    private fun shareUnlockShow(){
        val unlock_code: Pinview = findViewById(R.id.unlock_code)
        unlock_code.clearValue()

        val unlock_area:ConstraintLayout = findViewById(R.id.unlock_area)
        unlock_area.visibility = View.VISIBLE
    }

    //GOLDV2RAY
    private fun shareUnlockHide(){
        val unlock_area:ConstraintLayout = findViewById(R.id.unlock_area)
        unlock_area.visibility = View.GONE
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }

    //GOLDV2RAY
    private fun adsAllowToShow(gotAd: String?): Boolean {
        return true
        /*
        if (AppConfig.ADS_SERVICE == "ADIVERY") {
            return true
        }

        val shp = getSharedPreferences("GOLD", MODE_PRIVATE)
        var allowShowAd = false
        try {
            @SuppressLint("SimpleDateFormat")
            val dateTimeFormat = SimpleDateFormat("yyyy/MM/dd-HH:mm:ss", Locale.US)
            val latestTime = shp.getString(gotAd, null)
            val currentTime: String = dateTimeFormat.format(Date(System.currentTimeMillis()))

            if (latestTime != null) {
                val latestShowTime: Date? = dateTimeFormat.parse(latestTime)
                if (latestShowTime != null) {
                    val timesPlus: Int = AppConfig.ADS_BANNER_TIMER_HOUR * 3600000 +
                            AppConfig.ADS_BANNER_TIMER_MINUTE * 60000
                    latestShowTime.time += timesPlus

                    val timeForCompare: Date? = dateTimeFormat.parse(currentTime)
                    if (timeForCompare != null && timeForCompare.compareTo(latestShowTime) >= 0) {
                        allowShowAd = true
                        shp.edit().putString(gotAd, currentTime).apply()
                    }
                }
            } else {
                shp.edit().putString(gotAd, currentTime).apply()
            }
        } catch (e: Exception) {
            allowShowAd = false
            e.printStackTrace()
        }
        return allowShowAd
        */
    }

    //GOLDV2RAY
    private fun bannerShow(){
        if(AppConfig.ADS_BANNER_STATUS && adsAllowToShow("Ads_Banner_Latest_Show_Time")) {
            // Admob
            val container = findViewById<View>(R.id.ads_container_admob)
            container.visibility = View.GONE
            //if(AppConfig.ADS_SERVICE.equals("ADMOB")) {
            //    val container = findViewById<View>(R.id.ads_container_admob)
            //    val adsBanner = AdView(this)
            //    val request: AdRequest = AdRequest.Builder().build()
            //    adsBanner.setAdSize(AdSize.BANNER)
            //    adsBanner.setAdUnitId(AppConfig.ADMOB_ID_BANNER)
            //    (container as RelativeLayout).addView(adsBanner)
            //    adsBanner.loadAd(request)
            //    adsBanner.adListener = object : AdListener() {
            //        override fun onAdLoaded() {
            //            //adsBannerClose?.visibility = View.VISIBLE
            //            findViewById<View>(R.id.main_ads_area).visibility = View.VISIBLE
            //            container.visibility = View.VISIBLE
            //        }
            //    }
            //}
            // APPNEXT
            val bv = findViewById<BannerView>(R.id.bannerView)
            bv.visibility = View.GONE
            if (AppConfig.ADS_SERVICE.equals("APPNEXT")) {
                bv.apply {
                    setPlacementId("")
                    setBannerSize(com.appnext.banners.BannerSize.BANNER)
                    setBannerListener(object : BannerListener() {
                        override fun onAdLoaded(s: String?, t: AppnextAdCreativeType?) {
                            Log.d("ADebug", "Banner Loaded id=$s type=$t")
                            findViewById<View>(R.id.main_ads_area).visibility = View.VISIBLE
                            bv.visibility = View.VISIBLE
                        }
                        override fun adImpression() { Log.d("ADebug", "Banner Impression") }
                        override fun onAdClicked() { Log.d("ADebug", "Banner Clicked") }
                        override fun onError(e: AppnextError?) { Log.w("ADebug", "Banner Error: $e") }
                    })
                }
                bv.loadAd(BannerAdRequest())
            }
        }
    }

    //GOLDV2RAY
    fun bannerHide() {
        val adsArea: ConstraintLayout = findViewById(R.id.main_ads_area)
        adsArea.visibility = View.GONE
    }

    //GOLDV2RAY
    private var currentNativeAd: NativeAd? = null
    private fun loadNativeAds() {
        if (!AppConfig.ADS_NATIVE_STATUS) return
        if (!AppConfig.ADS_SERVICE.equals("ADMOB")) return

        val container = findViewById<ConstraintLayout>(R.id.main_ads_native)

        val adLoader = AdLoader.Builder(this, AppConfig.ADMOB_ID_Native).forNativeAd{ nativeAd ->
            currentNativeAd?.destroy()
            currentNativeAd = nativeAd

            val adView = layoutInflater.inflate(R.layout.ad_native, null) as NativeAdView

            populateNativeAdView(nativeAd, adView)

            container.removeAllViews()
            container.addView(adView)
        }
        .withAdListener(object : AdListener() {
            override fun onAdLoaded() {
                findViewById<View>(R.id.main_ads_area).visibility = View.VISIBLE
            }
        })
        .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }
    private fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
        adView.setMediaView(adView.findViewById(R.id.native_ad_media));
        adView.setIconView(adView.findViewById(R.id.native_ad_app_icon))
        adView.setHeadlineView(adView.findViewById(R.id.native_ad_headline))
        adView.setBodyView(adView.findViewById(R.id.native_ad_body))
        adView.setCallToActionView(adView.findViewById(R.id.native_ad_cta))

        (adView.headlineView as TextView).text = nativeAd.headline

        val bodyView = adView.bodyView as TextView
        if (nativeAd.body != null) {
            bodyView.text = nativeAd.body
            bodyView.visibility = View.VISIBLE
        } else {
            bodyView.visibility = View.GONE
        }

        val iconView = adView.iconView as ImageView
        if (nativeAd.icon != null) {
            iconView.setImageDrawable(nativeAd.icon!!.drawable)
            iconView.visibility = View.VISIBLE
        } else {
            iconView.visibility = View.GONE
        }

        val ctaView = adView.callToActionView as Button
        if (nativeAd.callToAction != null) {
            ctaView.text = nativeAd.callToAction
            ctaView.visibility = View.VISIBLE
        } else {
            ctaView.visibility = View.GONE
        }

        adView.setNativeAd(nativeAd)
    }

    //GOLDV2RAY
    private fun interstitialShow(){
        if (AppConfig.ADS_INTERSTITIAL_STATUS) {
            val shp = getSharedPreferences("GOLD", MODE_PRIVATE)
            Log.d("ADebug" , String.format("adsTimerCounter:%d , ADS_INTERSTITIAL_SHOW_ON_TIME:%d" , adsTimerCounter , AppConfig.ADS_INTERSTITIAL_SHOW_ON_TIME))
            if (adsTimerCounter >= AppConfig.ADS_INTERSTITIAL_SHOW_ON_TIME) {
                if (adsAllowToShow("Ads_Interstitial_Latest_Show_Time")) {
                    if (AppConfig.ADS_SERVICE == "ADMOB") {
                        Log.d("ADebug" ,"interstitial request")
                        var adsInterstitial: InterstitialAd?
                        val request: AdRequest = AdRequest.Builder().build()
                        InterstitialAd.load(this,
                            AppConfig.ADMOB_ID_INTERSTITIAL,
                            request,
                            object : InterstitialAdLoadCallback() {
                                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                                    shp.edit().putInt("Ads_Interstitial_Latest_Count", 0).apply()
                                    adsTimerCounter = 0

                                    adsInterstitial = interstitialAd
                                    adsInterstitial?.show(this@MainActivity)
                                    Log.d("ADebug" ,"interstitial load successful")
                                }
                                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                                    adsInterstitial = null
                                    Log.d("ADebug" ,"interstitial failed to load")
                                }
                            })
                    }
                    if (AppConfig.ADS_SERVICE == "APPNEXT") {
                        val interstitial_Ad = Interstitial(this, "")
                        interstitial_Ad.setOnAdLoadedCallback(object : OnAdLoaded {
                            override fun adLoaded(bannerId: String?, creativeType: com.appnext.core.AppnextAdCreativeType?) {
                                Log.d("ADebug", "interstitial load successful")
                                shp.edit().putInt("Ads_Interstitial_Latest_Count", 0).apply()
                                adsTimerCounter = 0
                                interstitial_Ad.showAd()
                            }
                        })
                        interstitial_Ad.loadAd()
                    }
                }
            }
        }
    }

    //GOLDV2RAY
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

    //GOLDV2RAY
    private fun fsuDownloader() {
        if(AppConfig.STORE_MODE){
            toast(getString(R.string.this_feature_is_currently_unavailable))
            return
        }

        if (!BuildConfig.IS_MOD) {
            addGoldV2raySubServers()
            importConfigViaSub()
            initGroupTab()
        }
    }

    //GOLDV2RAY
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

    //GOLDV2RAY
    fun getServersTest(smartConnection: Boolean) {
        autoSmartConnectionReq = smartConnection

        val selectedTab = binding.tabGroup.getTabAt(binding.tabGroup.selectedTabPosition)
        selectedSubscriptionIdAtTestStart = selectedTab?.tag as? String
        testingServerList = mainViewModel.serversCache.toList()

        val tabTag = selectedTab?.tag as? String
        selectedSubscriptionIdAtTestStart = tabTag
        testingServerList = mainViewModel.serversCache.toList()
        smartConnectionTargetSubscriptionId = if (smartConnection) {
            normalizeTabTagToSubscriptionId(tabTag)
        } else {
            null
        }

        if (mainViewModel.isRunning.value == true) {
            if (smartConnection) {
                toast(R.string.smart_test_tip)
            } else {
                toast(R.string.speed_test_quick_tip)
            }
            mainViewModel.testAllRealPing()
            startTestResultTimer()
        }
        else if ((binding.recyclerView.adapter?.itemCount ?: 0) > 0) {
            try {
                binding.recyclerView.scrollToPosition(0)
                onFabClick()

                if (smartConnection) {
                    toast(R.string.smart_test_tip)
                } else {
                    toast(R.string.speed_test_quick_tip)
                }
                mainViewModel.testAllRealPing()
                startTestResultTimer()
            } catch (e: Exception) {
                toast(R.string.connect_to_a_server_first)
            }
        }
        else {
            toast(R.string.connect_to_a_server_first)
        }
    }

    //GOLDV2RAY
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

    //GOLDV2RAY
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

    //GOLDV2RAY
    fun listGoUp() {
        binding.recyclerView.scrollToPosition(0)
        goUpState(false)
        goDownState(false)
    }

    //GOLDV2RAY
    fun listGoDown() {
        binding.recyclerView.scrollToPosition(
            Objects.requireNonNull(binding.recyclerView.getAdapter()).getItemCount() - 1
        )
        goUpState(false)
        goDownState(false)
    }

    //GOLDV2RAY
    private fun lockMode(storeMode: Boolean){
        val shp: SharedPreferences = getSharedPreferences("GOLD", Context.MODE_PRIVATE)
        val lock_code: Pinview = findViewById(R.id.lock_code)
        val lock_code_repeat: Pinview = findViewById(R.id.lock_code_repeat)
        if(lock_code.value.length < AppConfig.PIN_CODE_LENGTH || lock_code_repeat.value.length < AppConfig.PIN_CODE_LENGTH){
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
            shp.edit().putString("PIN_CODE", lock_code.value).apply()
            AppConfig.LOCK_PROFILES = true

            adapter.notifyDataSetChanged()

            lockSharingChTitle(true)
            dialogAlert(getString(R.string.congratulations), getString(R.string.lock_sharing_active_tip))
            shareLockHide()
        }
        else if(storeMode){
            shp.edit().putBoolean("SHARE_LOCK", true).apply()
            shp.edit().putBoolean("STORE_MODE", true).apply()
            shp.edit().putString("PIN_CODE", lock_code.value).apply()
            AppConfig.LOCK_PROFILES = true
            AppConfig.STORE_MODE = true

            adapter.notifyDataSetChanged()

            lockSharingChTitle(true)
            dialogAlert(getString(R.string.congratulations), getString(R.string.store_mode_active_tip))
            shareLockHide()
            delGoldV2raySubServers()
        }
    }

    //GOLDV2RAY
    val builtInGeoFiles = arrayOf("geosite.dat", "geoip.dat")
    val extDir by lazy { File(Utils.userAssetPath(this)) }
    private fun addBuiltInGeoItems(assets: List<Pair<String, AssetUrlItem>>): List<Pair<String, AssetUrlItem>> {
        val list = mutableListOf<Pair<String, AssetUrlItem>>()
        builtInGeoFiles.forEach {
            list.add(Utils.getUuid() to AssetUrlItem(it,AppConfig.geoUrl + it))
        }
        return list + assets
    }

    //GOLDV2RAY
    private fun downloadGeo(item: AssetUrlItem, timeout: Int, httpPort: Int): Boolean {
        val targetTemp = File(extDir, item.remarks + "_temp")
        val target = File(extDir, item.remarks)
        var conn: HttpURLConnection? = null
        try {
            conn = URL(item.url).openConnection() as HttpURLConnection
            conn.connectTimeout = timeout
            conn.readTimeout = timeout
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
            Log.e(AppConfig.ANG_PACKAGE, Log.getStackTraceString(e))
            return false
        } finally {
            conn?.disconnect()
        }
    }

    //GOLDV2RAY
    private fun downloadGeoFiles() {
        val httpPort = Utils.parseInt(MmkvManager.decodeSettingsString(AppConfig.PREF_HTTP_PORT), AppConfig.PORT_HTTP.toInt())
        var assets = MmkvManager.decodeAssetUrls()
        assets = addBuiltInGeoItems(assets)
        assets.forEach {
            lifecycleScope.launch(Dispatchers.IO) {
                val result = downloadGeo(it.second, 60000, httpPort)
                launch(Dispatchers.Main) {
                    if (result) {
                        Log.d(ANG_PACKAGE, getString(R.string.toast_success) + " " + it.second.remarks)
                    } else {
                        Log.d(ANG_PACKAGE, getString(R.string.toast_failure) + " " + it.second.remarks)
                    }
                }
            }
        }
    }

    //GOLDV2RAY
    private fun downloadGeoFilesCheck(geoDownloadOnTime :Int) {
        val shp: SharedPreferences = getSharedPreferences("GOLD", Context.MODE_PRIVATE)
        var geoRunsCounter: Int = shp.getInt("geoRunsCounter", 0)
        geoRunsCounter++
        shp.edit().putInt("geoRunsCounter", geoRunsCounter).apply()
        if (geoRunsCounter > geoDownloadOnTime) {
            shp.edit().putInt("geoRunsCounter", 0).apply()
            downloadGeoFiles()
        }
    }

    //GOLDV2RAY
    fun moveServerToGroup(guid: String, original: ProfileItem, targetGroup: String) {
        val movedProfile = original.copy(subscriptionId = targetGroup)

        MmkvManager.encodeServerConfig(guid, movedProfile)
        toast(getString(R.string.server_copied))

        mainViewModel.subscriptionIdChanged("")
        initGroupTab()
    }
}