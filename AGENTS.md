# FreshNow 项目规范

## 1. 图标

### 防踩坑
Material Symbols 的 SVG 官方坐标系是负 Y 以及 viewBox 从 -960 开始  
而 Android vector 的视口固定从 0 开始   
若直接把 SVG path 抄进 vector drawable 图形会整体画在视口外 编译不报错但图标不显示  

### 规范
图标命名 ic_用途.xml

### 获取
用户会从 Material Symbols 中提供项目所需的图标
所有图标均会被下载至 C:\Users\Administrator\Downloads

禁止私自下载图标 若用户要求开发某项功能但却未提供图标 则应暂停任务并告知用户

## 2. 设计规范
项目所使用的设计语言为 Material Design
在设计界面时 需遵循 Material 3 技能的规范
路径为 C:\Users\Administrator\.pi\agent\skills\material-3

## 3. 杂项
- 禁止私自在真机进行与键盘输入有关的调试 这会导致输入法的BUG 应要求用户进行手动调试
- baseline profile 无法在真机上录制 原因未知 需要使用虚拟机
- 跑仪器化测试不要用 Gradle 的 connectedDebugAndroidTest 它会在所有已连接设备上安装并在结束后卸载应用
  真机被卸载后需手动授权安装 很麻烦 且应用数据会一并丢失
  应改为指定设备 先 assembleDebugAndroidTest 再把 app 与 test 两个 apk 装到目标设备
  最后 adb -s <设备> shell am instrument -w com.freshnow.app.test/androidx.test.runner.AndroidJUnitRunner
