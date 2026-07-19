Simes

面向 Minecraft 1.21.8 Fabric 客户端的 Simall 市场价格与自动消息工具。

## 市场价格

- 仅连接 `play.simmc.cn` 时启用价格下载与提示框显示。
- 使用物品在游戏中的实际显示名称匹配，兼容由原版物品与 NBT/组件改造的插件物品。
- 精确匹配优先；模糊匹配只有在结果唯一且置信度足够高时才使用。
- 显示最低出售、最高收购、店铺、港口、库存、商店 X/Z 坐标、数据更新时间与数据来源。
- 默认悬停物品后按 `C` 复制最低出售坐标，按 `V` 复制最高收购坐标。
- 两个复制键均可在“选项 → 控制”中修改；若绑定为鼠标按键也可使用。
- 剪贴板格式为 `X Z`，例如 `-1500 3200`，复制成功后会在动作栏提示。

数据直接读取：<https://s.eabal.com/BCShop/data/price_index.json>

客户端每 10 分钟尝试更新一次，支持 gzip、ETag 与 304。新数据必须完整解析并通过规模校验后才会原子替换缓存；请求失败或数据损坏时继续使用上一次有效缓存。缓存位于 `.minecraft/config/simes/`。

当前 Simall 线上索引可能尚未部署文档中的 `x/z` 字段。缺少坐标时价格功能照常工作，提示框会显示“等待 API 提供”；接口补齐字段后无需更新 Simes 即可自动启用复制。

## 自动消息

Simes 内置 AutoMessage 1.0.5 的全部功能：

- 默认按 `O` 打开自动消息设置界面；按键可在游戏控制设置中修改。
- 可设置消息或以 `/` 开头的命令，以及 1～86400 秒发送间隔。
- 设置界面支持保存、启动/停止和立即发送。
- 客户端命令保持不变：
  - `/automsg`、`/automsg status`
  - `/automsg start`、`/automsg stop`
  - `/automsg sendnow`
  - `/automsg interval <秒数>`
  - `/automsg message <内容>`
- 原配置 `.minecraft/config/automessage.json` 保持兼容，无需迁移。
- 与原版一致，启用状态不会跨断线或重启保留，消息和间隔设置会保留。

AutoMessage 原实现由 `ni` 发布为 CC0；其许可证随 JAR 一并保留为 `LICENSE_automessage`。

## 安装

1. 安装 Minecraft 1.21.8、Fabric Loader 与对应版本 Fabric API。
2. 从 `mods` 文件夹移除旧的 `SimPriceDisplay` 和 `AutoMessage` JAR，避免功能重复。
3. 将 `Simes-1.0.0.jar` 放入客户端 `.minecraft/mods/`。

本模组不需要安装在服务器端。

## 构建

需要 Java 21：

```text
gradlew.bat build
```

构建产物位于 `build/libs/`。

## 作者与许可

- 作者：7imes、Codex
- AutoMessage 贡献：ni
- 价格数据来源：Simall
- Simes 代码：MIT
- 内置 AutoMessage 部分：CC0-1.0
