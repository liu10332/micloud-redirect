# MiCloud Redirect

将小米云备份流量重定向到你的私有 NAS 服务器。

## 功能

- ✅ 拦截小米云备份 HTTP 请求
- ✅ 重定向到自定义服务器地址
- ✅ 支持 HTTP/HTTPS 协议切换
- ✅ 图形界面配置
- ✅ 连接测试功能
- ✅ LSPosed 框架支持

## 工作原理

```
小米云备份 APP
    ↓ (原本) appbackupapi.micloud.xiaomi.net
    ↓ (hook 后) 你的 NAS 地址
    ↓
私有备份服务器 (Docker)
    ↓
NAS 存储
```

## 使用方法

### 1. 安装依赖

- [LSPosed](https://github.com/LSPosed/LSPosed) 框架
- 已 Root 的 Android 设备

### 2. 部署服务器

在 NAS 上部署 [micloud-backup-server](../micloud-server-go/)：

```bash
cd micloud-server-go
docker compose up -d
```

### 3. 安装本模块

1. 从 [Releases](../../releases) 下载最新 APK
2. 安装到手机
3. 在 LSPosed 管理器中启用本模块
4. 重启作用域应用（小米云备份）

### 4. 配置

打开 APP，配置：
- NAS 地址（IP 或域名）
- 端口（默认 8080）
- 协议（HTTP/HTTPS）
- 点击「测试连接」确认
- 点击「保存」

## 构建

### 本地构建

```bash
./gradlew assembleDebug
```

### GitHub Actions

推送到 main 分支会自动构建 APK，可在 Actions 页面下载。

## 项目结构

```
app/src/main/
├── AndroidManifest.xml          # Xposed 模块声明
├── assets/xposed_init           # Xposed 入口
├── java/com/micloud/redirect/
│   ├── ConfigManager.kt         # 配置管理
│   ├── hook/
│   │   └── BackupHook.kt        # Hook 核心逻辑
│   └── ui/
│       └── MainActivity.kt      # 配置界面
└── res/
    ├── layout/activity_main.xml # UI 布局
    └── values/                  # 字符串、颜色、主题
```

## License

MIT
