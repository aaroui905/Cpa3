package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.AppSettings
import com.example.data.model.AutomationState
import com.example.data.model.CampaignStat
import com.example.data.model.EmailItem
import com.example.data.model.ExtractedInfo
import com.example.data.model.GeneratedIdentity
import com.example.data.model.LogEntry
import com.example.data.model.ProxyItem
import com.example.data.model.ScriptItem
import com.example.data.model.TaskEntity
import com.example.service.AutomationScriptBuilder
import com.example.service.ExtractedPlanResult
import com.example.service.IdentityService
import com.example.service.TaskCategoryPlanner
import com.example.util.WebProxyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.UUID

enum class ScreenTab(val id: String, val title: String, val titleAr: String = "") {
    TASKS("tasks", "Tasks", "المهام"),
    BROWSER("browser", "Browser", "المتصفح"),
    CLICK("click", "Offer Click", "شاشة النقرة"),
    INFO("info", "Identity", "الهوية"),
    PROXIES("proxies", "Proxies", "البروكسي"),
    SCRIPTS("scripts", "Scripts", "السكربتات"),
    EMAILS("emails", "Emails", "الإيميلات"),
    STATS("stats", "Analytics", "الإحصائيات"),
    SETTINGS("settings", "Settings", "الإعدادات"),
    LOGS("logs", "Logs", "السجلات")
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val taskDao = db.taskDao()
    private val emailDao = db.emailDao()
    private val scriptDao = db.scriptDao()
    private val leadLogDao = db.leadLogDao()
    private val proxyDao = db.proxyDao()
    private val offerClickDao = db.offerClickDao()

    private val prefs = application.getSharedPreferences("cpa_automator_prefs", Context.MODE_PRIVATE)

    // Navigation Tab
    private val _currentTab = MutableStateFlow(ScreenTab.TASKS)
    val currentTab: StateFlow<ScreenTab> = _currentTab.asStateFlow()

    // Automation State
    private val _automationState = MutableStateFlow(AutomationState())
    val automationState: StateFlow<AutomationState> = _automationState.asStateFlow()

    // Logs
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    // Extracted Geo Info
    private val _extractedInfo = MutableStateFlow(ExtractedInfo())
    val extractedInfo: StateFlow<ExtractedInfo> = _extractedInfo.asStateFlow()

    // Generated Identity
    private val _identity = MutableStateFlow(IdentityService.generateIdentity())
    val identity: StateFlow<GeneratedIdentity> = _identity.asStateFlow()

    // Settings
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    // Flows from DB
    val tasks: StateFlow<List<TaskEntity>> = taskDao.getAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val emails: StateFlow<List<EmailItem>> = emailDao.getAllEmails()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val emailCount: StateFlow<Int> = emailDao.getEmailCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val proxies: StateFlow<List<ProxyItem>> = proxyDao.getAllProxies()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val proxyCount: StateFlow<Int> = proxyDao.getProxyCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val scripts: StateFlow<List<ScriptItem>> = scriptDao.getAllScripts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stats: StateFlow<List<CampaignStat>> = leadLogDao.getAllLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val offerClickItems: StateFlow<List<com.example.data.model.OfferClickItem>> = offerClickDao.getAllClickItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val enabledClickItems: StateFlow<List<com.example.data.model.OfferClickItem>> = offerClickDao.getEnabledClickItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Browser navigation event channel
    private val _browserCommand = MutableStateFlow<BrowserCommand?>(null)
    val browserCommand: StateFlow<BrowserCommand?> = _browserCommand.asStateFlow()

    // Completion callback flag from Smart Mode
    private var completionReceivedForCurrentTask: Boolean = false

    private var automationJob: Job? = null

    init {
        addLog("info", "CPA Automator initialized and ready.")
        val initSettings = _settings.value
        val initPort = initSettings.proxyPort.toIntOrNull()
        WebProxyManager.applyProxy(application, initSettings.proxyHost, initPort, initSettings.proxyType, initSettings.proxyUser, initSettings.proxyPass) { success, msg ->
            addLog(if (success) "info" else "warning", "[ProxyController] $msg")
        }
        refreshGeoInfo()
        viewModelScope.launch(Dispatchers.IO) {
            val allTasks = taskDao.getEnabledTasks()
            val hasCtc = allTasks.any { it.url.contains("consumertestconnect") }
            if (!hasCtc) {
                val ctcTask = TaskEntity(
                    id = "task_ctc_100gc",
                    name = "ConsumerTestConnect ($100 GC)",
                    url = "https://consumertestconnect.com/ctc-100gcsweep",
                    referer = "https://www.google.com",
                    userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                    mode = "mode1",
                    repeatCount = 3,
                    browserDuration = 45,
                    categories = "Email Submit, Terms Agreement, Survey / Quiz, Lead Gen Form, Skip Upsells, Confirmation",
                    completionKeywords = "thank you, congratulations, success, confirmed, sweepstakes, completed",
                    enabled = true
                )
                taskDao.insertTask(ctcTask)
                addLog("info", "Loaded ConsumerTestConnect ($100 GC) optimized automation plan.")
            }

            // Ensure GDFQO Blogspot Offer Landing Bridge task exists (Priority #1)
            val fullTaskList = taskDao.getAllTasksList()
            if (fullTaskList.none { it.url.contains("gdfqo.blogspot.com") }) {
                val blogspotTask = TaskEntity(
                    id = "task_gdfqo_blogspot",
                    name = "GDFQO Offer Landing Bridge (Priority #1)",
                    url = "https://gdfqo.blogspot.com",
                    referer = "https://www.google.com",
                    userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                    mode = "mode1",
                    repeatCount = 5,
                    browserDuration = 60,
                    categories = "Offer Click, Survey / Quiz, Email Submit, Lead Gen Form, Skip Upsells, Confirmation",
                    completionKeywords = "thank you, congratulations, success, confirmed, reward, sweepstakes, completed",
                    enabled = true
                )
                taskDao.insertTask(blogspotTask)
                addLog("info", "Loaded GDFQO Landing Bridge task with Priority #1 Offer Click.")
            }

            // Ensure BrowserLeaks IP & WebRTC verification task exists
            if (fullTaskList.none { it.url.contains("browserleaks.com/ip") }) {
                val browserLeaksTask = TaskEntity(
                    id = "task_browserleaks_ip",
                    name = "BrowserLeaks IP & WebRTC Audit",
                    url = "https://browserleaks.com/ip",
                    referer = "https://www.google.com",
                    userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                    mode = "mode1",
                    repeatCount = 1,
                    browserDuration = 30,
                    categories = "Proxy Audit, WebRTC Leak Test, Fingerprint Validation",
                    completionKeywords = "WebRTC, IP Address, Leak Test",
                    enabled = true
                )
                taskDao.insertTask(browserLeaksTask)
                addLog("info", "Loaded BrowserLeaks IP & WebRTC audit task.")
            }

            // Ensure Canvas Fingerprint Noise script is active in scripts registry
            val activeScripts = scriptDao.getActiveScripts()
            if (activeScripts.none { it.id == "script_canvas_noise" }) {
                val canvasScript = ScriptItem(
                    id = "script_canvas_noise",
                    name = "Canvas Fingerprint Randomized Noise",
                    timing = "before",
                    execMode = "sequential",
                    code = AutomationScriptBuilder.buildCanvasNoiseScript(),
                    enabled = true,
                    isSystemPreset = true
                )
                scriptDao.insertScript(canvasScript)
                addLog("info", "Canvas API randomized noise anti-fingerprinting script registered.")
            }

            // Ensure Offer Click texts are seeded if table is currently empty
            val clickItems = offerClickDao.getAllClickItemsList()
            if (clickItems.isEmpty()) {
                val defaults = listOf(
                    com.example.data.model.OfferClickItem(text = "Get \$1000 Walmart gift card", enabled = true, orderIndex = 0, tagOrNote = "Walmart $1000 GC Offer"),
                    com.example.data.model.OfferClickItem(text = "Claim \$750 Cash App Reward", enabled = true, orderIndex = 1, tagOrNote = "Cash App Reward"),
                    com.example.data.model.OfferClickItem(text = "Get \$500 Amazon Gift Card", enabled = true, orderIndex = 2, tagOrNote = "Amazon $500 Sweep"),
                    com.example.data.model.OfferClickItem(text = "Win \$100 Target Gift Card", enabled = true, orderIndex = 3, tagOrNote = "Target Gift Card"),
                    com.example.data.model.OfferClickItem(text = "Claim \$500 Apple Store Card", enabled = false, orderIndex = 4, tagOrNote = "Apple Store Voucher")
                )
                offerClickDao.insertClickItems(defaults)
                addLog("info", "🎯 [شاشة النقرة]: تم تجهيز نصوص النقرة الافتراضية.")
            }

            // Automatically populate proxy pool: ensure user's default US proxy exists
            val currentProxyCount = proxyDao.getProxyCountOnce()
            if (currentProxyCount == 0) {
                val userProxy = com.example.data.model.ProxyItem(
                    host = "185.100.232.75",
                    port = 9999,
                    type = "socks5",
                    username = "bqjcykpcvw-a2c12283-3e44-4012-b6c0-7a476bef9351",
                    password = "DKiwjdv40s2tG3Jk",
                    country = "US",
                    status = "working",
                    lastPingMs = 120L
                )
                proxyDao.insertProxy(userProxy)
                val targetUrl = _settings.value.proxyListUrl.ifBlank { IdentityService.DEFAULT_ASOCKS_URL }
                val result = IdentityService.fetchProxiesFromUrl(targetUrl, "socks5")
                if (result.isSuccess) {
                    val list = result.getOrNull() ?: emptyList()
                    if (list.isNotEmpty()) {
                        proxyDao.insertProxies(list)
                        addLog("success", "Loaded ${list.size} Asocks US proxies into pool. Default active: 185.100.232.75:9999")
                    }
                }
            }
        }
    }

    fun selectTab(tab: ScreenTab) {
        _currentTab.value = tab
    }

    fun addLog(level: String, message: String, taskName: String? = null) {
        val entry = LogEntry(level = level, message = message, taskName = taskName)
        _logs.update { listOf(entry) + it.take(250) }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    // --- Task Operations ---

    fun saveTask(task: TaskEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            taskDao.insertTask(task)
            addLog("info", "Task saved: ${task.name}")
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            taskDao.deleteTask(task)
            addLog("warning", "Task deleted: ${task.name}")
        }
    }

    fun toggleTaskEnabled(task: TaskEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = task.copy(enabled = !task.enabled)
            taskDao.updateTask(updated)
        }
    }

    // --- Email Pool Operations ---

    fun addEmail(email: String) {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            emailDao.insertEmails(listOf(EmailItem(email = trimmed)))
            addLog("info", "Added email: $trimmed")
        }
    }

    fun importEmailsBulk(raw: String) {
        val lines = raw.split("\n", ",", ";")
            .map { it.trim() }
            .filter { it.contains("@") && it.contains(".") }
        if (lines.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            emailDao.insertEmails(lines.map { EmailItem(email = it) })
            addLog("success", "Imported ${lines.size} emails to pool.")
        }
    }

    fun generateTestEmails(count: Int = 10) {
        val domains = listOf("gmail.com", "outlook.com", "yahoo.com", "icloud.com")
        val generated = (1..count).map {
            val ident = IdentityService.generateIdentity()
            val domain = domains.random()
            "${ident.firstName.lowercase()}.${ident.lastName.lowercase()}${System.currentTimeMillis() % 1000}@$domain"
        }
        viewModelScope.launch(Dispatchers.IO) {
            emailDao.insertEmails(generated.map { EmailItem(email = it) })
            addLog("info", "Generated $count test emails into pool.")
        }
    }

    fun deleteEmail(email: EmailItem) {
        viewModelScope.launch(Dispatchers.IO) {
            emailDao.deleteEmail(email)
        }
    }

    fun clearAllEmails() {
        viewModelScope.launch(Dispatchers.IO) {
            emailDao.clearAllEmails()
            addLog("warning", "Cleared email pool.")
        }
    }

    // --- Proxy Pool Operations ---

    fun fetchProxiesFromUrl(
        url: String,
        protocol: String = "socks5",
        onResult: (Boolean, String, Int) -> Unit
    ) {
        viewModelScope.launch {
            val cleanUrl = url.trim()
            addLog("info", "Fetching proxies from URL: $cleanUrl")
            val result = IdentityService.fetchProxiesFromUrl(cleanUrl, protocol)
            if (result.isSuccess) {
                val list = result.getOrNull() ?: emptyList()
                withContext(Dispatchers.IO) {
                    proxyDao.insertProxies(list)
                }
                if (list.isNotEmpty()) {
                    val first = list.first()
                    val s = _settings.value
                    updateSettings(
                        s.copy(
                            proxyListUrl = cleanUrl,
                            proxyHost = first.host,
                            proxyPort = first.port.toString(),
                            proxyType = first.type,
                            proxyUser = first.username,
                            proxyPass = first.password
                        )
                    )
                    addLog("success", "Active proxy set to #${1} ${first.host}:${first.port} [${first.type.uppercase()}]")
                    refreshGeoInfo()
                }
                addLog("success", "Successfully loaded ${list.size} proxies from URL into pool.")
                onResult(true, "Successfully imported ${list.size} proxies", list.size)
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Unknown error fetching proxies"
                addLog("error", "Failed to fetch proxies: $err")
                onResult(false, err, 0)
            }
        }
    }

    fun importProxiesBulk(raw: String, protocol: String = "socks5", onResult: ((Int) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = IdentityService.parseBulkProxies(raw, protocol)
            if (list.isNotEmpty()) {
                proxyDao.insertProxies(list)
                addLog("success", "Imported ${list.size} proxies into pool.")
                onResult?.invoke(list.size)
            } else {
                onResult?.invoke(0)
            }
        }
    }

    fun addSingleProxy(proxy: ProxyItem, makeActive: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            proxyDao.insertProxy(proxy)
            addLog("info", "Added proxy: ${proxy.host}:${proxy.port} [${proxy.type.uppercase()}]")
            if (makeActive) {
                withContext(Dispatchers.Main) {
                    setActiveProxy(proxy)
                }
            }
        }
    }

    fun deleteProxy(proxy: ProxyItem) {
        viewModelScope.launch(Dispatchers.IO) {
            proxyDao.deleteProxy(proxy)
            addLog("info", "Deleted proxy: ${proxy.host}:${proxy.port}")
        }
    }

    fun clearAllProxies() {
        viewModelScope.launch(Dispatchers.IO) {
            proxyDao.clearAllProxies()
            addLog("warning", "Cleared all proxies from pool.")
        }
    }

    fun setActiveProxy(proxy: ProxyItem) {
        val s = _settings.value.copy(
            proxyType = proxy.type,
            proxyHost = proxy.host,
            proxyPort = proxy.port.toString(),
            proxyUser = proxy.username,
            proxyPass = proxy.password
        )
        updateSettings(s)
        addLog("success", "Switched active proxy to ${proxy.host}:${proxy.port} (${proxy.type.uppercase()})")
        refreshGeoInfo()
    }

    fun testProxy(proxy: ProxyItem, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                IdentityService.testProxyConnection(
                    host = proxy.host,
                    port = proxy.port,
                    type = proxy.type,
                    user = proxy.username,
                    pass = proxy.password,
                    timeoutMs = 9000
                )
            }
            val isWorking = res.first
            val details = res.second
            val ping = res.third
            withContext(Dispatchers.IO) {
                proxyDao.updateProxyStatus(proxy.id, if (isWorking) "working" else "failed", ping)
            }
            if (isWorking) {
                addLog("success", "Proxy ${proxy.host}:${proxy.port} ONLINE! Exit: $details - ${ping}ms")
                onResult(true, "Online: $details (${ping}ms)")
            } else {
                addLog("error", "Proxy ${proxy.host}:${proxy.port} failed: $details")
                onResult(false, "Failed: $details (${ping}ms)")
            }
        }
    }

    fun testProxyDetails(
        host: String,
        port: Int,
        type: String,
        user: String = "",
        pass: String = "",
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                IdentityService.testProxyConnection(host, port, type, user, pass, timeoutMs = 9000)
            }
            if (res.first) {
                onResult(true, "Working: ${res.second} - ${res.third}ms")
            } else {
                onResult(false, "Failed: ${res.second}")
            }
        }
    }

    fun testAllProxies(
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        onComplete: (Int, Int) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { proxyDao.getAllProxiesList() }
            if (list.isEmpty()) {
                onComplete(0, 0)
                return@launch
            }
            addLog("info", "بدء فحص مجموعة البروكسيات (${list.size} بروكسي) بسرعة متوازية...")
            val total = list.size
            val progressCount = java.util.concurrent.atomic.AtomicInteger(0)
            val workingCount = java.util.concurrent.atomic.AtomicInteger(0)
            val failedCount = java.util.concurrent.atomic.AtomicInteger(0)

            val semaphore = Semaphore(5)

            withContext(Dispatchers.IO) {
                val jobs = list.map { proxy ->
                    async {
                        semaphore.withPermit {
                            val res = IdentityService.testProxyConnection(
                                host = proxy.host,
                                port = proxy.port,
                                type = proxy.type,
                                user = proxy.username,
                                pass = proxy.password,
                                timeoutMs = 8000
                            )
                            val isWorking = res.first
                            val ping = res.third

                            proxyDao.updateProxyStatus(
                                id = proxy.id,
                                status = if (isWorking) "working" else "failed",
                                ping = ping
                            )

                            val done = progressCount.incrementAndGet()
                            if (isWorking) {
                                workingCount.incrementAndGet()
                                addLog("success", "[#$done/$total] ✅ ${proxy.host}:${proxy.port} ONLINE (${res.second}) - ${ping}ms")
                            } else {
                                failedCount.incrementAndGet()
                                addLog("warning", "[#$done/$total] ❌ ${proxy.host}:${proxy.port} OFFLINE (${res.second})")
                            }
                            withContext(Dispatchers.Main) {
                                onProgress(done, total)
                            }
                        }
                    }
                }
                jobs.awaitAll()
            }

            val finalWorking = workingCount.get()
            val finalFailed = failedCount.get()
            addLog("info", "اكتمل فحص البروكسيات: $finalWorking شغالة ✅ | $finalFailed معطلة ❌")
            onComplete(finalWorking, finalFailed)
        }
    }

    fun deleteFailedProxies(onResult: ((Int) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val deleted = proxyDao.deleteFailedProxies()
            addLog("info", "تم حذف $deleted بروكسي معطل من القائمة.")
            withContext(Dispatchers.Main) {
                onResult?.invoke(deleted)
            }
        }
    }

    fun autoSelectFastestProxy(onResult: ((ProxyItem?) -> Unit)? = null) {
        viewModelScope.launch {
            val fastest = withContext(Dispatchers.IO) {
                val working = proxyDao.getWorkingProxies()
                working.filter { it.lastPingMs > 0 }.minByOrNull { it.lastPingMs } ?: working.firstOrNull()
            }
            if (fastest != null) {
                setActiveProxy(fastest)
                addLog("success", "تم تفعيل أسرع بروكسي: ${fastest.host}:${fastest.port} (${fastest.lastPingMs}ms)")
            } else {
                addLog("warning", "لا توجد بروكسيات صالحة في القائمة حالياً. يرجى فحص البروكسيات أولاً.")
            }
            onResult?.invoke(fastest)
        }
    }

    fun getWorkingProxiesFormatted(): String {
        val list = proxies.value
        val working = list.filter { it.status == "working" }
        val targetList = if (working.isNotEmpty()) working else list
        return targetList.joinToString("\n") { p ->
            if (p.username.isNotBlank() && p.password.isNotBlank()) {
                "${p.host}:${p.port}:${p.username}:${p.password}"
            } else {
                "${p.host}:${p.port}"
            }
        }
    }

    // --- Scripts Operations ---

    fun saveScript(script: ScriptItem) {
        viewModelScope.launch(Dispatchers.IO) {
            scriptDao.insertScript(script)
            addLog("info", "Saved script: ${script.name}")
        }
    }

    fun toggleScript(script: ScriptItem) {
        viewModelScope.launch(Dispatchers.IO) {
            scriptDao.toggleScript(script.id, !script.enabled)
        }
    }

    fun deleteScript(script: ScriptItem) {
        viewModelScope.launch(Dispatchers.IO) {
            scriptDao.deleteScript(script)
            addLog("warning", "Deleted script: ${script.name}")
        }
    }

    // --- Offer Click Operations (شاشة النقرة - Priority #1) ---

    fun addOfferClickItem(text: String, tagOrNote: String = "") {
        if (text.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val maxOrder = offerClickDao.getMaxOrderIndex() ?: -1
            val item = com.example.data.model.OfferClickItem(
                text = text.trim(),
                enabled = true,
                orderIndex = maxOrder + 1,
                tagOrNote = tagOrNote.trim()
            )
            offerClickDao.insertClickItem(item)
            addLog("info", "🎯 [شاشة النقرة]: تمت إضافة نص نقرة جديد: '${item.text}' (الترتيب: #${item.orderIndex + 1})")
        }
    }

    fun updateOfferClickItem(item: com.example.data.model.OfferClickItem) {
        viewModelScope.launch(Dispatchers.IO) {
            offerClickDao.updateClickItem(item)
            addLog("info", "🎯 [شاشة النقرة]: تم تعديل نص النقرة: '${item.text}'")
        }
    }

    fun toggleOfferClickItem(id: Long, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            offerClickDao.toggleClickItem(id, enabled)
            val action = if (enabled) "تفعيل" else "غلق / إيقاف"
            addLog("info", "🎯 [شاشة النقرة]: تم $action نص النقرة بنجاح")
        }
    }

    fun deleteOfferClickItem(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = offerClickDao.getClickItemById(id)
            if (item != null) {
                offerClickDao.deleteClickItem(item)
                addLog("warning", "🎯 [شاشة النقرة]: تم حذف نص النقرة: '${item.text}'")
            }
        }
    }

    fun moveOfferClickItem(id: Long, direction: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val all = offerClickDao.getAllClickItemsList().sortedBy { it.orderIndex }
            val index = all.indexOfFirst { it.id == id }
            if (index == -1) return@launch
            val targetIndex = index + direction
            if (targetIndex in all.indices) {
                val current = all[index]
                val other = all[targetIndex]
                offerClickDao.updateOrderIndex(current.id, other.orderIndex)
                offerClickDao.updateOrderIndex(other.id, current.orderIndex)
                addLog("info", "🎯 [شاشة النقرة]: تم تغيير تسلسل النص '${current.text}'")
            }
        }
    }

    fun resetDefaultOfferClickItems() {
        viewModelScope.launch(Dispatchers.IO) {
            offerClickDao.clearAll()
            val defaults = listOf(
                com.example.data.model.OfferClickItem(text = "Get \$1000 Walmart gift card", enabled = true, orderIndex = 0, tagOrNote = "Walmart $1000"),
                com.example.data.model.OfferClickItem(text = "Claim \$750 Cash App Reward", enabled = true, orderIndex = 1, tagOrNote = "Cash App $750"),
                com.example.data.model.OfferClickItem(text = "Get \$500 Amazon Gift Card", enabled = true, orderIndex = 2, tagOrNote = "Amazon $500"),
                com.example.data.model.OfferClickItem(text = "Win \$100 Target Gift Card", enabled = true, orderIndex = 3, tagOrNote = "Target $100"),
                com.example.data.model.OfferClickItem(text = "Claim \$500 Apple Store Card", enabled = false, orderIndex = 4, tagOrNote = "Apple Card")
            )
            offerClickDao.insertClickItems(defaults)
            addLog("success", "🎯 [شاشة النقرة]: تمت استعادة نصوص النقرة الافتراضية بنجاح.")
        }
    }

    fun onOfferClicked(text: String, url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            offerClickDao.recordClickByText(text)
            _automationState.update {
                it.copy(
                    lastClickedOfferUrl = url,
                    activeClickText = text
                )
            }
            addLog("success", "🎯 [النقرة على العرض رقم 1]: تم النقر بنجاح على '$text' والتحول إلى: $url", _automationState.value.currentTaskName)
        }
    }

    fun setActiveTabId(tabId: String) {
        _automationState.update { it.copy(activeTabId = tabId) }
    }

    fun onPageAnalyzed(reportJson: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val report = TaskCategoryPlanner.parseAnalysisReport(reportJson)
            _automationState.update { state ->
                val currentCats = TaskCategoryPlanner.parseCategories(state.activeTaskCategories)
                val updatedCats = if (report.detectedCategory.isNotBlank() && report.detectedCategory != "general") {
                    val reordered = (listOf(report.detectedCategory) + currentCats.filter { !it.equals(report.detectedCategory, ignoreCase = true) }).distinct()
                    reordered.joinToString(", ")
                } else {
                    state.activeTaskCategories
                }

                state.copy(
                    detectedPageCategory = report.detectedCategory,
                    detectedCategoryAr = report.detectedCategoryAr,
                    pageAnalysisSummary = report.summary,
                    activeTaskCategories = updatedCats,
                    lastAnalysisTime = System.currentTimeMillis()
                )
            }

            addLog(
                "info",
                "🧠 [تحليل ذكي]: تم كشف مرحلة '${report.detectedCategoryAr}' (${report.detectedCategory}) - الثقة: ${report.confidence}% | الخطوة: ${report.recommendedNextAction}",
                _automationState.value.currentTaskName
            )

            // If confirmation/thank you detected, notify completion!
            if (report.isConfirmationPage && _automationState.value.isRunning) {
                completionReceivedForCurrentTask = true
                addLog("success", "🏆 [تأكيد التحويل]: تم رصد صفحة الشكر والإكمال بنجاح!", _automationState.value.currentTaskName)
            }
        }
    }

    fun testOfferClickInBrowser(text: String, landingUrl: String = "https://gdfqo.blogspot.com") {
        _automationState.update {
            it.copy(
                activeClickText = text,
                currentUrl = landingUrl
            )
        }
        _currentTab.value = ScreenTab.BROWSER
        _browserCommand.value = BrowserCommand.LoadUrl(landingUrl, "https://www.google.com", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
        addLog("info", "🎯 [شاشة النقرة]: جاري فتح صفحة الهبوط ($landingUrl) لاختبار النقر التلقائي على: '$text'")
    }

    // --- Identity Operations ---

    fun regenerateIdentity() {
        viewModelScope.launch(Dispatchers.Default) {
            val newIdent = IdentityService.generateIdentity(_extractedInfo.value.countryCode)
            _identity.value = newIdent
            addLog("info", "Generated new identity: ${newIdent.fullName} (${newIdent.email})")
        }
    }

    fun refreshGeoInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            addLog("info", "Querying Geo location info...")
            val s = _settings.value
            val port = s.proxyPort.toIntOrNull()
            WebProxyManager.applyProxy(getApplication(), s.proxyHost, port, s.proxyType, s.proxyUser, s.proxyPass) { success, msg ->
                addLog(if (success) "info" else "warning", "[ProxyController] $msg")
            }
            val geo = IdentityService.fetchGeoInfo(s.proxyHost, port, s.proxyType, s.proxyUser, s.proxyPass)
            _extractedInfo.value = geo
            _automationState.update { it.copy(activeIp = geo.ip) }
            addLog(if (geo.isProxy) "success" else "warning", "Active IP: ${geo.ip} (${geo.city}, ${geo.country})")
        }
    }

    // --- Settings Operations ---

    fun updateSettings(newSettings: AppSettings) {
        _settings.value = newSettings
        saveSettingsToPrefs(newSettings)
        val port = newSettings.proxyPort.toIntOrNull()
        WebProxyManager.applyProxy(getApplication(), newSettings.proxyHost, port, newSettings.proxyType, newSettings.proxyUser, newSettings.proxyPass) { success, msg ->
            addLog(if (success) "info" else "warning", "[ProxyController] $msg")
        }
        addLog("info", "Settings updated.")
    }

    private fun loadSettings(): AppSettings {
        return AppSettings(
            waitBetweenTasks = prefs.getInt("waitBetweenTasks", 5),
            cpaUserId = prefs.getString("cpaUserId", "") ?: "",
            cpaApiKey = prefs.getString("cpaApiKey", "") ?: "",
            captchaProvider = prefs.getString("captchaProvider", "none") ?: "none",
            captchaApiKey = prefs.getString("captchaApiKey", "") ?: "",
            proxyType = prefs.getString("proxyType", "socks5") ?: "socks5",
            proxyHost = prefs.getString("proxyHost", "185.100.232.75") ?: "185.100.232.75",
            proxyPort = prefs.getString("proxyPort", "9999") ?: "9999",
            proxyUser = prefs.getString("proxyUser", "bqjcykpcvw-a2c12283-3e44-4012-b6c0-7a476bef9351") ?: "bqjcykpcvw-a2c12283-3e44-4012-b6c0-7a476bef9351",
            proxyPass = prefs.getString("proxyPass", "DKiwjdv40s2tG3Jk") ?: "DKiwjdv40s2tG3Jk",
            proxyAutoRotate = prefs.getBoolean("proxyAutoRotate", false),
            proxyListUrl = prefs.getString("proxyListUrl", IdentityService.DEFAULT_ASOCKS_URL) ?: IdentityService.DEFAULT_ASOCKS_URL,
            webrtcMode = prefs.getString("webrtcMode", "spoof") ?: "spoof",
            webrtcCustomIp = prefs.getString("webrtcCustomIp", "") ?: "",
            forceProxyDns = prefs.getBoolean("forceProxyDns", true)
        )
    }

    private fun saveSettingsToPrefs(s: AppSettings) {
        prefs.edit().apply {
            putInt("waitBetweenTasks", s.waitBetweenTasks)
            putString("cpaUserId", s.cpaUserId)
            putString("cpaApiKey", s.cpaApiKey)
            putString("captchaProvider", s.captchaProvider)
            putString("captchaApiKey", s.captchaApiKey)
            putString("proxyType", s.proxyType)
            putString("proxyHost", s.proxyHost)
            putString("proxyPort", s.proxyPort)
            putString("proxyUser", s.proxyUser)
            putString("proxyPass", s.proxyPass)
            putBoolean("proxyAutoRotate", s.proxyAutoRotate)
            putString("proxyListUrl", s.proxyListUrl)
            putString("webrtcMode", s.webrtcMode)
            putString("webrtcCustomIp", s.webrtcCustomIp)
            putBoolean("forceProxyDns", s.forceProxyDns)
            apply()
        }
    }

    fun setForceProxyDns(enabled: Boolean) {
        val updated = _settings.value.copy(forceProxyDns = enabled)
        updateSettings(updated)
    }

    fun setWebRtcMode(mode: String) {
        val updated = _settings.value.copy(webrtcMode = mode)
        updateSettings(updated)
    }

    fun setWebRtcCustomIp(ip: String) {
        val updated = _settings.value.copy(webrtcCustomIp = ip)
        updateSettings(updated)
    }

    fun testCpaConnection(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val s = _settings.value
            if (s.cpaUserId.isBlank() || s.cpaApiKey.isBlank()) {
                onResult(false, "Please provide CPA Grip User ID & API Key")
                return@launch
            }
            val ip = _extractedInfo.value.ip
            val res = IdentityService.checkLeadCPA(s.cpaUserId, s.cpaApiKey, ip)
            onResult(res.first, res.second)
            addLog(if (res.first) "success" else "info", "CPA Grip API Test: ${res.second}")
        }
    }

    fun clearStats() {
        viewModelScope.launch(Dispatchers.IO) {
            leadLogDao.clearAllLogs()
            _automationState.update { it.copy(completedThisSession = 0, leadsThisSession = 0) }
            addLog("info", "Campaign statistics cleared.")
        }
    }

    // --- Browser Navigation Controls ---

    fun navigateBrowser(url: String) {
        _currentTab.value = ScreenTab.BROWSER
        _browserCommand.value = BrowserCommand.LoadUrl(url, "https://www.google.com", "random")
    }

    fun reloadBrowser() {
        _browserCommand.value = BrowserCommand.Reload
    }

    fun goBackBrowser() {
        _browserCommand.value = BrowserCommand.GoBack
    }

    fun goForwardBrowser() {
        _browserCommand.value = BrowserCommand.GoForward
    }

    fun clearBrowserCommand() {
        _browserCommand.value = null
    }

    // Callback called from AndroidBridge when smart completion keyword detected
    fun notifyTaskCompleted(keyword: String, url: String) {
        completionReceivedForCurrentTask = true
        addLog("success", "Smart completion confirmed via keyword '$keyword' on $url")
    }

    // --- AUTOMATION ENGINE ---

    fun toggleAutomation() {
        if (_automationState.value.isRunning) {
            stopAutomation()
        } else {
            startAutomation()
        }
    }

    fun runTaskNow(task: TaskEntity) {
        if (automationJob?.isActive == true) {
            stopAutomation()
        }
        _currentTab.value = ScreenTab.BROWSER
        automationJob = viewModelScope.launch(Dispatchers.IO) {
            _automationState.update {
                it.copy(
                    isRunning = true,
                    phase = "preparing",
                    phaseDetail = "Preparing ${task.name}...",
                    loopCount = 1
                )
            }
            addLog("info", "Starting task directly: ${task.name}", task.name)
            try {
                runSingleTask(task)
            } catch (e: Exception) {
                addLog("error", "Task execution error: ${e.localizedMessage}", task.name)
            } finally {
                _automationState.update {
                    it.copy(
                        isRunning = false,
                        phase = "completed",
                        phaseDetail = "Task completed. Browser page preserved.",
                        currentTaskId = null,
                        currentTaskName = null,
                        currentUrl = null
                    )
                }
                // Maintain the browser page so the user can inspect the final conversion / thank you screen!
                addLog("info", "Task finished. Browser conversion screen preserved.")
            }
        }
    }

    /**
     * Extracts and optimizes a conversion funnel plan from ANY URL.
     */
    fun extractPlanForUrl(url: String): ExtractedPlanResult {
        return TaskCategoryPlanner.extractFunnelPlanFromUrl(url)
    }

    /**
     * Instantly creates a task from an extracted plan and immediately launches it in the browser.
     */
    fun createAndRunExtractedTask(rawUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val plan = TaskCategoryPlanner.extractFunnelPlanFromUrl(rawUrl)
            val newTask = TaskEntity(
                id = UUID.randomUUID().toString(),
                name = plan.detectedName,
                url = plan.targetUrl,
                referer = plan.recommendedReferer,
                userAgent = IdentityService.USER_AGENTS[0].value,
                mode = plan.recommendedMode,
                repeatCount = 1,
                browserDuration = plan.recommendedDuration,
                completionKeywords = plan.recommendedKeywords,
                categories = plan.categories.joinToString(", "),
                enabled = true
            )
            taskDao.insertTask(newTask)
            addLog("success", "Extracted & created task: ${newTask.name} [Funnel: ${plan.summary}]")
            withContext(Dispatchers.Main) {
                runTaskNow(newTask)
            }
        }
    }

    fun startAutomation() {
        if (automationJob?.isActive == true) return
        _currentTab.value = ScreenTab.BROWSER

        automationJob = viewModelScope.launch(Dispatchers.IO) {
            _automationState.update {
                it.copy(
                    isRunning = true,
                    phase = "preparing",
                    phaseDetail = "Smart Auto: Preparing intelligent campaign...",
                    loopCount = 0
                )
            }

            // 1. Intelligent Task Resolution: ensure we have optimized tasks ready
            var tasksToRun = taskDao.getEnabledTasks()
            if (tasksToRun.isEmpty()) {
                val allExisting = taskDao.getAllTasksList()
                if (allExisting.isNotEmpty()) {
                    addLog("info", "Smart Auto: Automatically enabling ${allExisting.size} existing tasks.")
                    allExisting.forEach { taskDao.updateTaskEnabled(it.id, true) }
                    tasksToRun = taskDao.getEnabledTasks()
                } else {
                    addLog("info", "Smart Auto: Initializing high-converting default CPA offer funnels...")
                    TaskCategoryPlanner.PRESET_OFFER_PLANS.take(2).forEach { preset ->
                        val newTask = TaskEntity(
                            id = UUID.randomUUID().toString(),
                            name = preset.name,
                            url = preset.url,
                            referer = "https://www.google.com",
                            userAgent = IdentityService.USER_AGENTS[0].value,
                            mode = preset.mode,
                            repeatCount = 1,
                            browserDuration = preset.duration,
                            completionKeywords = preset.completionKeywords,
                            categories = preset.categories,
                            enabled = true
                        )
                        taskDao.insertTask(newTask)
                    }
                    tasksToRun = taskDao.getEnabledTasks()
                    addLog("success", "Smart Auto: Initialized ${tasksToRun.size} intelligent CPA tasks.")
                }
            }

            // 2. Ensure Geo & Persona are ready
            if (_extractedInfo.value.ip.isBlank() || _extractedInfo.value.ip == "127.0.0.1") {
                _automationState.update { it.copy(phase = "fetching_geo", phaseDetail = "Detecting IP & Geo info...") }
                val s = _settings.value
                val port = s.proxyPort.toIntOrNull()
                val geo = IdentityService.fetchGeoInfo(s.proxyHost, port, s.proxyType, s.proxyUser, s.proxyPass)
                _extractedInfo.value = geo
                _automationState.update { it.copy(activeIp = geo.ip) }
                if (_identity.value.fullName.isBlank() || _identity.value.fullName == "John Doe") {
                    _identity.value = IdentityService.generateIdentity(geo.countryCode)
                }
            }

            addLog("info", "Smart Campaign running: ${tasksToRun.size} tasks queued.")

            try {
                var loop = 0
                while (_automationState.value.isRunning) {
                    loop++
                    _automationState.update { it.copy(loopCount = loop) }

                    val currentTasks = taskDao.getEnabledTasks()
                    if (currentTasks.isEmpty()) break

                    for (rawTask in currentTasks) {
                        if (!_automationState.value.isRunning) break

                        // Auto-optimize task if missing plan categories
                        val task = if (rawTask.categories.isBlank() || rawTask.completionKeywords.isBlank()) {
                            val plan = TaskCategoryPlanner.extractFunnelPlanFromUrl(rawTask.url)
                            val optimized = rawTask.copy(
                                name = if (rawTask.name.isBlank() || rawTask.name.startsWith("Task ")) plan.detectedName else rawTask.name,
                                categories = if (rawTask.categories.isBlank()) plan.categories.joinToString(", ") else rawTask.categories,
                                mode = if (rawTask.mode.isBlank()) plan.recommendedMode else rawTask.mode,
                                browserDuration = if (rawTask.browserDuration <= 0) plan.recommendedDuration else rawTask.browserDuration,
                                completionKeywords = if (rawTask.completionKeywords.isBlank()) plan.recommendedKeywords else rawTask.completionKeywords,
                                referer = if (rawTask.referer.isBlank()) plan.recommendedReferer else rawTask.referer
                            )
                            taskDao.updateTask(optimized)
                            optimized
                        } else {
                            rawTask
                        }

                        // Check repeat limit
                        if (task.repeatCount > 0 && task.completedRuns >= task.repeatCount) {
                            addLog("info", "Task ${task.name} reached repeat limit (${task.repeatCount}). Skipping.")
                            continue
                        }

                        runSingleTask(task)

                        // Wait between tasks
                        if (_automationState.value.isRunning) {
                            val waitSec = _settings.value.waitBetweenTasks.coerceAtLeast(2)
                            for (w in waitSec downTo 1) {
                                if (!_automationState.value.isRunning) break
                                _automationState.update {
                                    it.copy(
                                        phase = "preparing",
                                        phaseDetail = "Cooling down... ($w s)"
                                    )
                                }
                                delay(1000)
                            }
                        }
                    }

                    // Check if all tasks finished
                    val freshTasks = taskDao.getEnabledTasks()
                    val allDone = freshTasks.all { it.repeatCount > 0 && it.completedRuns >= it.repeatCount }
                    if (allDone) {
                        addLog("success", "All tasks finished their repeat quota!")
                        break
                    }
                }
            } catch (e: Exception) {
                addLog("error", "Automation error: ${e.localizedMessage}")
            } finally {
                _automationState.update {
                    it.copy(
                        isRunning = false,
                        phase = "completed",
                        phaseDetail = "Smart Automation finished. Conversion screen preserved.",
                        currentTaskId = null,
                        currentTaskName = null,
                        currentUrl = null,
                        activeTaskCategories = "",
                        activePlanSummary = ""
                    )
                }
                addLog("info", "Smart Automation campaign finished. Browser page preserved.")
            }
        }
    }

    private suspend fun runSingleTask(task: TaskEntity) {
        taskDao.updateTaskStatus(task.id, "running")
        val parsedCats = TaskCategoryPlanner.parseCategories(task.categories)
        val planSummary = TaskCategoryPlanner.formatPlanSummary(parsedCats)
        _automationState.update {
            it.copy(
                currentTaskId = task.id,
                currentTaskName = task.name,
                currentUrl = task.url,
                activeTaskCategories = task.categories,
                activePlanSummary = planSummary,
                phase = "preparing",
                phaseDetail = "AI Funnel: $planSummary"
            )
        }
        addLog("info", "--- Starting task: ${task.name} [Funnel: $planSummary] ---", task.name)

        // 1. Proxy & Geo Info
        _automationState.update { it.copy(phase = "fetching_geo", phaseDetail = "Updating IP & Geo info...") }
        var s = _settings.value
        if (s.proxyAutoRotate) {
            val nextProxy = proxyDao.getNextWorkingProxy() ?: proxyDao.getNextProxy()
            if (nextProxy != null) {
                proxyDao.markProxyUsed(nextProxy.id)
                s = s.copy(
                    proxyType = nextProxy.type,
                    proxyHost = nextProxy.host,
                    proxyPort = nextProxy.port.toString(),
                    proxyUser = nextProxy.username,
                    proxyPass = nextProxy.password
                )
                _settings.value = s
                val rotatedPort = s.proxyPort.toIntOrNull()
                WebProxyManager.applyProxy(getApplication(), s.proxyHost, rotatedPort, s.proxyType, s.proxyUser, s.proxyPass) { success, msg ->
                    addLog(if (success) "info" else "warning", "[ProxyController] $msg", task.name)
                }
                addLog("info", "🔄 Rotated IP using proxy: ${nextProxy.host}:${nextProxy.port} [${nextProxy.type.uppercase()}]", task.name)
            }
        }
        val port = s.proxyPort.toIntOrNull()
        WebProxyManager.applyProxy(getApplication(), s.proxyHost, port, s.proxyType, s.proxyUser, s.proxyPass)
        val geo = IdentityService.fetchGeoInfo(s.proxyHost, port, s.proxyType, s.proxyUser, s.proxyPass)
        _extractedInfo.value = geo
        _automationState.update { it.copy(activeIp = geo.ip) }
        addLog("info", "Active IP: ${geo.ip} (${geo.city}, ${geo.country})", task.name)

        // 2. Identity Generation
        _automationState.update { it.copy(phase = "generating_identity", phaseDetail = "Generating profile identity...") }
        // Try to pop email from pool
        val poolEmail = emailDao.getNextEmail()
        if (poolEmail != null) {
            emailDao.deleteEmail(poolEmail)
            addLog("info", "Used email from pool: ${poolEmail.email}", task.name)
        }
        val identityData = IdentityService.generateIdentity(geo.countryCode, poolEmail?.email)
        _identity.value = identityData
        addLog("info", "Identity generated: ${identityData.fullName} | CC: ${identityData.cardNumber.take(9)}****", task.name)

        // 2.5. Offer Click Priority #1 Sequential Selection
        val enabledClicks = offerClickDao.getEnabledClickItemsList()
        val nextClickTarget = if (enabledClicks.isNotEmpty()) {
            val idx = _automationState.value.clickSequenceIndex % enabledClicks.size
            val chosen = enabledClicks[idx]
            _automationState.update {
                it.copy(
                    activeClickText = chosen.text,
                    clickSequenceIndex = (idx + 1) % enabledClicks.size
                )
            }
            addLog("info", "🎯 [النقرة على العرض رقم 1]: تم اختيار نص العرض بالتسلسل (#${idx + 1}): '${chosen.text}'", task.name)
            chosen.text
        } else {
            null
        }

        // 3. Launch in Browser
        _automationState.update {
            it.copy(
                phase = "browser",
                phaseDetail = if (nextClickTarget != null) "Navigating to ${task.url} [Target: $nextClickTarget]..." else "Navigating to ${task.url}..."
            )
        }

        // Determine user agent
        val ua = if (task.userAgent == "random" || task.userAgent.isBlank()) {
            IdentityService.USER_AGENTS.random().value
        } else {
            task.userAgent
        }

        // Issue browser load command
        _browserCommand.value = BrowserCommand.LoadUrl(task.url, task.referer, ua)

        // Execution based on Mode
        when (task.mode) {
            "mode1" -> runMode1(task)
            "mode2" -> runMode2(task, ua)
            "mode3" -> runMode3(task)
            else -> runMode1(task)
        }

        // 4. CPA Grip Lead Check
        if (s.cpaUserId.isNotBlank() && s.cpaApiKey.isNotBlank()) {
            _automationState.update { it.copy(phase = "checking_lead", phaseDetail = "Verifying conversion on CPA Grip...") }
            val leadResult = IdentityService.checkLeadCPA(s.cpaUserId, s.cpaApiKey, geo.ip)
            val isLead = leadResult.first
            leadLogDao.insertLog(
                CampaignStat(
                    taskId = task.id,
                    taskName = task.name,
                    ip = geo.ip,
                    country = geo.country,
                    leadDetected = isLead,
                    details = leadResult.second
                )
            )
            if (isLead) {
                _automationState.update { it.copy(leadsThisSession = it.leadsThisSession + 1) }
                addLog("success", "CONVERSION CONFIRMED: ${leadResult.second}", task.name)
            } else {
                addLog("info", "Conversion check: ${leadResult.second}", task.name)
            }
        } else {
            // Still record stat
            leadLogDao.insertLog(
                CampaignStat(
                    taskId = task.id,
                    taskName = task.name,
                    ip = geo.ip,
                    country = geo.country,
                    leadDetected = false,
                    details = "Task session completed."
                )
            )
        }

        // Increment task run count
        taskDao.incrementCompletedRuns(task.id)
        taskDao.updateTaskStatus(task.id, "completed")
        _automationState.update {
            it.copy(
                completedThisSession = it.completedThisSession + 1,
                phase = "completed",
                phaseDetail = "Completed run for ${task.name}. Page preserved."
            )
        }
        addLog("success", "Finished run for: ${task.name}. Conversion page preserved in Browser.", task.name)
    }

    private suspend fun runMode1(task: TaskEntity) {
        val duration = task.browserDuration.coerceAtLeast(5)
        for (sec in duration downTo 1) {
            if (!_automationState.value.isRunning) break
            _automationState.update {
                it.copy(
                    phase = "executing",
                    phaseDetail = "Browsing & filling forms... (${sec}s left)"
                )
            }
            delay(1000)
        }
    }

    private suspend fun runMode2(task: TaskEntity, ua: String) {
        val repeats = task.taskRepeatCount.coerceAtLeast(1)
        val durationPerRepeat = (task.taskDuration / repeats).coerceAtLeast(5)

        for (r in 1..repeats) {
            if (!_automationState.value.isRunning) break
            if (r > 1) {
                addLog("info", "Mode 2 repeat #$r: Reloading task in same session", task.name)
                _browserCommand.value = BrowserCommand.LoadUrl(task.url, task.referer, ua)
            }
            for (sec in durationPerRepeat downTo 1) {
                if (!_automationState.value.isRunning) break
                _automationState.update {
                    it.copy(
                        phase = "executing",
                        phaseDetail = "Mode 2 sub-run $r/$repeats (${sec}s left)"
                    )
                }
                delay(1000)
            }
        }
    }

    private suspend fun runMode3(task: TaskEntity) {
        completionReceivedForCurrentTask = false
        val maxWaitSec = 90
        val keywords = task.completionKeywords.split(",").map { it.trim() }
        addLog("info", "Mode 3 listening for completion keywords: ${keywords.take(4).joinToString(", ")}", task.name)

        for (sec in 1..maxWaitSec) {
            if (!_automationState.value.isRunning || completionReceivedForCurrentTask) break
            _automationState.update {
                it.copy(
                    phase = "executing",
                    phaseDetail = "Mode 3: Detecting completion keywords (${maxWaitSec - sec}s remaining)"
                )
            }
            delay(1000)
        }

        if (completionReceivedForCurrentTask) {
            addLog("success", "Smart Mode detected conversion page early!", task.name)
        } else {
            addLog("warning", "Smart Mode reached timeout waiting for keywords.", task.name)
        }
    }

    fun stopAutomation() {
        _automationState.update {
            it.copy(
                isRunning = false,
                phase = "idle",
                phaseDetail = "Stopping automation..."
            )
        }
        automationJob?.cancel()
        automationJob = null
        _browserCommand.value = BrowserCommand.ClearUrl
        addLog("warning", "Automation manually stopped by user.")
    }

    fun purgeSessionAndCache() {
        _browserCommand.value = BrowserCommand.ClearCacheAndStorage
        addLog("info", "Requested on-demand purge of WebView cache, cookies, and local storage.")
    }
}

sealed class BrowserCommand {
    data class LoadUrl(val url: String, val referer: String?, val userAgent: String?) : BrowserCommand()
    object Reload : BrowserCommand()
    object GoBack : BrowserCommand()
    object GoForward : BrowserCommand()
    object ClearUrl : BrowserCommand()
    object ClearCacheAndStorage : BrowserCommand()
}
