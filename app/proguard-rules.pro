# Ktor 的 IntellijIdeaDebugDetector 在 JVM 上用 java.lang.management 判断有没有挂上调试器，
# 这个包在 Android 上整个不存在。那段代码在 Android 上永远不会被走到（调试检测不是 Android 的
# 调试路径），但 R8 在压缩时找不到被引用的类就会中止整个构建，而不是把它当成一条死代码删掉。
#
# 排除整个包而不是只排除 R8 报出来的那两个类：这个包在 Android 上一个类都没有，
# 任何引用都必然是同一类问题，逐个列出来只会让下次升级 Ktor 时再撞一次。
-dontwarn java.lang.management.**

# 发布包不留调试日志：Log.d 的参数里有模型返回内容、识别读数与思维链，属于用户数据。
# 只删 d/v，保留 i/w：它们携带同步诊断信息，且部分日志的参数里带副作用调用，
# 删掉调用可能改变同步行为。
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
