package com.example.data.model

data class AppSettings(
    val waitBetweenTasks: Int = 5,
    val cpaUserId: String = "",
    val cpaApiKey: String = "",
    val captchaProvider: String = "none", // none, 2captcha, anticaptcha, capsolver, deathbycaptcha
    val captchaApiKey: String = "",
    val proxyType: String = "none", // none, http, https, socks4, socks5
    val proxyHost: String = "",
    val proxyPort: String = "",
    val proxyUser: String = "",
    val proxyPass: String = "",
    val proxyAutoRotate: Boolean = false,
    val proxyListUrl: String = "",
    val webrtcMode: String = "spoof", // spoof, disabled, real
    val webrtcCustomIp: String = "",
    val forceProxyDns: Boolean = true // Force DNS resolution through proxy & prevent DNS leaks
)
