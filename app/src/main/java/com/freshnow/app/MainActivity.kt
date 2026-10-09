package com.freshnow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.freshnow.app.data.sync.SyncCoordinator
import com.freshnow.app.ui.FreshNowNavHost
import com.freshnow.app.ui.AutoUpdateCheck
import com.freshnow.app.ui.theme.FreshNowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FreshNowTheme {
                FreshNowNavHost()
                // 冷启动检查更新。挂在导航之外：对话框是盖在所有页面之上的，与当前停在哪一页无关；
                // 也不必进每个页面各接一次
                AutoUpdateCheck()
            }
        }
    }

    /**
     * 会话跟着应用的前台状态起停。
     *
     * 不做常驻：后台的 dataSync 前台服务在 Android 15 起每天只给 6 小时，逼着应用去猜什么时候
     * 会被系统摸掉，而猜错的表现是「同步时好时坏」；而这类应用的使用节奏本来就是打开看一眼，
     * 在打开的那一刻同步，体感与常驻无异。
     */
    override fun onStart() {
        super.onStart()
        SyncCoordinator.getInstance(this).startSession()
    }

    override fun onStop() {
        SyncCoordinator.getInstance(this).stopSession()
        super.onStop()
    }
}
