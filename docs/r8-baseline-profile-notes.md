# R8 与 baseline profile 备忘

这份文档记录本项目在 R8 收缩与 baseline profile 录制上的可用做法、判据和操作性陷阱，供上下文丢失后重新接手时参考。与代码里的注释分工：注释只解释那行代码为什么必须那么写，本文记录整体链路、判据，以及**会静默退化**的点。

## 维护约定（改这份文档前先读）

1. **本文可以改，而且应当改。** 过时或已被证伪的结论当场删掉或订正，不能让读者先被错结论带偏再自己发现；需要留作对照的旧结论才进附录 C，并写明它错在哪。
2. **写进正文的每一条都要是通用结论。** 判据要跨页面、跨机器、跨一次录制仍然成立，并且照着就能复现——涉及命令的把命令写上，涉及数字的把日期与口径写上；只对某一次运行成立的观测（某次耗时、某次条目数）不作结论，最多当该结论的实测样例，并标明会浮动。

**只想知道怎么跑：读「怎么跑」和「当前基线」两节就够。** 后面四个附录是「什么会静默坏掉」「为什么这么配」和「别再犯的旧结论」，只在改配置、换机器或排查异常时才需要读。

数字是**非确定性**的：轮数、耗时、条目数都会浮动，判据要看形态与量级，不要比单点数值。标「实测」的都带日期——`2026-10-07` 是建立这套配置那几次，`2026-10-12` 是欢迎页 / 同步页 / 看图页进来之后的一次（配置同为 `google_apis` / 4 核 / 4 GB）。

## 怎么跑

```bash
./gradlew :app:generateBaselineProfile   # 在 GMD 里录：6~20 分钟（随宿主浮动，2026-10-12 实测 17m57s；看门狗别设短超时）
./gradlew :app:assembleRelease           # 把 profile 编进 APK
```

入口只有这一个聚合任务。接线（app 侧的消费方插件、`baselineProfile` 依赖）已经在仓库里，见附录 B-4。

**「跑成功」不等于「录到了」**，三段各取一个判据：

1. **录制结果**：`baselineprofile/build/intermediates/baselineprofiles/nonMinifiedRelease/BaselineProfileGenerator_generate-baseline-prof-<时间戳>.txt` 里应有**上千条**应用侧原始类名：

   ```bash
   grep -cE '^[A-Z]*Lcom/freshnow' baselineprofile/build/intermediates/baselineprofiles/nonMinifiedRelease/*.txt
   # 2026-10-12 实测 1806（2026-10-07 是 863，欢迎页与同步页进脚本后翻倍）
   ```

   **必须带 `^[A-Z]*`**：这份文件的行带标志位前缀（`Lcom/…`、`SPLcom/…`、`HSPLcom/…`）。写成 `grep -c '^com/freshnow'` 会恒为 **0**，而 0 正是附录 A-1「整批丢失」的症状，会把人骗去查一个不存在的问题。同一条命令在每一级中间产物上都能用，数字应一路持平到 R8 改写那一步（实测见附录 A-5）。
2. **最终 profile**：用 `mapping.txt` 反查混淆名（`AiAnalysis -> g5` 就查 `Lg5;`），但**不能按类名逐个查**：顶层 Composable 类会被 R8 整体内联，在 `mapping.txt` 里没有独立条目（`PhotoViewerKt`、`WelcomeScreenKt` 都是），逐类查会得到假阴性。要判覆盖率就用**全部**应用类的混淆名批量比对：

   ```bash
   grep -oE '^com\.freshnow[^ ]* -> [^ :]+' app/build/outputs/mapping/release/mapping.txt
   # 再逐个按 L<混淆名>; 在 app/build/intermediates/combined_art_profile/release/compileReleaseArtProfile/baseline-prof.txt 里查
   # 2026-10-12 实测 306 个类命中 174 个（只算顶层类则 80 命中 47）
   ```
3. **产物**：`unzip -l app/build/outputs/apk/release/app-release-unsigned.apk | grep baseline` 应有 `assets/dexopt/baseline.prof`，旁边还有 `assets/dexopt/baseline.profm`（2026-10-12：12268 + 1629 字节；2026-10-07 是 9120）。

真丢了东西时怎么定位，见附录 A-5。

想**换一次**录制，不是加个 `--rerun` 就行（理由见附录 A-4），要先删旧输出：

```bash
rm -rf \
  baselineprofile/build/outputs/androidTest-results/managedDevice/nonminifiedrelease \
  baselineprofile/build/intermediates/baselineprofiles/nonMinifiedRelease \
  app/src/release/generated/baselineProfiles
```

三条必须一起删。分行写时别漏了行尾的 `\`——漏了只有第一条会被执行，后两条会被当成命令名报错，而结果正好是附录 A-4 说的「以为重录了，其实没有」。

另外：每次成功录制都会改写**入库**的 `app/src/release/generated/baselineProfiles/baseline-prof.txt`（2026-10-12 的提交是 205 增 / 105 删，2026-10-07 那次是 566 / 354）。

**提交前判一次「有没有录浅」**——别看某个类在不在（理由见上面第 2 条），而是与上一次入库版比**规则留存率**：

```bash
git show HEAD:app/src/release/generated/baselineProfiles/baseline-prof.txt > /tmp/old.txt
# 与录制后的新文件逐行取集合，算「旧规则里有多少还留在新集合里」
```

2026-10-12 实测 37537 / 37642 = **99.7%**（配合原始条目 1806 对 1804）。留存率远低于 99% 才说明这次录浅了。这个方法不依赖 R8 的改名与合并，比逐类查稳。

## 当前基线（正常时应该看到什么）

配置：`google_apis` 镜像 + 托管 AVD 手改到 4 核 / 4 GB + `-gpu swiftshader_indirect`。再往上加核/内存没有收益（实测见附录 B-6）。

| 指标 | 2026-10-12 实测 | 说明 |
|---|---|---|
| logcat 里 `lowmemorykiller` **真杀进程** | 0 | **判据**：非 0 就是附录 A-2 |
| 脚本假失败（等不到文案 / 描述符） | 无 | **判据**：有就是附录 A-2 或 A-7 |
| 与上一次入库版的规则留存率 | 99.7% | **判据**：远低于 99% 才是录浅了（正文「怎么跑」） |
| 用例耗时 / 轮数 | 1034.1 s / 12 轮（单轮 86 s） | 2026-10-07 同配置是 343.0 s / 10 轮（单轮 34.2 s） |
| `:app:generateBaselineProfile` 全程 | 17m57s | 同上；变动能到 2.5 倍，**别当判据** |
| 录制结果里的 `com/freshnow` 条目（带标志位口径） | 1806 | 2026-10-07 是 863 |
| 首帧等待（脚本上限 90 s） | 十几秒就过 | 2 核 / 2 GB 时曾三次里两次直接超时 |
| APK 内 `assets/dexopt/baseline.prof`（另有 `.profm`） | 12268（+1629）字节 | 2026-10-07 是 9120 |
| 应用类覆盖（mapping 全量口径，见附录 C-1） | 174 / 306 | 旧口径的「85 / 118」已废弃 |

上表只有标**判据**的三行能单独看健康状态。墙钟会随宿主负载膨胀（2026-10-12 规格没退、单轮却从 34.2 s 变成 86 s），所以「全程变回二十分钟」只能当去查一查的引子：先按附录 A-2 查设备规格，规格没退就别去改录制脚本。

---

# 附录 A：会静默退化的点（含恢复步骤）

这一节的共同特征：出问题时**不报错**，只表现为「更慢 / 覆盖更少 / 什么都没录」。「跑成功」和「录到了」是两件事。

## A-1 录制载体变成混淆构建

- **症状**：录制结果里的应用类条目从上千条（2026-10-12 实测 1806）掉到个位数或 0，最终 profile 里应用类条目整批消失。构建仍然成功。
- **原因**：`nonMinifiedRelease` 变体会被 `initWith(release)` 继承 `optimization.enable = true`（机制见附录 B-3），于是录出来的是混淆名，R8 按原始名改写时查不到，条目在 **`minifyReleaseWithR8`** 这一步被整批丢掉。
- **恢复**：确认 `app/build.gradle.kts` 里那段 `maybeCreate("nonMinifiedRelease") { optimization { enable = false } }` 还在，然后删旧输出重录。

## A-2 设备规格退回默认（2 核 / 2 GB）

- **症状**：logcat 里 `lowmemorykiller` 开始杀进程（正常是 0 次）；脚本以「等不到文案 / 等不到描述符」失败，失败点通常是首帧等待或最后一段 `backToHome()`；全程一般也一起变成二十分钟。
- **但墙钟不是判据**：宿主负载也会把单轮成本抬上去（2026-10-12 实测规格未变，单轮 34.2 s → 86 s、全程 17m57s，而 lmkd 0 次、无假失败、覆盖留存 99.7%）。**先看 lmkd 与假失败这两条**，两条都没有就不是本附录的问题。
- **原因**：内存压不住时，uiautomator 取一次无障碍树会从 ~100 ms 退化成秒级，脚本里 `SHORT_TIMEOUT_MS = 1000` 的短等待就成了假阴性——`backToHome()` 会以为「还没回首页」而连按返回，最后 30 秒也超时。**这是假失败，不是脚本逻辑问题。**
- **什么时候会退回**：AVD 被删或重建、换机器、清掉 `~/.android/avd/gradle-managed`。AGP 没有内存/核数的 DSL，那份 4 核 / 4 GB 是**仓库外的手改**，代码里没有它的影子（机制见附录 B-6）。
- **恢复**：见附录 A-6。
- **顺带**：**别删托管虚拟机来「重试」**。删掉 `~/.android/avd/gradle-managed` 会连带丢掉 `snapshots/default_boot`，下次变成首次冷启动（首次开机还要跑一遍 Provisioning，是这台机器上最慢的一段）。安装偶发 `Broken pipe` 时保留 AVD 直接重跑即可。

## A-3 为了「提速」去动覆盖率的旋钮

两个旋钮都**只降不报错**：

- `optimization.packageScope`：设成具体包之后，范围之外的代码完全不参与收缩与混淆（机制见附录 B-2）。收缩范围该用 keep 规则表达，不要用它去缩小。
- `BaselineProfileConfig` 的 `maxIterations` / `stableIterations`：直接把「轮数 × 单轮成本」里的轮数压小，覆盖率随之下降（见附录 B-6）。

要压时间就压设备规格那一侧，不要动这两个。

## A-4 用 `--rerun` 重录

- **症状**：以为重录了，其实没有；或者整个任务直接 UP-TO-DATE，什么都没发生。
- **原因**：`--rerun` 只作用于命令行上的**最后一个**任务（实测 `a b --rerun` 只重跑 `b`，把顺序对调就只重跑 `a`），所以给 `:app:generateBaselineProfile` 加 `--rerun` 并不能保证重新录制。
- **恢复**：按「怎么跑」里那三条 `rm -rf` 删旧输出。另外设备侧测试失败是正常的 `BUILD FAILED`，不会被当成 UP-TO-DATE，所以**失败之后直接重跑即可，不必先删**——需要删的只有「上一次成功了、想换一次录制」这种情况。

## A-5 profile 没进包时逐级定位

上面那 3 条判据只能告诉你「没进包」，要定位**在哪一步**丢的，就按中间产物顺序逐级比。类声明数在混淆前后本来就会大幅变化（2026-10-12 实测 8785 → 11693 → 4518 → 4518，四个阶段互相差着量级），所以**别拿类总数当判据**——要看「应用类还在不在」，用正文「怎么跑」第 1 条那条命令逐级数（数字应一路持平到 R8 改写那一步）：

```
merged_art_profile/release/mergeReleaseArtProfile/baseline-prof.txt
→ r8_art_profile/release/expandReleaseArtProfileWildcards/baseline-prof.txt
→ r8_art_profile/release/minifyReleaseWithR8/baseline-prof.txt
→ combined_art_profile/release/compileReleaseArtProfile/baseline-prof.txt
```

这套顺序就是用来定位丢弃发生在哪一步的。2026-10-12 实测（统一用 `grep -cE '^[A-Z]*Lcom/freshnow' <该阶段的 baseline-prof.txt>`）：录制结果 1806 → merged 1805 → expandWildcards 1805 → **minifyReleaseWithR8 16** → combined 16。 R8 改写之后**必然**掉到十几条（其余都改成了 `h3` / `i3` 这类混淆名），所以「十几条」本身不是判据；要判的是形态：应用类条目在这里**整批消失**（掉到 0）才是附录 A-1。

## A-6 首次搭建 / 换机器要做的事

1. **系统镜像**：安装 `system-images;android-36;google_apis;x86_64`（为什么是它、什么时候才需要换 `aosp`，见附录 B-5）。
   - 用 `sdkmanager.bat` 装时，包名里的 `;` 会被 cmd 拆成多个参数（报 `Package android-36 not found`）。写成 .bat 文件调用，别在 bash 里直接传字符串。
   - 装完如果 AGP 仍报 `System image does not exist at …`（`retrieveSystemImage` 会重试 5 次再放弃），先 `./gradlew --stop`：**长期存活的 Gradle daemon 会缓存 SDK 包列表**，看不到刚装进去的镜像。
2. **建 AVD**：`./gradlew :baselineprofile:pixel7Api36Setup`。托管设备的 `device` 要取硬件档案的**显示名**（`Pixel 7`），取目录 id（`pixel_7`）会报找不到档案——AGP 的 `AvdManager.createAvd` 只按 `displayName` 匹配。
3. **抬规格**（AGP 没有 DSL，只能手改生成的 AVD）：把 `~/.android/avd/gradle-managed/dev36_google_apis_x86_64_Pixel_7.avd/config.ini` 里的 `hw.ramSize` 改成 `4G`、`hw.cpu.ncore` 改成 `4`（本机现值）。
4. **重建快照**：改完必须重建，否则 `-force-snapshot-load` 会拿到硬件对不上的旧快照。删掉该 AVD 目录下的 `snapshots/`（连 `bootcompleted.ini` 一起删），再 `./gradlew :baselineprofile:pixel7Api36Setup --rerun` 让它重新开机存一份。
5. **核对 GPU 开关**：`gradle.properties` 里的 `android.testoptions.manageddevices.emulator.gpu=swiftshader_indirect` 必须在。默认的 `-gpu auto` 配上 AVD 档案里的 `hw.gpu.enabled=no` 会让 guest 起来后彻底卡死（qemu CPU 时间零增长、`adb shell` 无响应），构建就一直挂在设备操作上。核实办法：`~/.android/avd/gradle-managed/<avd>.avd/emu-launch-params.txt` 里应出现 `-gpu swiftshader_indirect`。
6. **保持 `useConnectedDevices = false`**：本机 adb 上若有真机，「连接设备」模式会把录制跑到真机上——生产侧插件扩展只有 `managedDevices` / `useConnectedDevices` / `skipBenchmarksOnEmulator` / `enableEmulatorDisplay`，没有 serial 项可用来指定设备。

## A-7 上一次失败留下僵尸模拟器

- **症状**：setup 或在设备启动阶段就失败，`baselineprofile/build/outputs/androidTest-results/` 里**连测试结果文件都没有**；而且之后每次重跑都同样失败。
- **原因**：setup 超时（或模拟器启动异常）时，AGP 不会回收它启的模拟器进程。残留的 `qemu-system-x86_64-headless` 会占住 AVD，后续所有运行都受它影响。2026-10-07 试 8 核那次超时后留下的进程**继续跑了 27 分钟、烧了 1508 秒 CPU**，直接让紧接着的 6 核录制白失败一次。
- **恢复**：

  ```bash
  powershell -Command "Get-Process qemu*,emulator* | Stop-Process -Force"
  rm -f ~/.android/avd/gradle-managed/*.lock
  rm -f ~/.android/avd/gradle-managed/<avd>.avd/multiinstance.lock
  ```

  然后重跑。**别一看失败就去改配置**——那一次的「6 核失败」就是这么误判出来的，清掉僵尸后 6 核一次就过。

**成功之后也会残留**：2026-10-12 录制成功后仍看到一个 `emulator` 包装进程活着（当时 CPU≈0，无害）。上面的清理命令很便宜，每次录制之后——成功也一样——跑一遍再开始下一次，别让它和下一次录制抢资源。

---

# 附录 B：为什么这么配（机制与实测依据）

## B-1 release 的 R8 全开

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

实测 `:app:assembleRelease`（2026-10-12）：APK 3557433 字节，`mapping.txt` 里应用类条目 **306 条**（口径：`grep -oE '^com\.freshnow[^ ]* -> [^ :]+'`，含内嵌类与 `$$ExternalSyntheticLambda` 合成类；只算顶层类则是 80 条），应用类被改名（如 `AiAnalysis → g5`），死代码带 `R8$$REMOVED$$CLASS$$` 标记。

注意这个条目数**不能当覆盖率指标**：顶层 Composable 类会被 R8 整体内联、条目直接消失，所以它会随 R8 的内联/合并反向变小（见附录 C-1）。库的保底规则本来就齐全（Room / Compose / CameraX / DataStore 的 consumer 规则都在 `app/build/outputs/mapping/release/configuration.txt` 里），`app/src/main/keepRules/rules.keep` 保持空模板即可。

## B-2 `packageScope` 是 gradual shrinking 的包范围

它**不是**「只优化这些包」，而是 R8 partial shrinking 的**生效范围**，默认值 `**` 即全量。AGP 内部把 `packageScopeEnabled`（= `includePackages != setOf("**")`）接到 `gradualShrinkingEnabled`，把内容接到 `gradualShrinkingPackages`，并受实验开关 `android.r8.gradual.support`（`BooleanOption.R8_GRADUAL_API`，AGP 9.4.1 里默认 true）约束。

本项目踩过一次：当时 `mapping.txt` 里 `com.freshnow` 条目为 0，应用类名全部原样进包。现在的写法（留空）就是修法，别再动它（症状见附录 A-3）。

## B-3 `optimization.enable` 会被 `initWith` 继承

`initWith(release)` 会把新的 `optimization` 块一并复制过去：`BuildType._initWith` 调 `OptimizationImpl.initWith`，后者复制 `enable` 与 `packageScope`。插件里用于关混淆的 `setMinifyEnabled(false)` 在新 DSL 下不再生效——AGP 9 里 `BuildType.isMinifyEnabled` 仍然存在，但它只是与 `optimization.enable` 并存的旧字段，变体侧算是否收缩时读的是新 DSL（`optimizationDslInfo.postProcessingOptions`）。所以关收缩必须写：

```kotlin
maybeCreate("nonMinifiedRelease").apply {
    optimization {
        enable = false
    }
}
```

这就是附录 A-1 的成因。要判断这一步是否生效，用**同一条命令**一路数下去（`grep -cE '^[A-Z]*Lcom/freshnow' <该阶段的 baseline-prof.txt>`）：2026-10-12 实测录制结果 1806 → merged 1805 → expandWildcards 1805 → **minifyReleaseWithR8 16** → combined 16。R8 改写之后**必然**掉到十几条（其余都改成了 `h3` / `i3` 这类混淆名），所以「十几条」本身不是判据，要在意的是它**是不是 0**：整批消失才是附录 A-1。

## B-4 接线：谁是入口，缺了会怎样

- 录制入口是 **`:app:generateBaselineProfile`**（app 侧的聚合任务）。
- 直接跑 `:baselineprofile:pixel7Api36NonMinifiedReleaseAndroidTest` **也会真的录制**：生产侧插件把 `android.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules` 直接设在测试变体上（`addEnabledRulesInstrumentationArgument` 默认为 true，只有显式带 `-Pandroidx.baselineprofile.dontdisablerules` 才不设）。这样跑丢的是**后面的 collect 步骤**：`intermediates/baselineprofiles/` 里不会落 `.txt`，等于白录。
- app 侧必须应用 `androidx.baselineprofile` 插件（同一个插件在 app 侧是消费方，负责合成构建类型、补 `<profileable>`、签名兜底），并声明依赖：

```kotlin
dependencies {
    baselineProfile(project(":baselineprofile"))
}
```

缺了依赖不会报错，只会静默没有 profile：`MergeBaselineProfileTask` 会打一条 `variantHasNoBaselineProfileDependency` 告警（可以用 `baselineProfile { warnings { … } }` 关掉）。缺了 app 侧的插件，`benchmarkRelease` / `nonMinifiedRelease` 变体根本不会生成。

## B-5 镜像：选 `google_apis` 是覆盖率取舍，不是速度取舍

同一台机器、同样 4 核 / 4 GB 下把镜像换掉，只值 **约 12% 耗时**（2026-10-07 实测 6m24s → 5m38s），却少覆盖 **40 个「含方法的类」**：`androidx/emoji2/text`(+flatbuffer)、`androidx/core/provider` 与 `androidx/core/graphics` 的可下载字体 provider 路径等。这些分支要先探测只有 GMS 才提供的字体 provider，`aosp` 上在入口就返回了。两种镜像在应用类与 `androidx/camera` 上的覆盖**没有差别**（当时记的类数是 244 / 584；应用类那个数用的是已废弃的旧口径，见附录 C-1）。

官方文档（`Create Baseline Profiles`）说 GMD 录制要设 `aosp`，理由是生成器需要 root——**那条只对 API < 33 成立**（同一文档也写了 API 33 及以上无需 root）。本项目 `apiLevel = 36`，实测 `google_apis` 能正常录制。要换成 `aosp` 就是 `systemImageSource = "aosp"`（AGP 里 `"aosp"` 与 `"default"` 同源，SDK 包是 `system-images;android-36;default;x86_64`）。

## B-6 耗时由「轮数 × 单轮成本」决定，两个因子都来自别处

- **轮数**由 `BaselineProfileConfig` 决定，默认 `maxIterations = 15`、`stableIterations = 3`：脚本会被完整跑到「连续 3 轮 profile 不再增长」或跑满 15 轮为止。所以总耗时不是常数。
- **单轮成本**取决于设备（冷启动 + 六条路径 + 每轮一次 ART profile reset/compile），而设备规格受 AGP 限制：AGP 把 `hw.cpu.ncore` 写死成 `EmulatedProperties.RECOMMENDED_NUMBER_OF_CORES`，并用 `restrictDefaultRamSize()` 把 `hw.ramSize` 顶到 `MAX_DEFAULT_RAM_SIZE = 2 GiB`（不管硬件档案要多少）；`ManagedDevice` 也没有相关 DSL。唯一入口是 AVD 首次生成后改 `config.ini`——`createOrRetrieveAvd` 发现 AVD 已存在且状态正常就直接复用，所以改动能留住（步骤见附录 A-6）。

把这套变量拆开实测的结果（**同一天连测**，同一套脚本、同一台机器，所以三列之间可比；跨天的墙钟不可比）：

| | `google_apis` 2 核 / 2.5 GB | `google_apis` 4 核 / 4 GB | `aosp` 4 核 / 4 GB |
|---|---|---|---|
| 全程 | 20m13s | 6m24s | 5m38s |
| 用例耗时 / 轮数 | 1002.6 s / 跑满 15 轮 | 343.0 s / 10 轮 stable | 317.6 s / 10 轮 stable |
| `lowmemorykiller` 杀进程 | 9 次 | 0 次 | 0 次 |

**结论：瓶颈是内存/核数，不是镜像。** 同一镜像只抬规格就是 3.2 倍加速、假失败归零、轮数从「跑满 15」变成「10 轮收敛」；镜像只值那 40 个 GMS-only 类和约 12% 耗时。

再把这两个因子往上推就是收益递减了：

- **核数**：4 核 → 6 核，单轮从 34.2 s 降到 33.4 s（UI 段 19.3 s → 17.8 s，而那个无选择器的「空档」15.7 s 几乎不动），全程 6m24s → 5m58s。而且**再想上 8 核直接被模拟器拒了**——它把 `hw.cpu.ncore` 从 8 自行压回 6，并让 setup 任务超时（症状与后遗症见附录 A-7）。
- **内存**：4 GB 下 `lowmemorykiller` / `kswapd` / `Compaction` / `onTrimMemory` **全是 0 次**，说明已经离开回收压力区，再加内存没有可修的东西；何况这台宿主只有 16 GB，8 GB 客体会反过来挤宿主。
- 所以 **4 核 / 4 GB 就是这台机器上的甜点**，别再往上加。「多核能更快」只在那次 2 核 → 4 核成立，而那次本质上是脱离 thrashing（lmkd 从 9 次到 0 次），不是线性并行收益。
- **单轮成本还会随宿主负载浮动**：2026-10-12 实测同一套配置、规格未变，单轮 34.2 s → 86 s（12 轮 / 1034.1 s，全程 17m57s）。所以上表只在同一天内可比；跨天比耗时之前，先看 `lowmemorykiller` 与脚本假失败（附录 A-2）。

## B-7 脚本与那条 startup profile 告警

录制脚本在 `baselineprofile/src/main/java/com/freshnow/app/baselineprofile/BaselineProfileGenerator.kt`，覆盖六条路径：首次启动的欢迎页（引导，只在第一轮出现）→ 冷启动首页 → 扫描页（含 CameraX，靠 `pm grant` 过权限门）→ 设置页 → 同步页 → 关于页 → 开源声明页。控件一律按文案与描述符定位，不用坐标。首次界面的等待放宽到 90 秒（非混淆构建冷启动明显慢于 release），但设备被 `lowmemorykiller` 拖住时 90 秒也可能不够，见附录 A-2。

脚本没有设 `includeInStartupProfile`，所以插件每次成功都会打一条 `No startup profile rules were generated for the variant 'release'`——这是默认行为（`BaselineProfileConfig.includeInStartupProfile` 默认 false），不是故障。要 startup profile 得在规则里显式设 true。

**脚本在结构上到不了的地方不要再去想办法**：录制机每一轮都是重装后的干净包，凡是「库里得先有记录或照片才进得去」的界面就永远录不到（`HomeRecordItem`、`ScanThumbnailKt`、看图页的 `PhotoViewerKt`），从不真的扫描所以 `ScanStatus$Analyzing` / `$Failed` / `$NotConfigured` 也走不到。要覆盖只能先给录制器种数据，而 release 包不可 `run-as` —— 结论是**接受不覆盖**，别为此改脚本或加调试钩子。

---

# 附录 C：已证伪的旧结论（别再写回去）

## C-1 「应用类覆盖 100%」不成立，且「118 个应用类」这种数字没有意义

「应用类条目数」不能当覆盖率指标：R8 会把**顶层 Composable 类整体内联**，这类类在 `mapping.txt` 里没有独立条目（`PhotoViewerKt`、`WelcomeScreenKt` 都是），条目数会随 R8 的内联/合并**反向变小**。要数就把口径写死（正文「怎么跑」第 2 条）：

```bash
grep -oE '^com\.freshnow[^ ]* -> [^ :]+' app/build/outputs/mapping/release/mapping.txt
# 逐个按 L<混淆名>; 在 combined_art_profile/release/compileReleaseArtProfile/baseline-prof.txt 里查
# 2026-10-12 实测：306 个类命中 174 个（只算顶层类则 80 命中 47）
```

**别按类名逐个查**：被内联掉的那些会给出假阴性，数出来偏低。

命中的差额就是脚本到不了的地方，这部分在结构上补不齐（原因见附录 B-7）：`RecordDetailViewModel` / `RecordDetailUiState`、需要已有记录才有数据的 `HomeRecordItem` / `ScanThumbnailKt` / 看图页相关类、以及 `ScanStatus$Analyzing` / `$Failed` / `$NotConfigured`（脚本从不真的扫描）。所以 100% 不可能达到，**「差额是哪几类」比「命中率是多少」有意义**；命中率只用来和上一次入库版比有没有突然变低。

## C-2 「设备侧测试失败仍报 BUILD SUCCESSFUL」不成立

实测两次设备侧失败（一次挂在最后一段 `backToHome()`，一次挂在首帧等待）都是 `> Task :baselineprofile:…AndroidTest FAILED` + `BUILD FAILED`。失败不会被当成 UP-TO-DATE。真正会「报成功却没结果」的是别的路径：直接跑测试任务丢了 collect 步骤（附录 B-4），或者任务本来就已经 UP-TO-DATE（附录 A-4）。

## C-3 「直接跑测试模块的 `*AndroidTest` 会被 assumption 静默跳过」不成立

实测直接跑 `:baselineprofile:pixel7Api36NonMinifiedReleaseAndroidTest --rerun` 会真的进入录制（`BaselineProfileRule.collect` → 脚本里的 UI 等待），失败也是真的 UI 等待失败。原因见附录 B-4：`enabledRules` 参数本来就设在测试变体上。

---

# 附录 D：已知遗留

- release 包仍是未签名的（`app-release-unsigned.apk`），需要发布密钥才能安装与上架。
- `app/src/main/java/com/freshnow/app/ui/scan/ScanViewModel.kt` 里 `e::class.java.simpleName` 在混淆后会把混淆名显示到界面上（仅在异常 `message` 为空时走到该分支）。
