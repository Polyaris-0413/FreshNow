# Ktor 的 IntellijIdeaDebugDetector 在 JVM 上用 java.lang.management 判断有没有挂上调试器，
# 这个包在 Android 上整个不存在。那段代码在 Android 上永远不会被走到（调试检测不是 Android 的
# 调试路径），但 R8 在压缩时找不到被引用的类就会中止整个构建，而不是把它当成一条死代码删掉。
#
# 排除整个包而不是只排除 R8 报出来的那两个类：这个包在 Android 上一个类都没有，
# 任何引用都必然是同一类问题，逐个列出来只会让下次升级 Ktor 时再撞一次。
-dontwarn java.lang.management.**
