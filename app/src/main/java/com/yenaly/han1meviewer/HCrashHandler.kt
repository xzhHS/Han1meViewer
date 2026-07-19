package com.yenaly.han1meviewer

import android.os.Process
import android.util.Log
import com.yenaly.yenaly_libs.ActivityManager

/**
 * 全局未捕获异常处理器。
 *
 * 防止重启循环：如果应用在上次启动后 [MIN_UPTIME_MS] 内再次崩溃，
 * 则不再重启，避免无限重启循环导致用户无法使用应用（如阅读使用须知的倒计时不断被重置）。
 */
object HCrashHandler : Thread.UncaughtExceptionHandler {

    private const val TAG = "HCrashHandler"

    /**
     * 最小正常运行时间（毫秒）。应用启动后必须至少运行这么久，
     * 崩溃时才会触发重启。如果在此时间窗口内崩溃，直接退出进程。
     */
    private const val MIN_UPTIME_MS = 10_000L

    /**
     * 记录应用启动的时间戳，用于检测重启循环
     */
    @Volatile
    private var appStartTime: Long = System.currentTimeMillis()

    /**
     * 由 Application.onCreate() 调用，更新启动时间戳
     */
    fun markAppStart() {
        appStartTime = System.currentTimeMillis()
        Log.d(TAG, "App start time recorded: $appStartTime")
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        e.printStackTrace()
        Log.e(TAG, "Uncaught exception in thread ${t.name}", e)

        // CI 构建使用占位 google-services.json，Firebase SDK 后台线程
        // 校验 API key 时会抛出 IllegalArgumentException。
        // 这类异常发生在后台线程（pool-*-thread-*），不影响主线程 UI，
        // 直接忽略即可让应用继续运行，用户可以在使用须知倒计时结束后正常进入。
        if (e is IllegalArgumentException &&
            e.message?.contains("valid API key") == true
        ) {
            Log.w(
                TAG,
                "Ignoring Firebase API key exception from background thread ${t.name}. " +
                    "This is expected when using placeholder google-services.json."
            )
            return
        }

        val uptime = System.currentTimeMillis() - appStartTime
        if (uptime < MIN_UPTIME_MS) {
            // 启动后很快崩溃，可能是重启循环，直接退出
            Log.w(
                TAG,
                "App crashed within ${uptime}ms of startup (< ${MIN_UPTIME_MS}ms). " +
                    "Killing process to prevent restart loop. Crash: ${e.message}"
            )
            Process.killProcess(Process.myPid())
        } else {
            // 正常运行时间够长，尝试重启
            Log.w(
                TAG,
                "App crashed after ${uptime}ms of startup. Restarting app."
            )
            ActivityManager.restart(killProcess = true)
        }
    }
}
