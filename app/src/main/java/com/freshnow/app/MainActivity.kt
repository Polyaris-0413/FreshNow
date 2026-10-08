package com.freshnow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
}
