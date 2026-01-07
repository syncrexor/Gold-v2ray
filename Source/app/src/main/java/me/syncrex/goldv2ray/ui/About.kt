package me.syncrex.goldv2ray.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import me.syncrex.goldv2ray.R
import androidx.core.graphics.drawable.toDrawable
import me.syncrex.goldv2ray.AppConfig
import me.syncrex.goldv2ray.BuildConfig
import me.syncrex.goldv2ray.handler.SettingsManager
import me.syncrex.goldv2ray.util.MyContextWrapper
import me.syncrex.goldv2ray.util.Utils

class About : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_about)
        } catch (e: Exception) {
            setContentView(R.layout.activity_about_low)
        }

        // statusbar color
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.colorBackground)
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        } else
            window.statusBarColor = ContextCompat.getColor(this, R.color.colorBackgroundDarker)

        // actionbar settings
        supportActionBar?.let { actionBar ->
            val title = SpannableString(getString(R.string.about))
            title.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, R.color.colorText)), 0, title.length, 0)
            title.setSpan(StyleSpan(Typeface.BOLD), 0, title.length, 0)
            actionBar.title = title
            actionBar.elevation = 0f
            actionBar.setDisplayHomeAsUpEnabled(true)
            val backIcon = ContextCompat.getDrawable(this, R.drawable.ic_back)?.mutate()
            backIcon?.setTint(ContextCompat.getColor(this, R.color.colorText))
            actionBar.setHomeAsUpIndicator(backIcon)
        }

        // navigationbar settings
        window.navigationBarColor = ContextCompat.getColor(this, R.color.colorBaseDark)

        // description
        if (BuildConfig.IS_MOD) {
            findViewById<TextView>(R.id.about_des).setText(R.string.about_description_mod_version)
        }

        // Rate App
        findViewById<Button>(R.id.about_rate_app).setOnClickListener {
            val packageName = packageName
            try {
                val uri = Uri.parse("market://details?id=$packageName")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                val url = "https://play.google.com/store/apps/details?id=$packageName"
                val webIntent = Intent(Intent.ACTION_VIEW)
                webIntent.setData(Uri.parse(url))
                startActivity(webIntent)
            }
        }

        // Share App
        findViewById<Button>(R.id.about_share_app).setOnClickListener {
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

        // Other Apps
        findViewById<Button>(R.id.about_other_apps).setOnClickListener {
            try{
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppConfig.GOOGLEPLAY_URL)))
            } catch (e: Exception){
               Toast.makeText(this, R.string.error_occurred, Toast.LENGTH_SHORT).show()
           }
        }

        // Feedback
        findViewById<Button>(R.id.about_feedback).setOnClickListener {
            try {
                val selectorIntent = Intent(Intent.ACTION_SENDTO)
                selectorIntent.setData(Uri.parse("mailto:"))
                val emailIntent = Intent(Intent.ACTION_SEND)
                emailIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf(AppConfig.FEEDBACK_MAIL))
                emailIntent.putExtra(
                    Intent.EXTRA_SUBJECT,
                    getString(R.string.title_pref_feedback) + ": " + getString(R.string.app_name)
                )
                emailIntent.selector = selectorIntent
                startActivity(
                    Intent.createChooser(
                        emailIntent,
                        getString(R.string.title_pref_feedback)
                    )
                )
            } catch (e: Exception){
                Toast.makeText(this, R.string.error_occurred, Toast.LENGTH_SHORT).show()
            }
        }

        // Privacy Policy
        findViewById<Button>(R.id.about_privacy_policy).setOnClickListener {
            Utils.openUri(this, AppConfig.PRIVACYPOLICY_URL)
        }

        // Version
        val aboutVersion = findViewById<TextView>(R.id.about_version)
        if (aboutVersion != null) {
            try {
                aboutVersion.text = "V " + BuildConfig.VERSION_NAME
            } catch (e: PackageManager.NameNotFoundException) {
                aboutVersion.visibility = View.INVISIBLE
            }
        }

        // auto hide keyboard
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(MyContextWrapper.wrap(newBase ?: return, SettingsManager.getLocale()))
    }

    override fun onResume() {
        super.onResume()

        // hide keyboard
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let {
            imm.hideSoftInputFromWindow(it.windowToken, 0)
            it.clearFocus()
        }
    }

    // an back pressed
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}