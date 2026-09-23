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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tab
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
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
import com.example.service.TaskCategoryPlanner
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
import java.util.UUID

class WebAppInterface(
    private val onCompleted: (String, String) -> Unit,
    private val onOfferClicked: ((String, String) -> Unit)? = null,
    private val onPageAnalyzed: ((String) -> Unit)? = null
) {
    @JavascriptInterface
    fun onTaskCompleted(keyword: String, url: String) {
        onCompleted(keyword, url)
    }

    @JavascriptInterface
    fun onOfferClicked(text: String, url: String) {
        onOfferClicked?.invoke(text, url)
    }

    @JavascriptInterface
    fun onPageAnalyzed(reportJson: String) {
        onPageAnalyzed?.invoke(reportJson)
    }
}

/**
 * Multi-Tab Model tracking tab identity, display title, URL, loading state and associated WebView.
 */
data class BrowserTabModel(
    val id: String,
    var title: String = "Tab 1",
    var url: String = "about:blank",
    var isLoading: Boolean = false,
    var progress: Float = 0f,
    var stageBadge: String = "",
    var webView: WebView? = null
)

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
    onPageAnalyzed: ((String) -> Unit)? = null,
    onActiveTabChanged: ((String) -> Unit)? = null,
    onUpdateWebRtcMode: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Multi-Tab browser tabs collection
    val tabs = remember {
        mutableStateListOf(
            BrowserTabModel(id = "tab_1", title = "Main Tab", url = "about:blank")
        )
    }
    var activeTabId by remember { mutableStateOf("tab_1") }
    val activeTab = tabs.find { it.id == activeTabId } ?: tabs.firstOrNull() ?: BrowserTabModel("tab_1")

    var urlInput by remember { mutableStateOf("") }
    var currentDisplayUrl by remember { mutableStateOf("about:blank") }
    var webProgress by remember { mutableFloatStateOf(0f) }
    var isPageLoading by remember { mutableStateOf(false) }

    // Synchronize URL input with active tab
    LaunchedEffect(activeTabId, activeTab.url) {
        urlInput = if (activeTab.url == "about:blank") "" else activeTab.url
        currentDisplayUrl = activeTab.url
        isPageLoading = activeTab.isLoading
        webProgress = activeTab.progress
    }

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

    // Re-inject WebRTC protection dynamically ONLY into the ACTIVE displayed tab
    LaunchedEffect(activeTabId, activeTab.webView, effectiveWebRtcIp, settings.webrtcMode, extractedInfo.timezone, extractedInfo.language) {
        activeTab.webView?.evaluateJavascript(
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

    // Autonomous 10-Second / URL / Tab Re-Analysis Engine:
    // Re-analyzes page DOM every 10 seconds or when URL/tab changes.
    // STRICT ISOLATION: Targets ONLY the currently open & displayed tab!
    var reanalysisCountdown by remember { mutableIntStateOf(10) }
    LaunchedEffect(activeTabId, currentDisplayUrl, automationState.isRunning) {
        reanalysisCountdown = 10
        // Trigger immediate perception analysis on tab or URL transition
        val currentWv = activeTab.webView
        currentWv?.evaluateJavascript(
            AutomationScriptBuilder.buildPageAnalyzerScript(clickTexts, activeClickText ?: automationState.activeClickText),
            null
        )

        while (automationState.isRunning) {
            delay(1000)
            reanalysisCountdown--
            if (reanalysisCountdown <= 0) {
                reanalysisCountdown = 10
                // ONLY evaluate on active displayed tab!
                val activeDisplayed = tabs.find { it.id == activeTabId }
                activeDisplayed?.webView?.evaluateJavascript(
                    AutomationScriptBuilder.buildPageAnalyzerScript(clickTexts, activeClickText ?: automationState.activeClickText),
                    null
                )
            }
        }
    }

    // Autonomous Smart Automation Loop: Only runs when automation is actively running
    // STRICT ISOLATION: Targets ONLY the currently open & displayed tab!
    LaunchedEffect(activeTabId, identity, automationState.isRunning, automationState.activeTaskCategories, clickTexts, activeClickText) {
        while (automationState.isRunning) {
            delay(1200)
            val activeDisplayed = tabs.find { it.id == activeTabId }
            activeDisplayed?.webView?.evaluateJavascript(
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

    // Execute BrowserCommands from ViewModel on the ACTIVE tab
    LaunchedEffect(browserCommand) {
        browserCommand?.let { cmd ->
            when (cmd) {
                is BrowserCommand.LoadUrl -> {
                    urlInput = cmd.url
                    currentDisplayUrl = cmd.url
                    activeTab.url = cmd.url
                    activeTab.webView?.let { webView ->
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
                is BrowserCommand.Reload -> activeTab.webView?.reload()
                is BrowserCommand.GoBack -> if (activeTab.webView?.canGoBack() == true) activeTab.webView?.goBack()
                is BrowserCommand.GoForward -> if (activeTab.webView?.canGoForward() == true) activeTab.webView?.goForward()
                is BrowserCommand.ClearUrl -> {
                    activeTab.webView?.let { webView ->
                        webView.clearCache(true)
                        webView.clearHistory()
                        webView.clearFormData()
                        webView.loadUrl("about:blank")
                        currentDisplayUrl = "about:blank"
                        activeTab.url = "about:blank"
                        activeTab.title = "New Tab"
                        urlInput = ""
                    }
                }
                is BrowserCommand.ClearCacheAndStorage -> {
                    MainActivity.clearWebViewData(context, activeTab.webView) {
                        activeTab.webView?.loadUrl("about:blank")
                        currentDisplayUrl = "about:blank"
                        activeTab.url = "about:blank"
                        activeTab.title = "New Tab"
                        urlInput = ""
                    }
                }
            }
            onClearBrowserCommand()
        }
    }

    Column(modifier = modifier.fillMaxSize().background(CpaBg)) {
        // 1. Ultra-Slim IP & Proxy Status Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .background(CpaCardElevated)
                .border(0.5.dp, CpaBorder.copy(alpha = 0.4f))
                .padding(horizontal = 6.dp, vertical = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // IP & WebRTC on the left
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = if (settings.proxyHost.isNotBlank()) CpaSuccess else CpaWarning,
                    modifier = Modifier.size(9.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "IP: $effectiveWebRtcIp",
                    color = CpaText,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )

                if (extractedInfo.countryCode.isNotBlank()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "[${extractedInfo.countryCode}]",
                        color = CpaPrimary,
                        fontSize = 7.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "WebRTC: ${settings.webrtcMode.uppercase()}",
                    color = if (settings.webrtcMode == "block") CpaError else CpaSuccess,
                    fontSize = 7.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Real-Time Page Perception & Countdown Indicator
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (automationState.isRunning) {
                    val catLabel = if (automationState.detectedCategoryAr.isNotBlank()) {
                        automationState.detectedCategoryAr
                    } else if (automationState.detectedPageCategory.isNotBlank()) {
                        automationState.detectedPageCategory
                    } else {
                        "جاري التحليل"
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(CpaPrimaryDim)
                            .border(0.5.dp, CpaPrimaryBorder, RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 0.5.dp)
                    ) {
                        Text(
                            text = "🧠 $catLabel (${reanalysisCountdown}s)",
                            color = CpaPrimary,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                }

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

        // 2. Multi-Tab Navigation Bar (Tabs Bar)
        val tabScrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CpaCardElevated)
                .border(0.5.dp, CpaBorder)
                .horizontalScroll(tabScrollState)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                val isActive = tab.id == activeTabId
                val tabBg = if (isActive) CpaCard else CpaBg.copy(alpha = 0.6f)
                val tabBorder = if (isActive) CpaPrimary else CpaBorder.copy(alpha = 0.4f)
                val tabTextColor = if (isActive) CpaPrimary else CpaTextMuted

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(tabBg)
                        .border(1.dp, tabBorder, RoundedCornerShape(4.dp))
                        .clickable {
                            activeTabId = tab.id
                            onActiveTabChanged?.invoke(tab.id)
                        }
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = if (isActive) CpaPrimary else CpaTextDim,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))

                    val displayTitle = if (tab.title.isNotBlank()) tab.title else "Tab ${index + 1}"
                    Text(
                        text = displayTitle.take(15),
                        color = tabTextColor,
                        fontSize = 10.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Funnel Stage Badge if available
                    if (tab.stageBadge.isNotBlank()) {
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "[${tab.stageBadge}]",
                            color = CpaWarning,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(5.dp))

                    // Close Tab Action
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .clickable {
                                if (tabs.size > 1) {
                                    val closingId = tab.id
                                    val closingIndex = tabs.indexOfFirst { it.id == closingId }
                                    tab.webView?.destroy()
                                    tabs.remove(tab)
                                    if (activeTabId == closingId) {
                                        val nextIndex = closingIndex.coerceAtMost(tabs.size - 1)
                                        activeTabId = tabs[nextIndex].id
                                        onActiveTabChanged?.invoke(activeTabId)
                                    }
                                } else {
                                    // Reset single tab
                                    tab.title = "Main Tab"
                                    tab.url = "about:blank"
                                    tab.webView?.loadUrl("about:blank")
                                    currentDisplayUrl = "about:blank"
                                    urlInput = ""
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Tab",
                            tint = if (isActive) CpaError else CpaTextDim,
                            modifier = Modifier.size(9.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            // New Tab "+" Button
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CpaCard)
                    .border(1.dp, CpaBorder, RoundedCornerShape(4.dp))
                    .clickable {
                        val newId = "tab_${System.currentTimeMillis()}"
                        val newTab = BrowserTabModel(
                            id = newId,
                            title = "Tab ${tabs.size + 1}",
                            url = "about:blank"
                        )
                        tabs.add(newTab)
                        activeTabId = newId
                        onActiveTabChanged?.invoke(newId)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Tab",
                    tint = CpaPrimary,
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        // 3. Navigation & URL Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CpaCard)
                .border(1.dp, CpaBorder)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { activeTab.webView?.let { if (it.canGoBack()) it.goBack() } },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CpaText, modifier = Modifier.size(14.dp))
            }

            IconButton(
                onClick = { activeTab.webView?.let { if (it.canGoForward()) it.goForward() } },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward", tint = CpaText, modifier = Modifier.size(14.dp))
            }

            IconButton(
                onClick = { activeTab.webView?.reload() },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = CpaText, modifier = Modifier.size(14.dp))
            }

            if (currentDisplayUrl.isNotBlank() && currentDisplayUrl != "about:blank") {
                IconButton(
                    onClick = {
                        urlInput = ""
                        currentDisplayUrl = "about:blank"
                        activeTab.url = "about:blank"
                        activeTab.title = "New Tab"
                        activeTab.webView?.stopLoading()
                        activeTab.webView?.loadUrl("about:blank")
                        MainActivity.clearWebViewData(context, activeTab.webView)
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close Page & Clear", tint = CpaError, modifier = Modifier.size(14.dp))
                }
            }

            Spacer(modifier = Modifier.width(3.dp))

            // URL Input Container
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
                                activeTab.url = target
                                activeTab.webView?.loadUrl(target)
                            }
                        }
                    ),
                    cursorBrush = SolidColor(CpaPrimary),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // GO button
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
                        activeTab.url = target
                        activeTab.webView?.loadUrl(target)
                    }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("GO", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }

        // 4. Quick Verification & Perception Bar
        val leakScrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CpaCardElevated)
                .horizontalScroll(leakScrollState)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Immediate Perception Analysis Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(CpaPrimaryDim)
                    .border(1.dp, CpaPrimaryBorder, RoundedCornerShape(3.dp))
                    .clickable {
                        activeTab.webView?.evaluateJavascript(
                            AutomationScriptBuilder.buildPageAnalyzerScript(clickTexts, activeClickText ?: automationState.activeClickText),
                            null
                        )
                        Toast.makeText(context, "جاري إعادة فحص وتحليل DOM الصفحة...", Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text("⚡ إعادة تحليل الآن", color = CpaPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
                        activeTab.url = target
                        activeTab.webView?.loadUrl(target)
                    }
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text("Test WebRTC", color = CpaText, fontSize = 9.sp)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(CpaCard)
                    .border(1.dp, CpaBorder, RoundedCornerShape(3.dp))
                    .clickable {
                        val target = "https://browserleaks.com/canvas"
                        urlInput = target
                        currentDisplayUrl = target
                        activeTab.url = target
                        activeTab.webView?.loadUrl(target)
                    }
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text("Canvas Noise", color = CpaText, fontSize = 9.sp)
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
                        activeTab.url = target
                        activeTab.webView?.loadUrl(target)
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
                        activeTab.url = target
                        activeTab.webView?.loadUrl(target)
                    }
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text("api.ipify.org", color = CpaTextMuted, fontSize = 9.sp)
            }

            // On-demand Purge Cache, Cookies & Storage
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(CpaError.copy(alpha = 0.15f))
                    .border(1.dp, CpaError.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                    .clickable {
                        MainActivity.clearWebViewData(context, activeTab.webView) {
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

        // Multi-Tab WebViews Container
        // Each tab retains its own distinct WebView instance in memory.
        // Background tabs are kept hidden (View.GONE) to save GPU/rendering cycles.
        // Automated scripts and interactions ONLY target the active tab!
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            tabs.forEach { tabItem ->
                val isThisTabActive = tabItem.id == activeTabId

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = if (isThisTabActive) 1f else 0f
                            translationY = if (isThisTabActive) 0f else 99999f
                        }
                ) {
                    AndroidView(
                        factory = { ctx ->
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
                                    setSupportMultipleWindows(false) // Open all popups inside the current tab
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

                                    safeBrowsingEnabled = false
                                    setGeolocationEnabled(false)
                                    allowFileAccess = false
                                    allowContentAccess = false
                                    mediaPlaybackRequiresUserGesture = false
                                }

                                if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
                                    try {
                                        WebSettingsCompat.setSafeBrowsingEnabled(this.settings, false)
                                    } catch (e: Exception) {}
                                }

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
                                    } catch (e: Exception) {}
                                }

                                addJavascriptInterface(
                                    WebAppInterface(
                                        onCompleted = { kw, pageUrl ->
                                            onNotifyCompletion(kw, pageUrl)
                                        },
                                        onOfferClicked = { txt, pageUrl ->
                                            onOfferClicked?.invoke(txt, pageUrl)
                                        },
                                        onPageAnalyzed = { reportJson ->
                                            try {
                                                val report = TaskCategoryPlanner.parseAnalysisReport(reportJson)
                                                tabItem.stageBadge = report.detectedCategoryAr.take(8)
                                            } catch (e: Exception) {}
                                            onPageAnalyzed?.invoke(reportJson)
                                        }
                                    ),
                                    "AndroidBridge"
                                )

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        val p = newProgress / 100f
                                        tabItem.progress = p
                                        tabItem.isLoading = newProgress < 100
                                        if (tabItem.id == activeTabId) {
                                            webProgress = p
                                            isPageLoading = newProgress < 100
                                        }
                                    }

                                    override fun onReceivedTitle(view: WebView?, title: String?) {
                                        super.onReceivedTitle(view, title)
                                        if (!title.isNullOrBlank() && !title.startsWith("http")) {
                                            tabItem.title = title
                                        }
                                    }

                                    override fun onPermissionRequest(request: PermissionRequest?) {
                                        try { request?.deny() } catch (e: Exception) {}
                                    }

                                    override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                                        try { callback?.invoke(origin, true, false) } catch (e: Exception) {}
                                    }
                                }

                                webViewClient = object : MainActivity.CustomWebViewClient(
                                    proxyUserProvider = { settings.proxyUser },
                                    proxyPassProvider = { settings.proxyPass },
                                    onPageStartedCallback = { view, url, _ ->
                                        tabItem.isLoading = true
                                        url?.let {
                                            tabItem.url = it
                                            if (tabItem.id == activeTabId) {
                                                currentDisplayUrl = it
                                                urlInput = it
                                                isPageLoading = true
                                            }
                                        }

                                        // WebRTC and anti-detection script injection
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

                                        scripts.filter { it.enabled && it.timing == "before" }.forEach { s ->
                                            view?.evaluateJavascript(s.code, null)
                                        }
                                    },
                                    onPageFinishedCallback = { view, url ->
                                        tabItem.isLoading = false
                                        url?.let {
                                            tabItem.url = it
                                            if (tabItem.id == activeTabId) {
                                                currentDisplayUrl = it
                                                urlInput = it
                                                isPageLoading = false
                                            }
                                        }

                                        // Reinforced anti-detection script injection
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

                                        // Trigger page analysis immediately upon finish
                                        view?.evaluateJavascript(
                                            AutomationScriptBuilder.buildPageAnalyzerScript(clickTexts, activeClickText ?: automationState.activeClickText),
                                            null
                                        )

                                        // If automation is running and this is the ACTIVE tab, trigger smart automation sequence
                                        if (automationState.isRunning && tabItem.id == activeTabId) {
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
                                            view?.evaluateJavascript(AutomationScriptBuilder.buildHumanBehaviorScript(), null)

                                            val keywords = listOf("thank you", "congratulations", "success", "completed", "verified", "confirmed", "survey", "reward")
                                            view?.evaluateJavascript(AutomationScriptBuilder.buildCompletionDetectorScript(keywords), null)
                                        }

                                        scripts.filter { it.enabled && it.timing == "after" }.forEach { s ->
                                            view?.evaluateJavascript(s.code, null)
                                        }
                                    },
                                    onErrorCallback = { _, request, _ ->
                                        if (request?.isForMainFrame == true) {
                                            tabItem.isLoading = false
                                            if (tabItem.id == activeTabId) isPageLoading = false
                                        }
                                    }
                                ) {
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val uri = request?.url ?: return false
                                        val scheme = uri.scheme?.lowercase() ?: ""

                                        if (MainActivity.CustomWebViewClient.isWebRtcStunTurnTraffic(uri, request.isForMainFrame)) {
                                            return true
                                        }

                                        if (scheme == "http" || scheme == "https") {
                                            return false
                                        }

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

                                if (tabItem.url.isNotBlank() && tabItem.url != "about:blank") {
                                    loadUrl(tabItem.url)
                                } else {
                                    loadUrl("about:blank")
                                }
                                tabItem.webView = this
                            }
                        },
                        update = { webView ->
                            tabItem.webView = webView
                            webView.visibility = if (isThisTabActive) View.VISIBLE else View.GONE
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Standby Overlay when browser is idle and no page is loaded in active tab
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
                            text = "SMART MULTI-TAB BROWSER",
                            color = CpaText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Multi-Tab isolation active. All automated scripts and 10s perception engine interact exclusively with the currently opened tab.",
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
                                    activeTab.url = target
                                    activeTab.webView?.loadUrl(target)
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
                                    activeTab.url = target
                                    activeTab.webView?.loadUrl(target)
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
