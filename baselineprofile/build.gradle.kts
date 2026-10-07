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
                // 设备与镜像对齐本机已装的 system-images;android-36;google_apis;x86_64，
                // 托管设备因此不会另下一份镜像。@device 取硬件档案的显示名，取 id 会报找不到档案。
                create<ManagedVirtualDevice>("pixel7Api36") {
                    device = "Pixel 7"
                    apiLevel = 36
                    systemImageSource = "google_apis"
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
