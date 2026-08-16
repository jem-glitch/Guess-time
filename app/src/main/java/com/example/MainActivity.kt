package com.example

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.sound.SmartSoundManager
import com.example.ui.theme.MyApplicationTheme
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // 1. Initialize Google Mobile Ads SDK asynchronously in background
    CoroutineScope(Dispatchers.IO).launch {
      try {
        MobileAds.initialize(this@MainActivity) { initializationStatus ->
          Log.d("AdMob", "AdMob initialized successfully: $initializationStatus")
        }
      } catch (e: Exception) {
        Log.e("AdMob", "Error initializing AdMob SDK", e)
      }
    }

    setContent {
      MyApplicationTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = androidx.compose.ui.graphics.Color.White
        ) {
          ReactionTimerScreen(
            modifier = Modifier
              .fillMaxSize()
              .safeDrawingPadding()
              .testTag("reaction_timer_screen")
          )
        }
      }
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReactionTimerScreen(modifier: Modifier = Modifier) {
  val context = LocalContext.current

  // Instantiate SmartSoundManager for Offline/Online and Persistent Sound handling
  val soundManager = remember { SmartSoundManager(context.applicationContext) }

  val webView = remember {
    WebView(context).apply {
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
      setBackgroundColor(Color.WHITE)
      overScrollMode = View.OVER_SCROLL_NEVER
      isVerticalScrollBarEnabled = false
      isHorizontalScrollBarEnabled = false

      settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        mediaPlaybackRequiresUserGesture = false
        allowFileAccess = true
        allowContentAccess = true
        cacheMode = WebSettings.LOAD_DEFAULT
        useWideViewPort = true
        loadWithOverviewMode = true
        setSupportZoom(false)
        builtInZoomControls = false
        displayZoomControls = false
      }

      // Add Javascript Interface for the Smart Sound System
      addJavascriptInterface(soundManager, "AndroidSoundBridge")
      soundManager.webViewRef = this

      webViewClient = object : WebViewClient() {}
      webChromeClient = object : WebChromeClient() {}

      loadUrl("file:///android_asset/index.html")
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      webView.stopLoading()
      webView.destroy()
      soundManager.release()
    }
  }

  // Structured layout: Main timer & controls on top, AdMob Banner at bottom
  Column(
    modifier = modifier
      .fillMaxSize()
      .background(androidx.compose.ui.graphics.Color.White)
  ) {
    // 1. Main View (WebView containing Retro LED Timer & Controls)
    AndroidView(
      factory = { webView },
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .background(androidx.compose.ui.graphics.Color.White)
        .testTag("reaction_timer_webview")
    )

    // 2. AdMob Banner at the bottom (never overlaps timer, buttons, or audio download)
    AdmobBannerSection(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .background(androidx.compose.ui.graphics.Color.White)
        .padding(bottom = 6.dp)
        .testTag("admob_banner_section")
    )
  }
}

/**
 * Robust AdMob Banner Composable.
 * Uses exact user's Banner Ad Unit ID from strings.xml.
 * Guaranteed failure tolerance: failure to load will never affect the timer or crash the app.
 */
@Composable
fun AdmobBannerSection(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val bannerAdUnitId = remember { context.getString(R.string.admob_banner_ad_unit_id) }

  Box(
    modifier = modifier,
    contentAlignment = Alignment.Center
  ) {
    AndroidView(
      factory = { ctx ->
        AdView(ctx).apply {
          setAdSize(AdSize.BANNER)
          adUnitId = bannerAdUnitId

          adListener = object : AdListener() {
            override fun onAdLoaded() {
              super.onAdLoaded()
              Log.d("AdMob", "AdMob Banner loaded successfully.")
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
              super.onAdFailedToLoad(error)
              Log.w("AdMob", "AdMob Banner failed to load: ${error.message} (code: ${error.code})")
              // Non-blocking: App and timer continue flawlessly
            }
          }

          val adRequest = AdRequest.Builder().build()
          loadAd(adRequest)
        }
      },
      update = { adView ->
        // No-op or dynamic updates if needed
      }
    )
  }
}
