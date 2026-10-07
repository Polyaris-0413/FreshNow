# R8 与 baseline profile 备忘

这份文档记录本项目在 R8 收缩与 baseline profile 录制上踩过的坑与可用做法，供上下文丢失后重新接手时参考。与代码里的注释分工：注释只解释那行代码为什么必须那么写，本文记录整体链路、判据和操作性陷阱。

标「本次实测」的数字来自 2026-10-07 在 GMD（`pixel7Api36`）上的三次完整重录，用来把「镜像」与「设备规格」两个变量拆开：`google_apis` / 2 核 / 2.5 GB（20m13s，中途假失败两次）、`google_apis` / 4 核 / 4 GB（6m24s）、`aosp` / 4 核 / 4 GB（5m38s）。录制是**非确定性**的：轮数、耗时、条目数都会浮动。判据要看形态与量级，不要比单点数值。

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
- 镜像定 **`google_apis`**。镜像是**覆盖率**上的取舍，不是速度上的：同一台机器、同样 4 核 / 4 GB，`aosp` 比 `google_apis` 只快约 12%（5m38s vs 6m24s），却少覆盖 40 个「含方法的类」——`androidx/emoji2/text`(+flatbuffer)、`androidx/core/provider` 与 `androidx/core/graphics` 的可下载字体 provider 路径等。这些分支要先探测只有 GMS 才提供的字体 provider，`aosp` 上在入口就返回了。应用类与 `androidx/camera` 两种镜像完全一致（244 / 584）。
- 官方文档（`Create Baseline Profiles`）说 GMD 录制要设 `aosp`，理由是生成器需要 root——**那条只对 API < 33 成立**（同一文档也写了 API 33 及以上无需 root）。本项目 `apiLevel = 36`，实测 `google_apis` 能正常录制。要换成 `aosp` 就是 `systemImageSource = "aosp"`（AGP 里 `"aosp"` 与 `"default"` 同源，SDK 包是 `system-images;android-36;default;x86_64`）。
- **内存与核数没有 DSL**：AGP 把 `hw.cpu.ncore` 写死成 `EmulatedProperties.RECOMMENDED_NUMBER_OF_CORES`，并用 `restrictDefaultRamSize()` 把 `hw.ramSize` 顶到 `MAX_DEFAULT_RAM_SIZE = 2 GiB`（不管硬件档案要多少）。要调只能改 AVD 首次生成后的 `config.ini`：`~/.android/avd/gradle-managed/dev36_google_apis_x86_64_Pixel_7.avd/config.ini` 里的 `hw.ramSize` / `hw.cpu.ncore`（本机现为 `4G` / `4`）。`createOrRetrieveAvd` 发现 AVD 已存在且状态正常就直接复用，所以改动能留住；但改完必须重建快照，否则 `-force-snapshot-load` 会拿到硬件对不上的旧快照——删掉 `snapshots/`（连 `bootcompleted.ini` 一起删），再 `./gradlew :baselineprofile:pixel7Api36Setup --rerun` 让它重新开机存一份。
- 装完镜像后如果 AGP 还报 `System image does not exist at …`（`retrieveSystemImage` 会重试 5 次再放弃），先 `./gradlew --stop`：**长期存活的 Gradle daemon 会缓存 SDK 包列表**，看不到刚装进去的镜像。本次就是这么撞的。
- 用 `sdkmanager.bat` 装镜像时，包名里的 `;` 会被 cmd 拆成多个参数（报 `Package android-36 not found`）。写成 .bat 文件调用，别在 bash 里直接传字符串。

### 耗时与失败率：都不要按固定值安排

- 轮数由 `BaselineProfileConfig` 决定，默认 `maxIterations = 15`、`stableIterations = 3`：脚本会被完整跑到「连续 3 轮 profile 不再增长」或跑满 15 轮为止。所以总耗时是「轮数 × 单轮成本」，不是常数。
- 换设备规格前后（同一套脚本、同一台机器）：

  | | `google_apis`<br>2 核 / 2.5 GB | `google_apis`<br>4 核 / 4 GB | `aosp`<br>4 核 / 4 GB |
  |---|---|---|---|
  | 用例耗时 | 1002.6 s（跑满 15 轮） | 343.0 s（10 轮即 stable） | 317.6 s（10 轮即 stable） |
  | `:app:generateBaselineProfile` 全程 | 20m13s | 6m24s | 5m38s |
  | logcat 里 `lowmemorykiller` 杀进程 | 9 次 | 0 次 | 0 次 |
  | 首帧等待（90 s 上限） | 三次尝试两次超时 | 一次通过 | 一次通过 |
  | 「含方法的类」总数 | 4793 | 4785 | 4746 |

- 瓶颈是**内存/核数，不是镜像**：同一个 `google_apis` 镜像，只把 2 核 / 2.5 GB 抬到 4 核 / 4 GB，全程就从 20m13s 降到 6m24s、lmkd 杀进程从 9 次降到 0、轮数从跑满 15 降到 10 即 stable；换镜像是另一个维度的事，只影响那 40 个 GMS-only 类和约 12% 耗时。
- 慢与偶发失败的来源是内存压力，不是脚本逻辑：设备被 `lowmemorykiller` 拖住时，控件查找与 `pressBack` 会退化成秒级，脚本里 1 秒的短等待就不够用，于是出现「最后一段退不回首页」这类**假失败**。先解决设备规格，再考虑动脚本——这套规格下两种镜像都已一次过。
- 要压时间还可以调 `BaselineProfileConfig`（`maxIterations` / `stableIterations`），但那是拿覆盖率换时间，别改默认值。

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
3. **产物**：`unzip -l app/build/outputs/apk/release/app-release-unsigned.apk | grep baseline` 应有 `assets/dexopt/baseline.prof`（约 9 KB，本次 9120 字节）。

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

`:app:copyReleaseBaselineProfileIntoSrc` 会把归并后的 profile 写进 **`app/src/release/generated/baselineProfiles/baseline-prof.txt`，而它是入库的**。也就是说每成功录一次就会产生一个约 2.5 MB 的 git 变更（本次 566 增 / 354 删）。所以提交前要比对方法级覆盖（见上面「排查方法」的第一、二段判据），别把一次录浅了的结果默默提交进去。

## 三、已知遗留

- release 包仍是未签名的（`app-release-unsigned.apk`），需要发布密钥才能安装与上架。
- `app/src/main/java/com/freshnow/app/ui/scan/ScanViewModel.kt` 里 `e::class.java.simpleName` 在混淆后会把混淆名显示到界面上（仅在异常 `message` 为空时走到该分支）。
