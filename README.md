[README.md](https://github.com/user-attachments/files/30207928/README.md)
# Simes 1.3.5

Simes（Integrated Mod Enhancement Suite）是由 **7imes** 为 SimMC 开发的 Minecraft 1.21.8 Fabric 客户端整合增强模组。

- 项目主页：https://github.com/Nov7imes/Simes
- 适用服务器：`play.simmc.cn`
- 市场数据来源：Simall
- 无需安装在服务端

## 安装

1. 安装 Minecraft 1.21.8、Fabric Loader 和对应版本的 Fabric API。
2. 删除旧的 Simes、SimPriceDisplay 和独立 AutoMessage JAR，避免重复加载。
3. 将 `Simes-1.3.5-mc1.21.8.jar` 放入客户端的 `.minecraft/mods/`。

## 市场价格与坐标

- 在物品栏、背包或容器中悬停物品，即可在提示框底部查看最低出售、最高收购、店铺、港口、库存、数据更新时间和 Simall 数据来源。
- 使用游戏内真实显示名称匹配物品，支持由原版物品加 NBT/组件制作的插件物品；优先精确匹配，唯一且可信的模糊结果也可使用。
- 默认悬停后按 `C` 复制最低出售店铺坐标，按 `V` 复制最高收购店铺坐标；可在“选项 → 控制”中改键，也支持鼠标键。
- 市场数据每 10 分钟尝试更新，支持 gzip、ETag 和 HTTP 304。下载、解析或校验失败时继续使用上次有效缓存。
- 功能只在 `play.simmc.cn` 启用，缓存位于 `.minecraft/config/simes/`。

## 背包与仓库价值

- 打开背包时，在 GUI 右下区域显示“背包价值”和“今日收益”。
- 物品提示框使用更高显示层级；即使与价值面板重叠，物品说明也不会被面板遮挡。
- 打开箱子、木桶及服务器双箱容量容器时，显示仓库价值、随身背包价值、总计、已估价与未估价格数。
- 价值按“物品数量 × 最高收购单价”计算；无收购价的物品不计入。精确匹配和可靠模糊匹配都会参与估值。
- 盔甲、快捷栏和副手物品计入背包价值。
- “开关背包/仓库价值显示”快捷键可在游戏控制设置中修改，状态会保存。

### 多箱累计 `/cal`

- 输入 `/cal` 开始记录，再次输入 `/cal` 结束并在本地聊天输出箱子数量和总价值。
- 每关闭一个容器，显示其序号、X/Y/Z、该箱价值和当前累计。
- 使用维度与方块坐标去重；大箱左右两半视为同一容器，木桶使用自身坐标。
- 记录只保存在本次客户端会话中，不会上传。

## 余额与今日收益

- 登录目标服务器 3 分钟后首次自动查询 `/bal`，之后每 10 分钟更新。
- 只隐藏 Simes 自动查询产生的余额回复，玩家手动执行 `/bal` 时仍正常显示。
- 今日收益按“最新余额 − 当日本地基准余额”计算；同一天重复登录会沿用当天基准，日期变化后自动重建。
- 查询失败时保留最后一次有效余额，并在下一周期重试。

## 奥术冷却 HUD

- 输入 `/simes` 或 `/simes hud` 打开设置，也可在“选项 → 控制”绑定 Simes HUD 设置键。
- 可选择保留原版 Action Bar，或使用 Simes 自定义奥术冷却 HUD。
- 自定义模式支持多个奥术同时显示、20 个奥术图标、法术专属加粗配色、平滑倒计时、进出场和补位动画。
- 同一次冷却按单调递减处理，过滤临近 0 秒时乱序到达的旧数据，避免数字回弹。
- 会识别并隐藏服务器盾牌产生的空白/红格 Action Bar；混合数据会先剥离盾牌部分，再读取奥术冷却。
- 在 HUD 设置中点击“HUD 布局与缩放”即可同时看到奥术 CD 和自动消息 HUD：点击框选择，拖动框定位，底部可按 10% 调整所选框，范围 50%–200%，也可一键重置全部。
- HUD 配置保存于 `.minecraft/config/simes/hud.json`。

## 自动消息

- 按默认 `O` 键或输入 `/atmsg` 打开设置；快捷键可在控制设置中修改。
- 设置消息/命令及 1–6400 秒发送间隔，然后点击“保存并返回”。以 `/` 开头的内容按命令发送，否则按聊天消息发送。
- 输入 `/atmsg st` 启动，再次输入则停止。
- 运行期间倒计时 HUD 始终显示，停止后隐藏，不占用 Action Bar。
- 倒计时使用持久化实际时间戳，断线重连后继续；到期后会在玩家和网络可用时发送一次并开始下一周期。
- 旧 `.minecraft/config/automessage.json` 会迁移至 `.minecraft/config/simes/automessage.json`。

## Debug 日志

- 输入 `/debug start` 开始记录，复现问题后输入 `/debug end` 结束；单次最多 60 秒。
- 记录服务端 Action Bar 数据包、系统提示、主副手物品及组件、状态效果、经验和界面/价值面板状态。
- 日志位于 `.minecraft/config/simes/debug/`，文件名精确到秒，仅保存在本地，不自动上传。

## 构建与公开包

需要 Java 21：

```text
gradlew.bat clean build
```

正式发布包使用 Fabric Loom 生成的标准 `remapJar`，不进行代码混淆，以保证 PCL、Fabric Loader 和其他模组的兼容性。

## 作者与许可

- Simes / AutoMessage 作者：7imes
- Simes：MIT License
- 内置 AutoMessage 部分：CC0-1.0（许可证作为 `LICENSE_automessage` 保留在 JAR 中）
