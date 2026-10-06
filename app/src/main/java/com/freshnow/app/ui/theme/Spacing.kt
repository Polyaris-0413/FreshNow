package com.freshnow.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 间距设计源，取值来自 Material Design 3 间距体系：
 * 4dp 基础网格，组件内边距 4/8/12/16/24，组件间距 8/12/16/24，段间距 24/32/48，紧凑屏页边距 16
 * 界面只引用，不得另行定义字面量
 */
object FreshNowSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 16.dp
    val md = 24.dp
    val lg = 32.dp
    val xl = 48.dp
}
