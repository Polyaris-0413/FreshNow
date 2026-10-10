plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // 同步的载荷是 @Serializable 的数据类，这个插件负责在编译期给它们生成序列化器
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    // 同一个插件在 app 侧是「消费方」：负责给录制用的构建类型补 <profileable> 与签名兜底，
    // 并决定 profile 落到哪个源集。只把它用在 :baselineprofile 上时，app 侧这套不会启用。
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.freshnow.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.freshnow.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 5
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // 让 debug 与 release 能在同一台设备上共存：包名不同 → 互不覆盖，也就不必让 debug
            // 共用发布密钥（发布包是 AS 的 Generate Signed APK 向导用 -Pandroid.injected.signing.*
            // 注入签名构建的，仓库里不存任何签名材料）。
            // 后缀只能加在 debug 上：baselineprofile 的录制脚本认死 com.freshnow.app，而它跑在
            // nonMinifiedRelease（从 release 派生）上，加了后缀就连不上目标应用。
            applicationIdSuffix = ".debug"
        }
        release {
            // packageScope 留空即默认的 "**"，R8 收缩范围覆盖整个应用
            optimization {
                enable = true
            }
            // AGP 只在内置默认规则之外找这里声明的文件，所以这一行是自定义规则唯一的入口
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // 插件的 nonMinifiedRelease 是 baseline profile 的录制载体：它的名字必须是原始名，
        // 之后由 R8 按映射改写成 release 的混淆名。但 AGP 9 的 optimization DSL 会被
        // initWith(release) 一并继承，插件里关混淆的旧开关不起作用，导致录制出来的是混淆名，
        // R8 改写时按原始名查不到，应用类条目会在 minifyReleaseWithR8 里被整批丢掉。
        maybeCreate("nonMinifiedRelease").apply {
            optimization {
                enable = false
            }
        }
        // 为了验「两台设备互相同步」而存在的第二份安装。
        //
        // 同一个包名装两份共享同一份数据，装不出两台设备来；而局域网同步的错处恰恰全在
        // 「两边各自看到的状态不一样」上。这一份与 debug 一起装在同一台设备上，各自的库、
        // 照片目录、设备身份互相隔离，中间走 127.0.0.1 上的真实 HTTP。
        // 它只用于手工验证与调试，不参与发布。
        maybeCreate("debugAlt").apply {
            initWith(getByName("debug"))
            applicationIdSuffix = ".debugalt"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    sourceSets {
        // 迁移测试（ScanRecordMigrationTest）要读 schemas/ 里的表结构。Room 自己的 Gradle 插件
        // 会自动把那个目录挂进测试的 assets，而本项目用的是 KSP 的 schemaLocation 参数，
        // 于是这里手动挂一次
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
    buildFeatures {
        compose = true
        // Room 需要按构建类型区分迁移策略（见 FreshNowDatabase），release 与 debug 走不同分支
        buildConfig = true
    }
}

ksp {
    // Room 导出的表结构，供核对与编写迁移语句用；该目录要提交进版本控制
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // release 变体的 baseline profile 来源：录制产物由 :baselineprofile 在虚拟机跑出来，
    // 再经 :app:generateBaselineProfile 归并进 src/release/generated/baselineProfiles。缺了这条，
    // release 变体不会带上应用自己的 profile。
    baselineProfile(project(":baselineprofile"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.client.cio)
    implementation(libs.kotlinx.serialization.json)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    // 迁移动不动就弄丢用户已有的记录，而它只在升级那一刻跑一次——写错了一年到头都不会发现
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}