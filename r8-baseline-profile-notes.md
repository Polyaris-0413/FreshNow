# R8 与 baseline profile 备忘

这份文档记录本项目在 R8 收缩与 baseline profile 录制上踩过的坑与可用做法，供上下文丢失后重新接手时参考。与代码里的注释分工：注释只解释那行代码为什么必须那么写，本文记录整体链路、判据和操作性陷阱。

## 一、release 的 R8 全开

配置形态（`app/build.gradle.kts`）：

```kotlin
buildTypes {
    release {
        optimization {
            enable = true
        }
    }
}
```

`packageScope` 留空即默认的 `**`。实测 `:app:assembleRelease`：APK 3817168 → 2587291 字节（−32%），`mapping.txt` 中 `com.freshnow` 条目 0 → 118，应用类被改名（如 `AiAnalysis → h3`），死代码带 `R8$$REMOVED$$CLASS$$` 标记。库的保底规则本来就齐全（Room / Compose / CameraX / DataStore 的 consumer 规则都在 `app/build/outputs/mapping/release/configuration.txt` 里），`app/src/main/keepRules/rules.keep` 保持空模板即可。

### 坑一：`optimization.packageScope` 不是「只优化这些包」

它是 R8 gradual shrinking（部分收缩）的**包范围**，默认值 `**` 即全量。AGP 内部把 `packageScopeEnabled` 接到 `gradualShrinkingEnabled`，把内容接到 `gradualShrinkingPackages`，并受实验开关 `android.r8.gradual.support`（AGP 9.4.1 里默认 true）约束。

设成具体包（例如 `setOf("androidx.**", "kotlin.**", "kotlinx.**")`）的后果是**范围之外的代码完全不参与收缩与混淆**。本项目曾因此漏掉应用自身代码：`mapping.txt` 里 `com.freshnow` 条目为 0，APK 里 383 个应用类名保持原样。

收缩范围该用 keep 规则表达，不要用它去缩小。

### 坑二：`optimization.enable` 会被 `initWith` 继承

`initWith(release)` 会把新的 `optimization` 块一并复制过去。插件里用于关混淆的 `setMinifyEnabled(false)`（旧属性，AGP 9 里仍存在）在新 DSL 下不再生效，要关收缩必须写 `optimization { enable = false }`。

## 二、baseline profile 录制

### 入口与必要接线

- 录制入口是 **`:app:generateBaselineProfile`**（app 侧的聚合任务），不是测试模块的 `*AndroidTest` 任务。直接跑后者会因缺少 `androidx.benchmark.enabledRules` 参数而被 JUnit 的 assumption 静默跳过，任务却报成功。
- app 侧必须应用 `androidx.baselineprofile` 插件（同一个插件在 app 侧是消费方，负责合成构建类型、补 `<profileable>`、签名兜底），并声明依赖：

```kotlin
dependencies {
    baselineProfile(project(":baselineprofile"))
}
```

缺了依赖不会报错，只会静默没有 profile（插件会打印一段提示）。缺了 app 侧的插件，`benchmarkRelease` / `nonMinifiedRelease` 变体根本不会生成。

### 核心约束：录制载体必须输出原始类名

链路是「在非混淆构建上录制（原始类名）→ R8 按映射改写成 release 的混淆名 → 编译进 APK」。名字对不上，应用类条目会在 **`minifyReleaseWithR8`** 里被整批丢掉。

本项目踩过：插件的 `nonMinifiedRelease` 变体被 `initWith(release)` 继承了 `optimization.enable = true`，实际仍是混淆的（该 APK 4048 个类里只有 3 个带 `com.freshnow` 名字）。结果录制出的 profile 是混淆名，R8 改写时按原始名查不到。

修法是显式关掉这个变体的收缩：

```kotlin
maybeCreate("nonMinifiedRelease").apply {
    optimization {
        enable = false
    }
}
```

修复前后对比（同一套录制脚本）：

| | 修复前 | 修复后 |
|---|---|---|
| 录制结果里的 `com/freshnow` 条目 | 18 条（混淆名） | 868 条（原始类名） |
| 编译出的 profile 类数 | 1525 | 2506 |
| APK 内 `assets/dexopt/baseline.prof` | 8654 字节 | 9095 字节 |
| 应用类覆盖 | 11 / 118 | 118 / 118 |

release APK 体积不变（2587291 字节），profile 本身不占什么空间。

### 环境要求

- 只能在托管虚拟机（GMD）上录：本机 adb 上若有真机，「连接设备」模式无法指定序列号，会把录制跑到真机上。
- `gradle.properties` 里须钉住 `android.testoptions.manageddevices.emulator.gpu=swiftshader_indirect`。默认的 `-gpu auto` 配上 AVD 档案里的 `hw.gpu.enabled=no` 会让 guest 起来后彻底卡死（qemu CPU 时间零增长、`adb shell` 无响应），构建就一直挂在设备操作上。
- 托管设备定义在 `baselineprofile/build.gradle.kts`，设备名要取硬件档案的**显示名**（`Pixel 7`），取目录 id（`pixel_7`）会报找不到档案。
- 整轮录制约 6 分钟（虚拟机冷启动 + 多轮多界面）。

### 操作陷阱：重录前必须删旧输出

测试任务在**设备侧失败时仍然报 BUILD SUCCESSFUL**，于是结果被判成 UP-TO-DATE，下次整个跳过（`--rerun` 在有多任务时会失效）。重录前先删：

```
rm -rf baselineprofile/build/outputs/androidTest-results/managedDevice/nonminifiedrelease
       baselineprofile/build/intermediates/baselineprofiles/nonMinifiedRelease
       app/src/release/generated/baselineProfiles
```

另外安装偶发 `Broken pipe`（模拟器只有 2GB RAM，装 13MB 的非混淆包容易出），清掉托管虚拟机重试即过。

### 排查方法：profile 没进包时逐级比对类数

按中间产物顺序比对，能直接定位在哪一步丢的：

```
merged_art_profile/release/mergeReleaseArtProfile/baseline-prof.txt
→ r8_art_profile/release/expandReleaseArtProfileWildcards/baseline-prof.txt
→ r8_art_profile/release/minifyReleaseWithR8/baseline-prof.txt
→ combined_art_profile/release/compileReleaseArtProfile/baseline-prof.txt
```

本次就是靠它发现丢弃发生在 `minifyReleaseWithR8`（6238 个类进去，1525 个类出来）。

### 录制脚本

`baselineprofile/src/main/java/com/freshnow/app/baselineprofile/BaselineProfileGenerator.kt`。覆盖四条路径：冷启动首页 → 扫描页（含 CameraX，靠 `pm grant` 过权限门）→ 设置 → 关于 → 开源声明。控件一律按文案与描述符定位，不用坐标。首次界面的等待单独放宽到 90 秒：非混淆构建冷启动明显慢于 release，实测首帧出现在 10 秒等待到期之后。

## 三、已知遗留

- release 包仍是未签名的（`app-release-unsigned.apk`），需要发布密钥才能安装与上架。
- `app/src/main/java/com/freshnow/app/ui/scan/ScanViewModel.kt` 里 `e::class.java.simpleName` 在混淆后会把混淆名显示到界面上（仅在异常 `message` 为空时走到该分支）。
