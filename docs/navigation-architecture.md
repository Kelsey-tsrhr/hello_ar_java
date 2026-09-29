# 室内导盲导航代码结构

## 主流程

```text
用户点击/语音
       |
       v
NavigationActivity  -- 只负责生命周期、UI 和流程编排
       |
       +--> NetworkRepository.queryRelativeMap(jpeg)
       |          |
       |          v
       |    RelativeLocalizationResponse
       |          |
       |          v
       |    NavigationMapBuilder
       |          |
       |          v
       |    AR 世界 Node 列表
       |
       +--> NavigationPathPlanner --> NavUtils(A* / 图算法)
       |
       +--> NavigationEngine(逐帧：到点、转弯、偏航、朝向)
       |
       +--> MultimodalInteractionManager(TTS / 触觉 / 日志)
       |
       +--> PathRenderer / PointRenderer(AR 绘制)
```

## 偏航后的重新规划

`NavigationEngine` 检测到横向偏离超过阈值后，仍然先发送原有偏航提示；随后由 `NavigationActivity` 执行重规划：

1. 保留当前目标节点 ID。
2. 使用最新的 `lastArPose` 作为当前位置。
3. 重新调用 `NavigationPathPlanner` 和 `NavUtils` 的图搜索流程。
4. 成功后替换 `currentPathNodeIds`，重置 `NavigationEngine` 的进度游标并重建 AR 路径。
5. 失败时保留旧路线继续引导。

重规划设置了 8 秒冷却时间，避免用户持续偏航时重复计算和重复播报。当前重规划仍然使用现有的几何节点图和 A*，不会把障碍物临时加入图中。

## 楼道模式与低频提醒

`CorridorModeController` 将路线方向、转弯窗口、固定路线段横向偏移和 Depth 空间证据合成为 `NORMAL` / `CORRIDOR` 状态：

- 路线方向与相机前向基本同向，且不接近转弯时，才允许进入楼道模式。
- `DepthCorridorAnalyzer` 从同一帧 Depth 图像采样左右边界和中央前方空间；Depth 暂时不可用或质量不足时退化为路径判断。
- 进入和退出都需要连续采样确认，避免单帧深度噪声造成状态抖动。
- 楼道模式只抑制重复的朝向和轻微偏航提示，转弯、到达、障碍物和严重偏航仍然保留。
- 导航引擎使用“上一已通过路径节点 → 当前目标节点”作为固定偏移基准，不使用每帧变化的相机首点计算路线偏移。

## 窄门候选检测

`DepthDoorwayAnalyzer` 不把 ARCore 当作门的语义识别器，而是利用中部多条水平扫描带寻找“左右两个近距离的竖直门框 + 中央连续远距离开口”。必须同时看到左右门框，但不要求门顶或门底进入画面。它结合 `CameraIntrinsics` 将开口的水平像素跨度换算为米，并暂定 `0.55m` 到 `2.0m` 为窄门范围。

只有至少两条水平扫描带同时满足条件，且 `NarrowDoorAlertController` 连续确认两帧后，`MultimodalInteractionManager` 才播报一次窄门提示。Depth 不可用、质量不足、开口过宽或当前证据消失时不会播报；因此该功能是导航安全辅助，不等同于完整的门检测模型。

Debug 构建的主界面提供“窄门测试”按钮。开启后直接复用当前 ARCore 相机帧和 `acquireDepthImage16Bits()` 的真实 Depth 图像，只显示检测结果，不调用定位接口、不上传图片、不触发服务器日志。关闭按钮即可恢复普通界面；该入口和结果面板均可独立删除。

设置中的“导航辅助设置”提供两个持久化开关：窄门识别和楼道低频提醒模式。窄门识别关闭后不会运行窄门连续帧确认或播报；楼道模式关闭后恢复普通导航提醒。窄门确认后的固定安全播报为“前方有窄门，请小心行走”。

设置中的“无障碍模式”默认关闭。开启后，主界面按钮使用至少 `64dp` 高的触控区域和 `20sp` 文字，并补充明确的内容描述：单击只朗读按钮名称和“双击执行”提示，双击才触发原有操作。状态文本使用无障碍实时区域，便于 TalkBack 朗读；该模式不改变颜色，关闭后恢复原有控件尺寸。

## 目录职责

| 目录 | 职责 |
| --- | --- |
| `api` | Retrofit 接口和云端 DTO，不放 UI 或导航算法 |
| `interaction` | 无副作用的输入解析，例如语音命令分类 |
| `navigation` | 地图投影、路径计划等导航领域编排 |
| `managers` | 具有生命周期或外部资源的模块：网络、TTS、录音、视觉、触觉、导航引擎 |
| `math` | 坐标变换等纯数学组件 |
| `render` | OpenGL 绘制 |
| `utils` | 通用算法工具，目前 `NavUtils` 内部包含图构建和 A* |
| `view` | 自定义 Android View |

## 线程和资源边界

- `onDrawFrame` 运行在 GL 线程，只读取当前 AR 帧并提交轻量任务；不要在网络回调里直接操作 `Session` 或 OpenGL 对象。
- UI 状态、按钮、Toast、对话框和 Activity 字段更新放在主线程。
- Retrofit 回调默认回到 Android 主线程；云端返回后由 Activity 更新地图状态，再由下一帧读取。
- 楼道 Depth 分析复用 Activity 当前帧获取的 Depth 图像，不依赖 EfficientDet 目标检测模型是否就绪。
- 本地障碍检测在 `LocalVisionObstacleDetector` 的单线程执行器中运行；相机图像和深度图必须由拥有它们的模块关闭。
- `TTSManager`、`VoiceRecognitionManager`、`LocalVisionObstacleDetector` 和 ARCore `Session` 都由 Activity 在生命周期结束时释放。

## 当前状态对象

目前 Activity 仍持有定位、目标、路径和渲染快照，这是下一轮可继续收敛的重点。建议后续引入一个 `NavigationState`，集中保存：

- 是否已定位、是否导航中；
- 当前目标和当前路径节点 ID；
- 云端相对节点和 AR 世界节点；
- Anchor / capture pose 的有效性。

这样可以把 `performRelocalization`、`rebuildNodesFromBasePose`、`rebuildLiveNavPath` 进一步从 Activity 中移出，同时保持 ARCore 生命周期仍由 Activity 管理。
