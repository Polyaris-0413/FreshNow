import com.android.build.api.dsl.ManagedVirtualDevice

plugins {
    // 不带版本：AGP 已由 :app 带版本放进 classpath，此处再声明版本会与它冲突。
    // 版本仍在 gradle/libs.versions.toml 的 agp 上。
    id("com.android.test")
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.freshnow.app.baselineprofile"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // 录制 baseline profile 与宏基准本身都要求 API 28 起
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
    // com.android.test 模块默认由目标应用承载 instrumentation，宏基准要它自己承载自己
    experimentalProperties["android.experimental.self-instrumenting"] = true

    testOptions {
        managedDevices {
            allDevices {
                // @device 取硬件档案的显示名，取 id 会报找不到档案。
                create<ManagedVirtualDevice>("pixel7Api36") {
                    device = "Pixel 7"
                    apiLevel = 36
                    // 官方要求 GMD 录制 baseline profile 用 aosp（见 create-baselineprofile 文档：
                    // 生成器需要 root，Play / Google APIs 镜像不行），对应 SDK 包
                    // system-images;android-36;default;x86_64。不带 GMS，内存压力也比 google_apis 小得多。
                    // 内存/核数 AGP 没有 DSL（RAM 被 sdklib 顶到 2 GiB、核数写死），本机是在 AVD
                    // 生成后手改 config.ini 抬到 4G / 4 核的，细节见 r8-baseline-profile-notes.md。
                    systemImageSource = "aosp"
                    // 不显式声明的话 AGP 10 起默认转 arm64-v8a 并走 NDK 翻译，此处钉住本机架构
                    testedAbi = "x86_64"
                }
            }
        }
    }
}

baselineProfile {
    // 只能用托管虚拟机：本机 adb 上同时连着真机，而连接设备模式无法指定序列号，会录到真机上
    useConnectedDevices = false
    managedDevices += "pixel7Api36"
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.uiautomator)
}
