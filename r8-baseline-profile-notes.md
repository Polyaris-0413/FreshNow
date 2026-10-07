# R8 与 baseline profile 备忘

这份文档记录本项目在 R8 收缩与 baseline profile 录制上踩过的坑与可用做法，供上下文丢失后重新接手时参考。与代码里的注释分工：注释只解释那行代码为什么必须那么写，本文记录整体链路、判据和操作性陷阱。

标「本次实测」的数字来自 2026-10-07 在 GMD（`pixel7Api36`）上的一次完整重录。录制是**非确定性**的：轮数、耗时、条目数都会浮动。判据要看形态与量级，不要比单点数值。

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

`packageScope` 留空即默认的 `**`。本次实测 `:app:assembleRelease`：APK 2587291 字节，`mapping.txt` 中 `com.freshnow` 条目 118 条，应用类被改名（如 `AiAnalysis → h3`），死代码带 `R8$$REMOVED$$CLASS$$` 标记。库的保底规则本来就齐全（Room / Compose / CameraX / DataStore 的 consumer 规则都在 `app/build/outputs/mapping/release/configuration.txt` 里），`app/src/main/keepRules/rules.keep` 保持空模板即可。

### 坑一：`optimization.packageScope` 不是「只优化这些包」

它是 R8 gradual shrinking（部分收缩）的**包范围**，默认值 `**` 即全量。AGP 内部把 `packageScopeEnabled`（= `includePackages != setOf("**")`）接到 `gradualShrinkingEnabled`，把内容接到 `gradualShrinkingPackages`，并受实验开关 `android.r8.gradual.support`（`BooleanOption.R8_GRADUAL_API`，AGP 9.4.1 里默认 true）约束。

设成具体包（例如 `setOf("androidx.**", "kotlin.**", "kotlinx.**")`）的后果是**范围之外的代码完全不参与收缩与混淆**。本项目踩过一次：当时 `mapping.txt` 里 `com.freshnow` 条目为 0，应用类名全部原样进包。

收缩范围该用 keep 规则表达，不要用它去缩小。现在的写法（留空）就是修法，别再动它。

### 坑二：`optimization.enable` 会被 `initWith` 继承

`initWith(release)` 会把新的 `optimization` 块一并复制过去：`BuildType._initWith` 调 `OptimizationImpl.initWith`，后者复制 `enable` 与 `packageScope`。插件里用于关混淆的 `setMinifyEnabled(false)` 在新 DSL 下不再生效——AGP 9 里 `BuildType.isMinifyEnabled` 仍然存在，但它只是与 `optimization.enable` 并存的旧字段，变体侧算是否收缩时读的是新 DSL（`optimizationDslInfo.postProcessingOptions`）。要关收缩必须写 `optimization { enable = false }`。

## 二、baseline profile 录制

### 入口与必要接线

- 录制入口是 **`:app:generateBaselineProfile`**（app 侧的聚合任务）。
- 直接跑 `:baselineprofile:pixel7Api36NonMinifiedReleaseAndroidTest` **也会真的录制**：生产侧插件把 `android.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules` 直接设在测试变体上（`addEnabledRulesInstrumentationArgument` 默认为 true，只有显式带 `-Pandroidx.baselineprofile.dontdisablerules` 才不设）。这样跑丢的是**后面的 collect 步骤**：`baselineprofile/build/intermediates/baselineprofiles/` 里不会落 `.txt`，等于白录。
- app 侧必须应用 `androidx.baselineprofile` 插件（同一个插件在 app 侧是消费方，负责合成构建类型、补 `<profileable>`、签名兜底），并声明依赖：

```kotlin
dependencies {
    baselineProfile(project(":baselineprofile"))
}
```

缺了依赖不会报错，只会静默没有 profile：`MergeBaselineProfileTask` 会打一条 `variantHasNoBaselineProfileDependency` 告警（可以用 `baselineProfile { warnings { … } }` 关掉）。缺了 app 侧的插件，`benchmarkRelease` / `nonMinifiedRelease` 变体根本不会生成。

### 核心约束：录制载体必须输出原始类名

链路是「在非混淆构建上录制（原始类名）→ R8 按映射改写成 release 的混淆名 → 编译进 APK」。名字对不上，应用类条目会在 **`minifyReleaseWithR8`** 里被整批丢掉。

本项目踩过：插件的 `nonMinifiedRelease` 变体被 `initWith(release)` 继承了 `optimization.enable = true`，实际仍是混淆的，录制出的 profile 带的是混淆名。修法是显式关掉这个变体的收缩：

```kotlin
maybeCreate("nonMinifiedRelease").apply {
    optimization {
        enable = false
    }
}
```

判据（本次实测）：**录制结果**里的 `com/freshnow` 条目应是数百条**原始类名**（872 条）；**最终 profile** 里应用类应是混淆名。反例是录制结果里只剩个位数 `com/freshnow` 条目——那说明录制跑在了混淆构建上。

这里有个容易看错的地方：修好之后，`minifyReleaseWithR8` 那一步的 `com/freshnow` 条目**也只剩 18 条**（其余都改成了 `h3` / `i3` 这类混淆名）。「18 条」在坏、好两种状态下都会出现，区别是前者出现在**录制结果**里、后者出现在 **R8 改写后**的产物里。

### 应用类覆盖率：不要把靶子画成 118 / 118

R8 保留了 118 个应用类（`mapping.txt` 口径），但录制脚本永远到不了其中一部分：

- 按混淆名在最终 profile 里查，命中 **85 / 118**；按原始类名在录制结果里数，只有 **72 / 118**。两者不等，是因为 R8 会合并类，未录制类的条目可能落到已录制类的混淆名上。
- 缺的是脚本到不了的地方：记录详情页的 `RecordDetailViewModel` / `RecordDetailUiState`、需要已有记录才有数据的 `HomeRecordItem` / `ScanThumbnailKt`、以及 `ScanStatus$Analyzing` / `$Failed` / `$NotConfigured` 这些分支（脚本从不真的扫描）。

所以 118/118 在结构上不可能达到，85/118 这个量级才是正常的。

### 环境要求

- 只能在托管虚拟机（GMD）上录：本机 adb 上若有真机，「连接设备」模式无法指定序列号（生产侧插件扩展只有 `managedDevices` / `useConnectedDevices` / `skipBenchmarksOnEmulator` / `enableEmulatorDisplay`，没有 serial 项），会把录制跑到真机上。
- `gradle.properties` 里须钉住 `android.testoptions.manageddevices.emulator.gpu=swiftshader_indirect`。默认的 `-gpu auto` 配上 AVD 档案里的 `hw.gpu.enabled=no` 会让 guest 起来后彻底卡死（qemu CPU 时间零增长、`adb shell` 无响应），构建就一直挂在设备操作上。核实办法：`~/.android/avd/gradle-managed/<avd>.avd/emu-launch-params.txt` 里应出现 `-gpu swiftshader_indirect`。
- 托管设备定义在 `baselineprofile/build.gradle.kts`，设备名要取硬件档案的**显示名**（`Pixel 7`），取目录 id（`pixel_7`）会报找不到档案——AGP 的 `AvdManager.createAvd` 只按 `displayName` 匹配。

### 耗时与失败率：都不要按固定值安排

- 轮数由 `BaselineProfileConfig` 决定，默认 `maxIterations = 15`、`stableIterations = 3`：脚本会被完整跑到「连续 3 轮 profile 不再增长」或跑满 15 轮为止。所以总耗时是「轮数 × 单轮成本」，不是常数。
- 本次实测：用例 1002.6 s（跑满 15 轮，单轮约 1 分钟）+ 构建与建机 = `:app:generateBaselineProfile` 全程 **20m13s**。同一台机器上另两次尝试都倒在第 90 秒的首帧等待上超时（三次的首帧等待分别约 17 s、>90 s、>90 s）。
- 设备是 2 核 / 2.5 GB（`hardware-qemu.ini` 的 `hw.ramSize = 2560`）+ 软件渲染。成功那次的 logcat 里仍有 **9 次 `lowmemorykiller` 杀进程**；设备一旦进入这种状态，控件查找与 `pressBack` 都会退化成秒级，脚本里 1 秒的短等待就不够用，于是出现「最后一段退不回首页」这类**假失败**。
- 结论：慢、以及偶发失败，是这套组合（未收缩的构建 + 软件 GPU + 低内存 + 15 轮）的**预期**表现，重跑一次通常就好。要压时间就调 `BaselineProfileConfig`（`maxIterations` / `stableIterations`），不要加等待。

### 操作陷阱：重录前必须删旧输出

理由来自 `--rerun`：它只作用于命令行上的**最后一个**任务（实测 `a b --rerun` 只重跑 `b`，把顺序对调就只重跑 `a`），所以给 `:app:generateBaselineProfile` 加 `--rerun` 并不能保证重新录制。要换一次录制就显式删旧输出：

```
rm -rf baselineprofile/build/outputs/androidTest-results/managedDevice/nonminifiedrelease
       baselineprofile/build/intermediates/baselineprofiles/nonMinifiedRelease
       app/src/release/generated/baselineProfiles
```

设备侧测试失败是正常的 `BUILD FAILED`，不会被当成 UP-TO-DATE，所以失败之后直接重跑即可，不必先删。

另外：**别删托管虚拟机来「重试」**。删掉 `~/.android/avd/gradle-managed` 会连带丢掉 `snapshots/default_boot`，下次变成首次冷启动——本次那样做直接导致首帧 90 秒超时、测试挂在最后一段。安装偶发 `Broken pipe` 时保留 AVD 直接重跑即可。

### 排查方法：确认 profile 真的进了包

三段各取一个判据，比逐级「数类数」可靠——类数在混淆前后本来就会大幅变化（本次链路是 merged 4435 → `expandReleaseArtProfileWildcards` 7078 → `minifyReleaseWithR8` 3054），单看数字容易误判：

1. **录制结果**：`baselineprofile/build/intermediates/baselineprofiles/nonMinifiedRelease/BaselineProfileGenerator_generate-baseline-prof-<时间戳>.txt` 里 `com/freshnow` 应有数百条**原始类名**条目。
2. **最终 profile**：用 `mapping.txt` 反查混淆名，`combined_art_profile/release/compileReleaseArtProfile/baseline-prof.txt` 里应能查到（如 `AiAnalysis -> h3` 就查 `Lh3;`）。
3. **产物**：`unzip -l app/build/outputs/apk/release/app-release-unsigned.apk | grep baseline` 应有 `assets/dexopt/baseline.prof`（约 9 KB，本次 9088 字节）。

真丢了东西时，再按中间产物顺序逐级看：

```
merged_art_profile/release/mergeReleaseArtProfile/baseline-prof.txt
→ r8_art_profile/release/expandReleaseArtProfileWildcards/baseline-prof.txt
→ r8_art_profile/release/minifyReleaseWithR8/baseline-prof.txt
→ combined_art_profile/release/compileReleaseArtProfile/baseline-prof.txt
```

### 录制脚本

`baselineprofile/src/main/java/com/freshnow/app/baselineprofile/BaselineProfileGenerator.kt`。覆盖四条路径：冷启动首页 → 扫描页（含 CameraX，靠 `pm grant` 过权限门）→ 设置 → 关于 → 开源声明。控件一律按文案与描述符定位，不用坐标。首次界面的等待单独放宽到 90 秒（非混淆构建冷启动明显慢于 release），但设备被 `lowmemorykiller` 拖住时 90 秒也可能不够，见上面「耗时与失败率」。

脚本没有设 `includeInStartupProfile`，所以插件每次成功都会打一条 `No startup profile rules were generated for the variant 'release'`——这是默认行为（`BaselineProfileConfig.includeInStartupProfile` 默认 false），不是故障。要 startup profile 得在规则里显式设 true。

### 产物去哪了：别忘了那个受版本控制的文件

`:app:copyReleaseBaselineProfileIntoSrc` 会把归并后的 profile 写进 **`app/src/release/generated/baselineProfiles/baseline-prof.txt`，而它是入库的**。也就是说每成功录一次就会产生一个约 2.5 MB 的 git 变更（本次 2821 增 / 3489 删，方法级覆盖与上一版一致）。

## 三、已知遗留

- release 包仍是未签名的（`app-release-unsigned.apk`），需要发布密钥才能安装与上架。
- `app/src/main/java/com/freshnow/app/ui/scan/ScanViewModel.kt` 里 `e::class.java.simpleName` 在混淆后会把混淆名显示到界面上（仅在异常 `message` 为空时走到该分支）。
