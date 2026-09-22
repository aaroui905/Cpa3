package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.View
import android.widget.Toast
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WebAsset
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.MainActivity
import com.example.data.model.AppSettings
import com.example.data.model.AutomationState
import com.example.data.model.ExtractedInfo
import com.example.data.model.GeneratedIdentity
import com.example.data.model.ScriptItem
import com.example.service.AutomationScriptBuilder
import com.example.ui.BrowserCommand
import com.example.ui.theme.CpaAccent
import com.example.ui.theme.CpaBg
import com.example.ui.theme.CpaBorder
import com.example.ui.theme.CpaCard
import com.example.ui.theme.CpaCardElevated
import com.example.ui.theme.CpaError
import com.example.ui.theme.CpaPrimary
import com.example.ui.theme.CpaPrimaryBorder
import com.example.ui.theme.CpaPrimaryDim
import com.example.ui.theme.CpaSuccess
import com.example.ui.theme.CpaText
import com.example.ui.theme.CpaTextDim
import com.example.ui.theme.CpaTextMuted
import com.example.ui.theme.CpaWarning
import com.example.util.WebProxyManager
import kotlinx.coroutines.delay

class WebAppInterface(
    private val onCompleted: (String, String) -> Unit,
    private val onOfferClicked: ((String, String) -> Unit)? = null
) {
    @JavascriptInterface
    fun onTaskCompleted(keyword: String, url: String) {
        onCompleted(keyword, url)
    }

    @JavascriptInterface
    fun onOfferClicked(text: String, url: String) {
        onOfferClicked?.invoke(text, url)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    settings: AppSettings,
    automationState: AutomationState,
    extractedInfo: ExtractedInfo,
    identity: GeneratedIdentity,
    scripts: List<ScriptItem>,
    browserCommand: BrowserCommand?,
    clickTexts: List<String> = emptyList(),
    activeClickText: String? = null,
    onClearBrowserCommand: () -> Unit,
    onNotifyCompletion: (String, String) -> Unit,
    onOfferClicked: ((String, String) -> Unit)? = null,
    onUpdateWebRtcMode: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var urlInput by remember { mutableStateOf("") }
    var currentDisplayUrl by remember { mutableStateOf("about:blank") }
    var webProgress by remember { mutableFloatStateOf(0f) }
    var isPageLoading by remember { mutableStateOf(false) }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Resolve the active proxy IP to be used for WebRTC ICE spoofing
    val effectiveWebRtcIp = remember(settings.webrtcCustomIp, automationState.activeIp, extractedInfo.ip, settings.proxyHost) {
        if (settings.webrtcCustomIp.isNotBlank()) {
            settings.webrtcCustomIp.trim()
        } else if (automationState.activeIp.isNotBlank() && automationState.activeIp != "Not Connected") {
            automationState.activeIp
        } else if (extractedInfo.ip.isNotBlank()) {
            extractedInfo.ip
        } else if (settings.proxyHost.isNotBlank()) {
            settings.proxyHost
        } else {
            "104.28.19.42"
        }
    }

    // Keep proxy configuration synchronized whenever proxy settings change
    LaunchedEffect(settings.proxyHost, settings.proxyPort, settings.proxyType, settings.proxyUser, settings.proxyPass) {
        val port = settings.proxyPort.toIntOrNull()
        WebProxyManager.applyProxy(context, settings.proxyHost, port, settings.proxyType, settings.proxyUser, settings.proxyPass)
    }

    // Re-inject WebRTC protection dynamically whenever proxy IP, mode, or timezone changes
    LaunchedEffect(webViewRef, effectiveWebRtcIp, settings.webrtcMode, extractedInfo.timezone, extractedInfo.language) {
        webViewRef?.evaluateJavascript(
            AutomationScriptBuilder.buildAntiDetectionScript(
                proxyIp = effectiveWebRtcIp,
                webrtcMode = settings.webrtcMode,
                timezone = extractedInfo.timezone,
                language = extractedInfo.language,
                latitude = extractedInfo.latitude,
                longitude = extractedInfo.longitude
            ),
            null
        )
    }

    // Autonomous Smart Automation Loop: Only runs when automation is actively running
    LaunchedEffect(webViewRef, identity, automationState.isRunning, automationState.activeTaskCategories, clickTexts, activeClickText) {
        while (automationState.isRunning) {
            delay(1200)
            webViewRef?.let { wv ->
                wv.evaluateJavascript(
                    AutomationScriptBuilder.buildSmartFormFillScript(
                        identity = identity,
                        categories = automationState.activeTaskCategories,
                        clickTexts = clickTexts,
                        activeClickText = activeClickText ?: automationState.activeClickText
                    ),
                    null
                )
            }
        }
    }

    // Execute BrowserCommands from ViewModel
    LaunchedEffect(browserCommand) {
        browserCommand?.let { cmd ->
            when (cmd) {
                is BrowserCommand.LoadUrl -> {
                    urlInput = cmd.url
                    currentDisplayUrl = cmd.url
                    webViewRef?.let { webView ->
                        if (!cmd.userAgent.isNullOrBlank()) {
                            webView.settings.userAgentString = cmd.userAgent
                        }
                        val headers = mutableMapOf<String, String>()
                        if (!cmd.referer.isNullOrBlank()) {
                            headers["Referer"] = cmd.referer
                        }
                        webView.loadUrl(cmd.url, headers)
                    }
                }
                is BrowserCommand.Reload -> webViewRef?.reload()
                is BrowserCommand.GoBack -> if (webViewRef?.canGoBack() == true) webViewRef?.goBack()
                is BrowserCommand.GoForward -> if (webViewRef?.canGoForward() == true) webViewRef?.goForward()
                is BrowserCommand.ClearUrl -> {
                    webViewRef?.let { webView ->
                        webView.clearCache(true)
                        webView.clearHistory()
                        webView.clearFormData()
                        webView.loadUrl("about:blank")
                        currentDisplayUrl = "about:blank"
                        urlInput = ""
                    }
                }
                is BrowserCommand.ClearCacheAndStorage -> {
                    MainActivity.clearWebViewData(context, webViewRef) {
                        webViewRef?.loadUrl("about:blank")
                        currentDisplayUrl = "about:blank"
                        urlInput = ""
                    }
                }
            }
            onClearBrowserCommand()
        }
    }

    Column(modifier = modifier.fillMaxSize().background(CpaBg)) {
        // 1. Ultra-Slim IP & Proxy Status Top Bar (Thinned)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .background(CpaCardElevated)
                .border(0.5.dp, CpaBorder.copy(alpha = 0.4f))
                .padding(horizontal = 6.dp, vertical = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // IP & WebRTC on the left
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (WebProxyManager.isProxyActive) CpaSuccess else CpaWarning)
                )
                Spacer(modifier = Modifier.width(3.dp))
                val displayIp = if (automationState.activeIp.isNotBlank() && automationState.activeIp != "Not Connected") {
                    automationState.activeIp
                } else if (extractedInfo.ip.isNotBlank()) {
                    extractedInfo.ip
                } else {
                    "Detecting..."
                }
                Text(
                    text = "IP: $displayIp",
                    color = CpaText,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(4.dp))

                // WebRTC Protection Badge
                val webrtcColor = when (settings.webrtcMode) {
                    "spoof" -> CpaSuccess
                    "disabled" -> CpaWarning
                    else -> CpaError
                }
                val webrtcBadgeText = when (settings.webrtcMode) {
                    "spoof" -> "RTC: SPOOF"
                    "disabled" -> "RTC: OFF"
                    else -> "RTC: REAL"
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .background(webrtcColor.copy(alpha = 0.12f))
                        .border(0.5.dp, webrtcColor.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                        .clickable {
                            val nextMode = when (settings.webrtcMode) {
                                "spoof" -> "disabled"
                                "disabled" -> "real"
                                else -> "spoof"
                            }
                            onUpdateWebRtcMode?.invoke(nextMode)
                        }
                        .padding(horizontal = 2.dp)
                ) {
                    Text(
                        text = webrtcBadgeText,
                        color = webrtcColor,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.width(3.dp))

                // Proxy Lock Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (WebProxyManager.isProxyActive) CpaPrimary.copy(alpha = 0.12f) else CpaCard)
                        .border(0.5.dp, if (WebProxyManager.isProxyActive) CpaPrimary.copy(alpha = 0.3f) else CpaBorder, RoundedCornerShape(2.dp))
                        .padding(horizontal = 2.dp)
                ) {
                    Text(
                        text = if (WebProxyManager.isProxyActive) "PROXY" else "DIRECT",
                        color = if (WebProxyManager.isProxyActive) CpaPrimary else CpaTextMuted,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Center AI Plan / Funnel if present
            if (automationState.activePlanSummary.isNotBlank()) {
                Text(
                    text = automationState.activePlanSummary,
                    color = CpaAccent,
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .weight(1f, fill = false)
                )
            }

            // Status on the right
            Row(verticalAlignment = Alignment.CenterVertically) {
                val statusText = if (automationState.isRunning) {
                    automationState.phase.uppercase()
                } else {
                    "READY"
                }
                val statusColor = if (automationState.isRunning) CpaWarning else CpaPrimary

                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // 2. Ultra-Slim Navigation & URL Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CpaCard)
                .border(1.dp, CpaBorder)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { webViewRef?.let { if (it.canGoBack()) it.goBack() } },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CpaText, modifier = Modifier.size(14.dp))
            }

            IconButton(
                onClick = { webViewRef?.let { if (it.canGoForward()) it.goForward() } },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward", tint = CpaText, modifier = Modifier.size(14.dp))
            }

            IconButton(
                onClick = { webViewRef?.reload() },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = CpaText, modifier = Modifier.size(14.dp))
            }

            if (currentDisplayUrl.isNotBlank() && currentDisplayUrl != "about:blank") {
                IconButton(
                    onClick = {
                        urlInput = ""
                        currentDisplayUrl = "about:blank"
                        webViewRef?.stopLoading()
                        webViewRef?.loadUrl("about:blank")
                        MainActivity.clearWebViewData(context, webViewRef)
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close Page & Clear", tint = CpaError, modifier = Modifier.size(14.dp))
                }
            }

            Spacer(modifier = Modifier.width(3.dp))

            // Ultra-Slim URL Input Container
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(26.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CpaBg)
                    .border(1.dp, CpaBorder, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (currentDisplayUrl.startsWith("https")) Icons.Default.Lock else Icons.Default.Language,
                    contentDescription = "SSL",
                    tint = if (currentDisplayUrl.startsWith("https")) CpaSuccess else CpaTextMuted,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                BasicTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = CpaText,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Go
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onGo = {
                            var target = urlInput.trim()
                            if (target.isNotBlank()) {
                                if (!target.startsWith("http://") && !target.startsWith("https://")) {
                                    target = "https://$target"
                                }
                                urlInput = target
                                currentDisplayUrl = target
                                webViewRef?.loadUrl(target)
                            }
                        }
                    ),
                    cursorBrush = SolidColor(CpaPrimary),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Ultra-Slim GO button
            Box(
                modifier = Modifier
                    .height(26.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CpaPrimary)
                    .clickable {
                        var target = urlInput.trim()
                        if (!target.startsWith("http://") && !target.startsWith("https://")) {
                            target = "https://$target"
                        }
                        urlInput = target
                        currentDisplayUrl = target
                        webViewRef?.loadUrl(target)
                    }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("GO", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }

        // 3. Quick Verification Shortcut Bar (Instant testing of proxy, WebRTC leak, canvas, and fingerprint)
        val leakScrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CpaCardElevated)
                .horizontalScroll(leakScrollState)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Leak Test:", color = CpaTextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(CpaCard)
                    .border(1.dp, CpaPrimaryBorder, RoundedCornerShape(3.dp))
                    .clickable {
                        val target = "https://browserleaks.com/ip"
                        urlInput = target
                        currentDisplayUrl = target
                        webViewRef?.loadUrl(target)
                    }
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text("BrowserLeaks (IP & WebRTC)", color = CpaPrimary, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(CpaCard)
                    .border(1.dp, CpaBorder, RoundedCornerShape(3.dp))
                    .clickable {
                        val target = "https://browserleaks.com/webrtc"
                        urlInput = target
                        currentDisplayUrl = target
                        webViewRef?.loadUrl(target)
                    }
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text("WebRTC Engine", color = CpaAccent, fontSize = 9.sp)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(CpaCard)
                    .border(1.dp, CpaBorder, RoundedCornerShape(3.dp))
                    .clickable {
                        val target = "https://whoer.net"
                        urlInput = target
                        currentDisplayUrl = target
                        webViewRef?.loadUrl(target)
                    }
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text("Whoer.net", color = CpaText, fontSize = 9.sp)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(CpaCard)
                    .border(1.dp, CpaBorder, RoundedCornerShape(3.dp))
                    .clickable {
                        val target = "https://browserleaks.com/dns"
                        urlInput = target
                        currentDisplayUrl = target
                        webViewRef?.loadUrl(target)
                    }
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text("DNS Leak Test", color = CpaAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(CpaCard)
                    .border(1.dp, CpaBorder, RoundedCornerShape(3.dp))
                    .clickable {
                        val target = "https://dnsleaktest.com"
                        urlInput = target
                        currentDisplayUrl = target
                        webViewRef?.loadUrl(target)
                    }
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text("dnsleaktest.com", color = CpaText, fontSize = 9.sp)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(CpaCard)
                    .border(1.dp, CpaBorder, RoundedCornerShape(3.dp))
                    .clickable {
                        val target = "https://iphey.com"
                        urlInput = target
                        currentDisplayUrl = target
                        webViewRef?.loadUrl(target)
                    }
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text("IPHey Anonymity", color = CpaText, fontSize = 9.sp)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(CpaCard)
                    .border(1.dp, CpaBorder, RoundedCornerShape(3.dp))
                    .clickable {
                        val target = "https://api.ipify.org"
                        urlInput = target
                        currentDisplayUrl = target
                        webViewRef?.loadUrl(target)
                    }
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text("api.ipify.org", color = CpaTextMuted, fontSize = 9.sp)
            }

            // On-demand Purge Cache, Cookies & Storage Action Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(CpaError.copy(alpha = 0.15f))
                    .border(1.dp, CpaError.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                    .clickable {
                        MainActivity.clearWebViewData(context, webViewRef) {
                            Toast.makeText(context, "Purged WebView Cache, Cookies & Local Storage", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear Cache & Local Storage",
                        tint = CpaError,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Purge Cache & Storage", color = CpaError, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Web Loading Progress Indicator
        if (isPageLoading && webProgress < 1f) {
            LinearProgressIndicator(
                progress = { webProgress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = CpaPrimary,
                trackColor = CpaBorder
            )
        }

        // Real Native Android WebView
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { ctx ->
                    // Apply current proxy settings before loading anything
                    val port = settings.proxyPort.toIntOrNull()
                    WebProxyManager.applyProxy(ctx, settings.proxyHost, port, settings.proxyType, settings.proxyUser, settings.proxyPass)

                    WebView(ctx).apply {
                        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                        this.settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            javaScriptCanOpenWindowsAutomatically = true
                            setSupportMultipleWindows(false) // Open all popups inside the same webview
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

                            // STRICT ANTI-DNS-LEAK CONFIGURATION:
                            // 1. Disable Safe Browsing (Google Safe Browsing bypasses proxy to query Google lookup servers directly via local DNS)
                            safeBrowsingEnabled = false

                            // 2. Disable Geolocation network requests from WebView
                            setGeolocationEnabled(false)

                            // 3. Disable File and Content scheme access to prevent file-based leaks
                            allowFileAccess = false
                            allowContentAccess = false

                            // 4. Force no media background autoplay
                            mediaPlaybackRequiresUserGesture = false
                        }

                        // Force Safe Browsing disabled via AndroidX WebSettingsCompat if supported
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
                            try {
                                WebSettingsCompat.setSafeBrowsingEnabled(this.settings, false)
                            } catch (e: Exception) {
                                // Ignore if unsupported
                            }
                        }

                        // Register document start script if supported so WebRTC spoofing is injected before DOM loads
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                            try {
                                WebViewCompat.addDocumentStartJavaScript(
                                    this,
                                    AutomationScriptBuilder.buildAntiDetectionScript(
                                        proxyIp = effectiveWebRtcIp,
                                        webrtcMode = settings.webrtcMode,
                                        timezone = extractedInfo.timezone,
                                        language = extractedInfo.language,
                                        latitude = extractedInfo.latitude,
                                        longitude = extractedInfo.longitude
                                    ),
                                    setOf("*")
                                )
                                WebViewCompat.addDocumentStartJavaScript(
                                    this,
                                    AutomationScriptBuilder.buildTimezoneScript(extractedInfo.timezone, extractedInfo.language),
                                    setOf("*")
                                )
                                WebViewCompat.addDocumentStartJavaScript(
                                    this,
                                    AutomationScriptBuilder.buildCanvasNoiseScript(),
                                    setOf("*")
                                )
                            } catch (e: Exception) {
                                // Fallback handled in onPageStarted
                            }
                        }

                        addJavascriptInterface(
                            WebAppInterface(
                                onCompleted = { kw, pageUrl ->
                                    onNotifyCompletion(kw, pageUrl)
                                },
                                onOfferClicked = { txt, pageUrl ->
                                    onOfferClicked?.invoke(txt, pageUrl)
                                }
                            ),
                            "AndroidBridge"
                        )

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                webProgress = newProgress / 100f
                                isPageLoading = newProgress < 100
                            }

                            // Strictly deny WebRTC media stream requests (Camera/Microphone/WebRTC capture)
                            override fun onPermissionRequest(request: PermissionRequest?) {
                                try {
                                    request?.deny()
                                } catch (e: Exception) {
                                    // ignore
                                }
                            }

                            // Authorize Geolocation prompts so spoofed JS coordinates (matching proxy IP) are delivered
                            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                                try {
                                    callback?.invoke(origin, true, false)
                                } catch (e: Exception) {
                                    // ignore
                                }
                            }
                        }

                        webViewClient = object : MainActivity.CustomWebViewClient(
                            proxyUserProvider = { settings.proxyUser },
                            proxyPassProvider = { settings.proxyPass },
                            onPageStartedCallback = { view, url, _ ->
                                isPageLoading = true
                                url?.let {
                                    currentDisplayUrl = it
                                    urlInput = it
                                }

                                // Inject BEFORE-load reinforced anti-detection & WebRTC spoofing
                                view?.evaluateJavascript(
                                    AutomationScriptBuilder.buildAntiDetectionScript(
                                        proxyIp = effectiveWebRtcIp,
                                        webrtcMode = settings.webrtcMode,
                                        timezone = extractedInfo.timezone,
                                        language = extractedInfo.language,
                                        latitude = extractedInfo.latitude,
                                        longitude = extractedInfo.longitude
                                    ),
                                    null
                                )
                                view?.evaluateJavascript(AutomationScriptBuilder.buildTimezoneScript(extractedInfo.timezone, extractedInfo.language), null)
                                view?.evaluateJavascript(AutomationScriptBuilder.buildCanvasNoiseScript(), null)

                                // Inject user before-load scripts
                                scripts.filter { it.enabled && it.timing == "before" }.forEach { s ->
                                    view?.evaluateJavascript(s.code, null)
                                }
                            },
                            onPageFinishedCallback = { view, url ->
                                isPageLoading = false
                                url?.let {
                                    currentDisplayUrl = it
                                    urlInput = it
                                }

                                // Re-inject anti-detection script to ensure all dynamic elements are protected
                                view?.evaluateJavascript(
                                    AutomationScriptBuilder.buildAntiDetectionScript(
                                        proxyIp = effectiveWebRtcIp,
                                        webrtcMode = settings.webrtcMode,
                                        timezone = extractedInfo.timezone,
                                        language = extractedInfo.language,
                                        latitude = extractedInfo.latitude,
                                        longitude = extractedInfo.longitude
                                    ),
                                    null
                                )

                                // Inject AFTER-load scripts:
                                // 1. Smart form filler with active generated identity, task categories and Priority #1 Offer Click
                                val effClickText = activeClickText ?: automationState.activeClickText
                                view?.evaluateJavascript(
                                    AutomationScriptBuilder.buildSmartFormFillScript(
                                        identity = identity,
                                        categories = automationState.activeTaskCategories,
                                        clickTexts = clickTexts,
                                        activeClickText = effClickText
                                    ),
                                    null
                                )

                                // 2. Human behavior simulator
                                view?.evaluateJavascript(AutomationScriptBuilder.buildHumanBehaviorScript(), null)

                                // 3. Smart completion detector
                                val keywords = listOf("thank you", "congratulations", "success", "completed", "verified", "confirmed", "survey", "reward")
                                view?.evaluateJavascript(AutomationScriptBuilder.buildCompletionDetectorScript(keywords), null)

                                // 4. Active user after-load scripts
                                scripts.filter { it.enabled && it.timing == "after" }.forEach { s ->
                                    view?.evaluateJavascript(s.code, null)
                                }

                                // 5. Secondary injection delayed for dynamically loaded SPA frameworks (React/Vue/Angular)
                                postDelayed({
                                    view?.evaluateJavascript(
                                        AutomationScriptBuilder.buildSmartFormFillScript(
                                            identity = identity,
                                            categories = automationState.activeTaskCategories,
                                            clickTexts = clickTexts,
                                            activeClickText = effClickText
                                        ),
                                        null
                                    )
                                }, 1800)
                            },
                            onErrorCallback = { _, request, _ ->
                                if (request?.isForMainFrame == true) {
                                    isPageLoading = false
                                }
                            }
                        ) {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val uri = request?.url ?: return false
                                val scheme = uri.scheme?.lowercase() ?: ""

                                // Instantly block navigation attempts to WebRTC STUN/TURN schemes
                                if (MainActivity.CustomWebViewClient.isWebRtcStunTurnTraffic(uri, request.isForMainFrame)) {
                                    return true
                                }

                                // Let WebView handle normal HTTP/HTTPS natively with full POST data and redirect support
                                if (scheme == "http" || scheme == "https") {
                                    return false
                                }

                                // Safely handle or ignore non-http schemes without causing crashes
                                return try {
                                    if (scheme == "tel" || scheme == "mailto" || scheme == "sms") {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                                        view?.context?.startActivity(intent)
                                        true
                                    } else {
                                        true
                                    }
                                } catch (e: Exception) {
                                    true
                                }
                            }
                        }

                        if (urlInput.isNotBlank()) {
                            loadUrl(urlInput)
                        } else {
                            loadUrl("about:blank")
                        }
                        webViewRef = this
                    }
                },
                update = { webView ->
                    webViewRef = webView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Standby Overlay when browser is idle and no page is loaded
            if (!automationState.isRunning && (currentDisplayUrl.isBlank() || currentDisplayUrl == "about:blank")) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CpaBg.copy(alpha = 0.96f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(CpaCardElevated)
                                .border(1.dp, CpaBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WebAsset,
                                contentDescription = null,
                                tint = CpaPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "BROWSER STANDBY",
                            color = CpaText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Proxy is locked globally. WebRTC IP leaks are neutralized.",
                            color = CpaTextDim,
                            fontSize = 11.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val target = "https://browserleaks.com/ip"
                                    urlInput = target
                                    currentDisplayUrl = target
                                    webViewRef?.loadUrl(target)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CpaPrimary),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Check BrowserLeaks", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    val target = "https://api.ipify.org"
                                    urlInput = target
                                    currentDisplayUrl = target
                                    webViewRef?.loadUrl(target)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CpaCardElevated),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CpaBorder),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Check Ipify", color = CpaText, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
