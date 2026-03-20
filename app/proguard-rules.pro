# Xposed 相关
-keep class com.micloud.redirect.hook.** { *; }
-keep class de.robv.android.xposed.** { *; }

# 保留 ConfigManager
-keep class com.micloud.redirect.ConfigManager { *; }
