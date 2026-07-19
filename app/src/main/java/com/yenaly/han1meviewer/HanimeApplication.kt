package com.yenaly.han1meviewer

import android.content.ComponentName
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.material.color.DynamicColors
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.analytics
import com.google.firebase.crashlytics.crashlytics
import com.google.firebase.crashlytics.setCustomKeys
import com.google.firebase.database.database
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.yenaly.han1meviewer.logic.network.HProxySelector
import com.yenaly.han1meviewer.ui.viewmodel.AppViewModel
import com.yenaly.han1meviewer.util.AnimeShaders
import com.yenaly.han1meviewer.util.ThemeUtils
import com.yenaly.yenaly_libs.base.YenalyApplication
import com.yenaly.yenaly_libs.utils.LanguageHelper
import `is`.xyz.mpv.MPVLib
import java.net.ProxySelector

/**
 * @project Hanime1
 * @author Yenaly Liew
 * @time 2022/06/08 008 17:32
 */
class HanimeApplication : YenalyApplication() {

    companion object {
        const val TAG = "HanimeApplication"

        /**
         * 标志 Firebase 是否可用（初始化成功）。
         * 当使用占位 google-services.json 构建时，Firebase 不可用，
         * 所有 Firebase API 调用都应检查此标志以避免崩溃。
         */
        @Volatile
        var isFirebaseAvailable: Boolean = false
            private set
    }

    /**
     * 已经在 [HInitializer] 中处理了
     */
    override val isDefaultCrashHandlerEnabled: Boolean = false

    override fun onCreate() {
        super.onCreate()

        // 标记应用启动时间，用于 HCrashHandler 检测重启循环
        HCrashHandler.markAppStart()

        try {
            ThemeUtils.applyDarkModeFromPreferences(this)
        } catch (e: Exception) {
            Log.e(TAG, "Theme init failed", e)
        }

        if (Preferences.useDynamicColor) {
            try {
                DynamicColors.applyToActivitiesIfAvailable(this)
            } catch (e: Exception) {
                Log.e(TAG, "DynamicColors init failed", e)
            }
        }

        try {
            ProxySelector.setDefault(HProxySelector())
            HProxySelector.rebuildNetwork()
        } catch (e: Exception) {
            Log.e(TAG, "ProxySelector init failed", e)
        }

        initFirebase()
        initNotificationChannel()

        try {
            MPVLib.create(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "MPVLib.create failed", e)
        }
        try {
            MPVLib.init()
        } catch (e: Exception) {
            Log.e(TAG, "MPVLib.init failed", e)
        }

        try {
            if (AnimeShaders.copyShaderAssets(applicationContext) <= 0) {
                Log.w(TAG, "Shader 复制失败")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Shader copy failed", e)
        }

        try {
            if (AnimeShaders.copyCertAssets(applicationContext) <= 0) {
                Log.w(TAG, "cert 复制失败")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cert copy failed", e)
        }

        try {
            val selected = Preferences.fakeLauncherIcon
            switchLauncher(selected)
        } catch (e: Exception) {
            Log.e(TAG, "switchLauncher failed", e)
        }
    }

    private fun initFirebase() {
        try {
            // 手动初始化 Firebase（已禁用自动初始化 ContentProvider）
            FirebaseApp.initializeApp(this)
            // 用于处理 Firebase Analytics 初始化
            Firebase.analytics.setAnalyticsCollectionEnabled(Preferences.isAnalyticsEnabled)
            // 用于处理 Firebase Crashlytics 初始化
            Firebase.crashlytics.apply {
                isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG
                setCustomKeys {
                    key(
                        FirebaseConstants.APP_LANGUAGE,
                        LanguageHelper.preferredLanguage.toLanguageTag()
                    )
                    key(
                        FirebaseConstants.VERSION_SOURCE,
                        BuildConfig.VERSION_SOURCE
                    )
                }
            }
            // 用于处理 Firebase Remote Config 初始化
            Firebase.remoteConfig.apply {
                setConfigSettingsAsync(remoteConfigSettings {
                    minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 0 else 3 * 60 * 60
                    fetchTimeoutInSeconds = 10
                })
                setDefaultsAsync(FirebaseConstants.remoteConfigDefaults)
                fetchAndActivate().addOnCompleteListener {
                    AppViewModel.getLatestVersion(delayMillis = 200)
                }
            }
            Firebase.database.setPersistenceEnabled(true)
            isFirebaseAvailable = true
        } catch (e: Exception) {
            Log.e(TAG, "Firebase init failed, running without Firebase", e)
            isFirebaseAvailable = false
        }
    }

    private fun initNotificationChannel() {
        try {
            val nm = NotificationManagerCompat.from(this)

            val hanimeDownloadChannel = NotificationChannelCompat.Builder(
                DOWNLOAD_NOTIFICATION_CHANNEL,
                NotificationManagerCompat.IMPORTANCE_HIGH
            ).setName("Hanime Download").build()
            nm.createNotificationChannel(hanimeDownloadChannel)

            val appUpdateChannel = NotificationChannelCompat.Builder(
                UPDATE_NOTIFICATION_CHANNEL,
                NotificationManagerCompat.IMPORTANCE_HIGH
            ).setName("App Update").build()
            nm.createNotificationChannel(appUpdateChannel)
        } catch (e: Exception) {
            Log.e(TAG, "NotificationChannel init failed", e)
        }
    }

    fun switchLauncher(alias: String) {
        val pm = packageManager

        val allAliases = listOf(
            "com.yenaly.han1meviewer.LauncherAliasDefault",
            "com.yenaly.han1meviewer.LauncherFakeCalc",
            "com.yenaly.han1meviewer.LauncherFakeCornhub",
            "com.yenaly.han1meviewer.LauncherFakeXxt"
        )

        allAliases.forEach { a ->
            val state = if (a == alias)
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED

            pm.setComponentEnabledSetting(
                ComponentName(this, a),
                state,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}
