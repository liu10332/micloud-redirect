package com.micloud.redirect.hook

import android.content.Context
import android.util.Log
import com.micloud.redirect.ConfigManager
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 小米云备份 Hook 核心
 * 
 * Hook 策略：
 * 1. Hook API 基础 URL 常量类，替换为私有服务器地址
 * 2. Hook SSL 验证（如需要）
 */
class BackupHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "MiCloudRedirect"
        
        // 小米云备份 API 域名
        const val MICLOUD_BACKUP_HOST = "appbackupapi.micloud.xiaomi.net"
        const val MICLOUD_STATUS_HOST = "statusapi.micloud.xiaomi.net"
        const val MICLOUD_PDC_HOST = "pdc.micloud.xiaomi.net"
        const val MICLOUD_FILE_HOST = "fileapi.micloud.xiaomi.net"
        
        // 目标包名
        const val TARGET_PACKAGE = "com.miui.cloudbackup"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) return

        Log.i(TAG, "Hooking ${TARGET_PACKAGE}...")
        XposedBridge.log("[$TAG] Hooking ${TARGET_PACKAGE}...")

        try {
            // 初始化配置
            initConfig(lpparam)

            // Hook 方案 1: 替换 API URL 常量（最稳定）
            hookApiUrls(lpparam)

            // Hook 方案 2: Hook HTTP 请求发起点（兜底方案）
            hookHttpRequests(lpparam)

            // 如果用 HTTPS，需要禁用证书验证
            hookSslVerification(lpparam)

            XposedBridge.log("[$TAG] Hook 成功!")
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] Hook 失败: ${e.message}")
            Log.e(TAG, "Hook failed", e)
        }
    }

    /**
     * 初始化配置管理器
     */
    private fun initConfig(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Hook Application.onCreate 读取配置
        XposedHelpers.findAndHookMethod(
            "android.app.Application",
            lpparam.classLoader,
            "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val context = param.thisObject as Context
                    ConfigManager.init(context)
                    val baseUrl = ConfigManager.getBaseUrl()
                    XposedBridge.log("[$TAG] Config loaded: baseUrl=$baseUrl")
                }
            }
        )
    }

    /**
     * Hook 方案 1: 替换 API URL 常量
     * 
     * 目标类 (从逆向结果):
     * - p066N0.AbstractC0396f: 备份 API 基础 URL
     * - com.xiaomi.micloudsdk.utils.AbstractC1763h: PDC/Status URL
     */
    private fun hookApiUrls(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 尝试 hook 所有可能包含 URL 的静态字段
        val urlHosts = listOf(
            MICLOUD_BACKUP_HOST,
            MICLOUD_STATUS_HOST,
            MICLOUD_PDC_HOST,
            MICLOUD_FILE_HOST
        )

        // Hook Class.forName 拦截类加载
        XposedHelpers.findAndHookMethod(
            "java.lang.Class",
            lpparam.classLoader,
            "forName",
            String::class.java,
            Boolean::class.javaPrimitiveType,
            ClassLoader::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    // 不需要处理
                }
            }
        )

        // 更直接的方式：hook 所有 String 字段的静态初始化
        // 当类加载时，扫描静态字段中包含小米域名的字符串并替换
        hookStringConstants(lpparam)
    }

    /**
     * 扫描并替换所有静态字符串常量中的小米域名
     */
    private fun hookStringConstants(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 使用 loadPackage 后的时机，hook 特定的类
        // 从逆向结果，我们知道关键的类名格式
        
        val classNames = listOf(
            // 混淆后的类名（需要根据实际 APK 调整）
            // 这里我们用另一种方式：hook URL 构造过程
        )

        // 实际上，更可靠的方式是 hook URL/Uri 的解析
        hookUrlParsing(lpparam)
    }

    /**
     * Hook 方案 2: Hook HTTP 请求的 URL
     * 
     * 拦截所有 HTTP 请求，将小米域名替换为私有服务器
     */
    private fun hookHttpRequests(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Hook Apache HttpClient 的 execute 方法
            val httpClientClass = try {
                XposedHelpers.findClass(
                    "org.apache.http.impl.client.DefaultHttpClient",
                    lpparam.classLoader
                )
            } catch (e: Throwable) {
                null
            }

            httpClientClass?.let { clazz ->
                XposedHelpers.findAndHookMethod(
                    clazz,
                    "execute",
                    XposedHelpers.findClass("org.apache.http.client.methods.HttpUriRequest", lpparam.classLoader),
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val request = param.args[0]
                            val uri = XposedHelpers.callMethod(request, "getURI") as java.net.URI
                            val originalHost = uri.host

                            if (originalHost != null && isMicloudHost(originalHost)) {
                                val newUri = replaceUri(uri)
                                XposedBridge.log("[$TAG] Redirect: $uri -> $newUri")

                                // 替换 URI
                                XposedHelpers.callMethod(request, "setURI", newUri)
                            }
                        }
                    }
                )
                XposedBridge.log("[$TAG] Hooked DefaultHttpClient.execute")
            }
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] Failed to hook HttpClient: ${e.message}")
        }

        // 同时 hook HttpURLConnection
        try {
            XposedHelpers.findAndHookConstructor(
                "java.net.URL",
                lpparam.classLoader,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val urlStr = param.args[0] as String
                        if (urlStr.contains(MICLOUD_BACKUP_HOST) ||
                            urlStr.contains(MICLOUD_STATUS_HOST) ||
                            urlStr.contains(MICLOUD_PDC_HOST)) {
                            
                            val newUrl = replaceUrlString(urlStr)
                            XposedBridge.log("[$TAG] URL redirect: $urlStr -> $newUrl")
                            param.args[0] = newUrl
                        }
                    }
                }
            )
            XposedBridge.log("[$TAG] Hooked URL constructor")
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] Failed to hook URL: ${e.message}")
        }
    }

    /**
     * Hook URL/Uri 解析
     */
    private fun hookUrlParsing(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Hook android.net.Uri.parse
            XposedHelpers.findAndHookMethod(
                "android.net.Uri",
                lpparam.classLoader,
                "parse",
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val urlStr = param.args[0] as? String ?: return
                        if (containsMicloudHost(urlStr)) {
                            val newUrl = replaceUrlString(urlStr)
                            XposedBridge.log("[$TAG] Uri.parse redirect: $urlStr -> $newUrl")
                            param.args[0] = newUrl
                        }
                    }
                }
            )
            XposedBridge.log("[$TAG] Hooked Uri.parse")
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] Failed to hook Uri.parse: ${e.message}")
        }
    }

    /**
     * Hook SSL 验证（允许自签名证书或 HTTP）
     */
    private fun hookSslVerification(lpparam: XC_LoadPackage.LoadPackageParam) {
        val config = ConfigManager
        if (config.protocol != "https") return

        try {
            // 信任所有证书
            val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
                object : javax.net.ssl.X509TrustManager {
                    override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                }
            )

            // Hook SSLContext.init
            XposedHelpers.findAndHookMethod(
                "javax.net.ssl.SSLContext",
                lpparam.classLoader,
                "init",
                Array<javax.net.ssl.KeyManager>::class.java,
                Array<javax.net.ssl.TrustManager>::class.java,
                java.security.SecureRandom::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        // 只替换 TrustManager
                        param.args[1] = trustAllCerts
                        XposedBridge.log("[$TAG] SSL verification disabled")
                    }
                }
            )

            // Hook HostnameVerifier
            XposedHelpers.findAndHookMethod(
                "javax.net.ssl.HttpsURLConnection",
                lpparam.classLoader,
                "setHostnameVerifier",
                javax.net.ssl.HostnameVerifier::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.args[0] = javax.net.ssl.HostnameVerifier { _, _ -> true }
                    }
                }
            )

            XposedBridge.log("[$TAG] SSL hooks installed")
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] SSL hook failed: ${e.message}")
        }
    }

    // ========== 工具方法 ==========

    private fun isMicloudHost(host: String): Boolean {
        return host.contains("micloud.xiaomi.net") ||
               host.contains("xiaomi.com") && host.contains("backup")
    }

    private fun containsMicloudHost(url: String): Boolean {
        return url.contains(MICLOUD_BACKUP_HOST) ||
               url.contains(MICLOUD_STATUS_HOST) ||
               url.contains(MICLOUD_PDC_HOST) ||
               url.contains(MICLOUD_FILE_HOST)
    }

    /**
     * 替换 URI 中的域名为私有服务器
     */
    private fun replaceUri(uri: java.net.URI): java.net.URI {
        val config = ConfigManager
        val newHost = config.address
        val newPort = config.port
        val newScheme = config.protocol

        return java.net.URI(
            newScheme,
            uri.userInfo,
            newHost,
            newPort,
            uri.path,
            uri.query,
            uri.fragment
        )
    }

    /**
     * 替换 URL 字符串中的域名
     */
    private fun replaceUrlString(url: String): String {
        val config = ConfigManager
        val baseUrl = config.getBaseUrl()
        if (baseUrl.isEmpty()) return url

        var result = url
        result = result.replace("https://$MICLOUD_BACKUP_HOST", baseUrl)
        result = result.replace("http://$MICLOUD_BACKUP_HOST", baseUrl)
        result = result.replace("https://$MICLOUD_STATUS_HOST", baseUrl)
        result = result.replace("http://$MICLOUD_STATUS_HOST", baseUrl)
        result = result.replace("https://$MICLOUD_PDC_HOST", baseUrl)
        result = result.replace("http://$MICLOUD_PDC_HOST", baseUrl)
        result = result.replace("https://$MICLOUD_FILE_HOST", baseUrl)
        result = result.replace("http://$MICLOUD_FILE_HOST", baseUrl)
        return result
    }
}
