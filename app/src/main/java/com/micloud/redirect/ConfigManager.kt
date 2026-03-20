package com.micloud.redirect

import android.content.Context
import android.content.SharedPreferences

/**
 * 配置管理器
 * 管理 NAS 服务器地址、端口、协议等配置
 */
object ConfigManager {

    private const val PREFS_NAME = "micloud_redirect_config"
    private const val KEY_ADDRESS = "nas_address"
    private const val KEY_PORT = "nas_port"
    private const val KEY_PROTOCOL = "protocol" // "http" or "https"
    private const val KEY_ENABLED = "enabled"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE)
    }

    /**
     * 获取配置（供 Hook 使用，需要 WORLD_READABLE）
     */
    private var cachedPrefs: SharedPreferences? = null

    fun init(context: Context) {
        cachedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE)
    }

    private fun prefs(): SharedPreferences {
        return cachedPrefs ?: throw IllegalStateException("ConfigManager not initialized")
    }

    var address: String
        get() = prefs().getString(KEY_ADDRESS, "") ?: ""
        set(value) = prefs().edit().putString(KEY_ADDRESS, value).apply()

    var port: Int
        get() = prefs().getInt(KEY_PORT, 8080)
        set(value) = prefs().edit().putInt(KEY_PORT, value).apply()

    var protocol: String
        get() = prefs().getString(KEY_PROTOCOL, "http") ?: "http"
        set(value) = prefs().edit().putString(KEY_PROTOCOL, value).apply()

    var enabled: Boolean
        get() = prefs().getBoolean(KEY_ENABLED, true)
        set(value) = prefs().edit().putBoolean(KEY_ENABLED, value).apply()

    /**
     * 构建完整的服务器基础 URL
     */
    fun getBaseUrl(): String {
        val addr = address
        if (addr.isEmpty()) return ""
        return "$protocol://$addr:$port"
    }

    /**
     * 检查配置是否有效
     */
    fun isValid(): Boolean {
        return address.isNotEmpty()
    }
}
