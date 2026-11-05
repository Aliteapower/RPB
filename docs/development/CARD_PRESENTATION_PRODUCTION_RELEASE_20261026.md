# 顾客持卡二维码生产发布 — 2026-10-26

## 版本与目标

- 功能提交及前后端线上版本：`3f78ebc5bc0dc29803b17e8ab3802f411402b243`，已推送 `origin/codex/paynow-payment-product-line-staging`。
- 项目 `D:\RPB`，服务器 `43.134.69.75`，服务 `rpb-backend`，数据库 `rpb_acceptance`。
- 不可变制品目录 `/opt/rpb/releases/20261026-card-presentation-3f78ebc5-r2`；实际服务运行 `/opt/rpb/app/reservation-platform.jar`，前端发布到 `/opt/rpb/frontend`。
- 无迁移；Flyway 保持 `079|79|0`。制品从功能提交的干净 git archive 构建。

## 备份与回退

完整备份 `/opt/rpb/backups/20261026-card-presentation-3f78ebc5-r2-before` 包含旧 JAR、前端、SHA 标记及 12,456,522 字节数据库 dump，pg_restore 目录校验通过。首轮备份及日志也保留。

首轮 QA 写入验收被 STORE_APP_NOT_ENABLED 拒绝，原因是既有 QA 门店钱包产品关闭；保护性回退已成功，尚未更新前端入口。修正验收脚本，仅临时开启该 QA 门店钱包及卡功能，完成后恢复原值。未修改业务代码或真实门店开关。首轮 QA 准备卡保留，无核销分录；没有删除验证事实。

如后续需回退，恢复本次备份的上一版应用与前端；保留现有数据库和所有业务交易，不回退到不支持存酒 NULL 价格的旧版本。

## 验证

- 沿用实现阶段卡模块 22 项 PostgreSQL 回归、规则/架构各 2 项及最终 7 项定向安全复核；干净源码再次执行前端 9 项测试、vue-tsc/Vite 构建及 Maven package 均通过。
- 新旧 JAR 的依赖、配置和历史迁移字节完全一致；后端差异仅卡 DTO、核销服务及顾客卡控制器。
- 在独立 QA 租户 `43d4b2f4-b3f6-4883-9e6e-474de2985011` 新建验收卡 `d093631c-fd75-48cd-97c5-68d906f31fd0`，无银行操作，无真实支付：出码时剩余 3 次不变；商户核销后剩 2 次；同键重试返回原结果；新键重复扫码拒绝，只有一笔核销分录；顾客自行 confirm 被 403 拒绝；顾客读取状态为 completed。
- 钱包、目录、条码和存酒读取及隔离 20 项兼容检查通过，旧 QA 卡剩余 28 次、钱包余额 3,800 分保持不变。
- QA 门店开关恢复，测试会话撤销；保留 QA 审计与业务记录。
- 148 项业务表/配置指纹一致。比较排除上述 QA 租户数据以及会话、审计等易变表；不宣称全库逐字节一致。
- 四个生产域名共 32 个页面入口与新 index 一致；18 个发布资源内容一致；40 项匿名 API 检查均为 401，包含新增出码接口和商户核销接口。
- `rpb-backend` active，PID `1778698`，InvocationID `b741f02cb4b844449657af88f10f6ec3`，观察期间稳定、无新增 ERROR。nginx、PostgreSQL、Redis、Docker 的状态及进程身份未变。
- 原有 12 项本地无关改动按 SHA256 保留，未纳入提交。

## 制品

| 制品 | SHA256 |
|---|---|
| JAR | `385baa261e7d40ddcb3ec7990d0eda190f6169686da235c68b4567ee053a48e4` |
| 前端归档 | `812135dbb335e72d182562c7ffbbcc5f4c1712b244588071d6e5f66e2674100a` |
| index.html | `7785390851336f604873d7d15e480e696ea246f0ced5eb3c23a3ec7d0a1859a9` |

## 结论与限制

Production readiness：GO，已部署。手机相机、微信/iPhone/Android 实机扫码和移动端视觉仍待设备验收；本次证明真实生产 API 的授权与核销行为，不冒充设备扫码验证。

本地与服务器证据：`target/card-presentation-release-20261026/` 和 `/home/ubuntu/card-presentation-release-20261026/`。后续文档提交不改变线上功能制品 SHA。
