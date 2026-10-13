# FreshNow 动效待办

设计源是 `app/src/main/java/com/freshnow/app/ui/theme/Transitions.kt`，界面只引用、不另定义取值。

已经铺好的四条通道：**页面切换**、**页内状态切换**、**弹簧**（接手势的那类运动）、若干一次性时长。
下面这些属于还没有通道的两类：**「用户在等」** 与 **「一个值变了」**。

一次做一条；做完一条就从这里删掉（改动本身记在 `更新日志.txt`）。

---

## 1. 照片从无到有是硬跳

详情页占位图 → 照片（`ui/component/ScanPhoto.kt` 无淡入），编辑页换照片同理。给一档淡入。

## 2. 列表缩略图解码完就冒出来

`ui/component/ScanThumbnail.kt`，滚动时像一格一格地闪。解码完成时淡入。

注意判据：只演「刚出现的」，滚动中重新进视口的旧图不该也淡一遍（同 `HomeScreen` 的 `appearFadeIn`）。

## 3. 拍照页按下快门没有任何确认

`ui/scan/PhotoCaptureScreen.kt`，按下去要等下一帧回来才整页退场，中间那几十到几百毫秒什么都没发生，
用户会怀疑没按到。按下即给一次可见的回执。

## 4. 两个跟手页面对「松手」的处理不一致

看图有回弹（`PhotoViewer.kt` 的 `fastSpatial`），裁剪页没有（`ImageCropScreen.kt` 在拖动中就夹住了，
既无过冲也无回弹）。同一套 `transformable`，两种手感。二选一并把理由写进注释。

## 5. 同步页的状态整块硬切

`ui/sync/SyncScreen.kt`：搜索中 → 发现 N 台 → 已配对。沿用 `fadeSwap` 与 `animateItem`。

## 6. 瞬时反馈走的是系统 Toast

`ui/Toast.kt` 用的是 `android.widget.Toast`：位置、时长、长相都不由应用控制，是全应用唯一一处
「反馈不是自己画的」。自绘会牵动每个调用点，是这份清单里最重的一条。

## 7. 下拉刷新的指示器是默认的

`ui/home/HomeScreen.kt` 用的 `PullToRefreshDefaults.Indicator`，颜色没跟主题走。

## 8. 关于页整页静态

`ui/about/AboutScreen.kt`。价值最低，可以一直放着。

---

## 已经排除的（别再往这几个方向走）

- **在标签位置上直接浮现字段**（AR 那种）：模型只返回日期字段、没有坐标。要做成那样得改协议，
  还得赌模型给的框准不准。
- **思维链逐字浮出**：`AiVisionClient` 是一次性读完整个 body，没有流式，所以现在只能整段淡入。
- **`ScrollEdgeFade` 不做淡入淡出**：有意为之，理由在它自己的注释里。
