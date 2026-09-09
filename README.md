# project-0013-真忠诚-三叉戟附魔

## 功能

为 Paper 1.21.11 增加自定义附魔「真·忠诚」：

- 附魔台三叉戟出现「忠诚」选项时，有 50% 概率替换为「真·忠诚」（等级 1）。
- 创造模式附魔书栏会自动出现「真·忠诚」附魔书，可直接拿取，和普通附魔书一致。
- 持有真·忠诚三叉戟受到致命伤害时，三叉戟像不死图腾一样碎裂消耗，屏幕中央播放原版不死图腾动画，获得吸收/生命恢复/抗火/抗性保护。
- 触发后 30 把幻影三叉戟从玩家身边球面展开，弧形升空汇聚，自动追踪附近敌对生物；如果致命伤害来自某个玩家，该玩家会成为复仇目标。
- 命中产生爆炸粒子与不破坏地形的闪电效果（effect-only），结束后三叉戟永久消失并显示结束文字。

## 命令

- `/trueloyalty give [玩家]`：给一把真·忠诚三叉戟
- `/trueloyalty book [玩家]`：给一本真·忠诚附魔书
- `/trueloyalty test [玩家]`：直接触发技能动画（测试用）
- `/trueloyalty reload`：重载 config.yml

权限：`trueloyalty.admin`（默认 OP）。

## 数据包

插件启动时自动把 `datapack/true-loyalty` 写入每个世界
`datapacks/true-loyalty`，需要重启服务器一次使附魔注册生效。

## 构建

```powershell
.\gradlew.bat build
```

本机 Gradle 自带 test worker 存在环境问题（旧工程同样报
`ClassNotFoundException`），测试用 `.\gradlew.bat runTests` 执行即可，
已验证 3/3 通过。

产物：`build/libs/TrueLoyalty-1.0.0.jar`，部署到
`D:\MC\server\[25568]lifesteal生存\plugins\`。
