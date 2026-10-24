# FreshNow 项目规范

## 1. 图标

### 防踩坑

Material Symbols 的 SVG 官方坐标系是负 Y，且 `viewBox` 从 -960 开始；而 Android vector 的视口固定从 0 开始。

若直接把 SVG path 抄进 vector drawable，图形会整体画在视口外；编译不报错，但图标不显示。

### 规范

- 图标命名为 `ic_用途.xml`。
- 图标应统一为 rounded 变体。
- 若发现图标为 outlined 或 sharp 变体，应停止任务并告知用户。

### 获取

- 用户会从 Material Symbols 中提供项目所需的图标。
- 所有图标均会被下载至 `C:\Users\Administrator\Downloads`。
- 禁止私自下载图标；若用户要求开发某项功能，但却未提供图标，则应暂停任务并告知用户。

## 2. 设计规范

项目所使用的设计语言为 Material Design。

在设计界面时，需遵循 Material 3 技能的规范，路径为 C:\Users\Administrator\.pi\agent\skills`。

## 3. 更新日志

项目包含一个更新日志文件，路径为 `C:\Users\Administrator\AndroidStudioProjects\FreshNow`，文件名为 `更新日志.txt`。

更新日志中有五个条目，依次为：新增、改进、修复、调整、移除。

每次完成代码修改后，都需将相较于上个已发布版本的净更改写入日志中。

具体风格见历史更新日志。

## 4. 调试

- 应使用虚拟机进行常规调试，真机调试可能面临未知问题。

  > 禁止在真机上进行 baseline profile 录制。

## 5.  编译
- 测试时默认编译的包为debug包；而debug包的包名带有 .debug 后缀。