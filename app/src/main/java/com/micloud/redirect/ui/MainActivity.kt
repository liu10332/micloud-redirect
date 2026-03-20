package com.micloud.redirect.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.micloud.redirect.ConfigManager
import com.micloud.redirect.R
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var etAddress: TextInputEditText
    private lateinit var etPort: TextInputEditText
    private lateinit var toggleProtocol: MaterialButtonToggleGroup
    private lateinit var btnSave: MaterialButton
    private lateinit var btnTest: MaterialButton
    private lateinit var tvTestResult: android.widget.TextView
    private lateinit var tvModuleStatus: android.widget.TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        loadConfig()
        setupListeners()
        checkModuleStatus()
    }

    private fun initViews() {
        etAddress = findViewById(R.id.etAddress)
        etPort = findViewById(R.id.etPort)
        toggleProtocol = findViewById(R.id.toggleProtocol)
        btnSave = findViewById(R.id.btnSave)
        btnTest = findViewById(R.id.btnTest)
        tvTestResult = findViewById(R.id.tvTestResult)
        tvModuleStatus = findViewById(R.id.tvModuleStatus)
    }

    private fun loadConfig() {
        ConfigManager.init(this)
        etAddress.setText(ConfigManager.address)
        etPort.setText(ConfigManager.port.toString())

        when (ConfigManager.protocol) {
            "https" -> toggleProtocol.check(R.id.btnHttps)
            else -> toggleProtocol.check(R.id.btnHttp)
        }
    }

    private fun setupListeners() {
        // 协议切换
        toggleProtocol.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                ConfigManager.protocol = when (checkedId) {
                    R.id.btnHttps -> "https"
                    else -> "http"
                }
            }
        }

        // 保存配置
        btnSave.setOnClickListener {
            val address = etAddress.text.toString().trim()
            val portStr = etPort.text.toString().trim()

            if (address.isEmpty()) {
                Toast.makeText(this, R.string.address_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 清理地址（去掉 http:// 或 https:// 前缀和尾部斜杠）
            val cleanAddress = address
                .removePrefix("http://")
                .removePrefix("https://")
                .removeSuffix("/")

            ConfigManager.address = cleanAddress
            ConfigManager.port = portStr.toIntOrNull() ?: 8080
            ConfigManager.enabled = true

            Toast.makeText(this, R.string.saved_success, Toast.LENGTH_SHORT).show()
        }

        // 测试连接
        btnTest.setOnClickListener {
            val address = etAddress.text.toString().trim()
            if (address.isEmpty()) {
                Toast.makeText(this, R.string.address_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val cleanAddress = address
                .removePrefix("http://")
                .removePrefix("https://")
                .removeSuffix("/")
            val port = etPort.text.toString().trim().toIntOrNull() ?: 8080
            val protocol = when (toggleProtocol.checkedButtonId) {
                R.id.btnHttps -> "https"
                else -> "http"
            }

            testConnection(protocol, cleanAddress, port)
        }
    }

    private fun testConnection(protocol: String, address: String, port: Int) {
        tvTestResult.visibility = View.VISIBLE
        tvTestResult.text = getString(R.string.test_testing)
        tvTestResult.setTextColor(getColor(R.color.hint))
        btnTest.isEnabled = false

        thread {
            val url = URL("$protocol://$address:$port/health")
            var success = false
            var errorMsg = ""

            try {
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.requestMethod = "GET"

                // 如果是 HTTPS，信任所有证书（仅测试用）
                if (protocol == "https" && conn is javax.net.ssl.HttpsURLConnection) {
                    val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
                        object : javax.net.ssl.X509TrustManager {
                            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                        }
                    )
                    val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
                    sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                    conn.sslSocketFactory = sslContext.socketFactory
                    conn.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
                }

                val responseCode = conn.responseCode
                success = responseCode == 200
                conn.disconnect()
            } catch (e: Exception) {
                errorMsg = e.message ?: "Unknown error"
            }

            runOnUiThread {
                btnTest.isEnabled = true
                if (success) {
                    tvTestResult.text = getString(R.string.test_success)
                    tvTestResult.setTextColor(getColor(R.color.success))
                } else {
                    tvTestResult.text = "${getString(R.string.test_fail)}\n$errorMsg"
                    tvTestResult.setTextColor(getColor(R.color.error))
                }
            }
        }
    }

    private fun checkModuleStatus() {
        // 检查 LSPosed 是否激活了这个模块
        // 简单检查：如果 SharedPreferences 是 WORLD_READABLE 且能读到配置，说明模块框架在运行
        try {
            val prefs = getSharedPreferences("micloud_redirect_config", MODE_WORLD_READABLE)
            tvModuleStatus.text = getString(R.string.module_active)
            tvModuleStatus.setTextColor(getColor(R.color.success))
        } catch (e: Exception) {
            tvModuleStatus.text = getString(R.string.module_inactive)
            tvModuleStatus.setTextColor(getColor(R.color.error))
        }
    }
}
