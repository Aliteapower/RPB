# 商户消费出码与积分兑换码审查

## Scope
D:\RPB；开发测试，未提交、未推送、未部署。原有扫码收款、手机号充值与积分入口保留。

## API Review Report
- 消费付款复用既有 bills、bill-code、quote/pay，不新增资金扣款实现。
- 新增 points-redemptions 意图创建、状态查询、出码、取消及本人确认接口；见 docs/api/2026-10-24-wallet-merchant-qr-contract.md。
- 员工依原 wallet.points.view/redeem 权限、AppGate、门店范围；顾客依真实会话及绑定顾客归属。前端隐藏入口不能替代后端授权。
- 新控制器接入 WalletPointsErrorAdvice，版本冲突、余额不足、权限、无记录、非法请求有稳定错误。写请求保留同源与 X-RPB-Wallet 校验，local 仅精确开放声明的方法路径。
- 意图不含明文令牌；WR 出码只存 SHA-256，120 秒，刷新旧码失效；读取和扫码均不扣分。顾客显式确认必须提交版本和幂等键。
- 请求行锁、原积分命令幂等与同一数据库事务保证不同幂等键的并发确认也只产生一笔兑换；完成后重试指向原 entry。等待积分账户锁后再次检查有效期，过期则整笔回滚。

## Database Review Report
- V065 只新增 wallet_points_redemption_requests，不重写既有余额、流水、历史数据。
- tenant/program/store/customer 外键、正整数、状态及完成 entry/时间一致约束；码散列唯一索引与租户门店顾客时间索引。
- 每次意图查询均同时限定 tenant/program/store；完成 entry 保留同租户 FK。状态变更以既有 wallet audit 留痕。
- 积分扣减及兑换由 loyalty 模块承担，钱包适配器协调既有权限与开关；意图不作为第二套积分账本。待确认／取消／过期意图不计支出，完成 entry 自动进入统一交易记录。
- 回退：先回退应用，保留附加意图表及积分历史；不能通过删兑换流水或回写余额撤销业务，必要时使用既有冲正。

## TDD Review Report
| 场景 | 证据 |
|---|---|
| 扫码不扣分、本人确认、资金独立、相同或不同键重试 | WalletPersonalPaymentPostgresTest |
| 取消、120 秒过期、刷新旧码、版本改变、余额不足、积分停用、越权及跨范围 | WalletPersonalPaymentPostgresTest |
| 不同键并发只生成一次兑换 | WalletPersonalPaymentPostgresTest |
| 消费 WB 扫码后原 quote/pay 确认扣款 | WalletPersonalPaymentPostgresTest |
| 身份校验、精确方法、同源请求头、非法数量与顾客覆盖字段 | WalletLocalRuntimeSecurityTest |
| 商户消费／积分出码、顾客确认及扫码类型分流 | wallet-marketing-points / wallet-merchant-code 前端测试 |

测试先记录缺包／未实现的失败，再实现并复跑；误用未启用类型转换的前端启动命令已改用项目验证命令。回归发现旧测试仍按 61 秒过期，已同步已上线的 120 秒约定：119 秒有效、121 秒失效。

## Code Review Report
### P0 - Must Fix
无已知未解决项。
### P1 - Should Fix
审查发现的错误映射遗漏、创建意图重试前误校验余额、积分关闭仍可创建意图、等待账户锁后未复查过期，均已修复。消费部分付款显示待补付并引导原账单，不提前视为已结清。
### P2 - Improvement
商户消费码保持绑定顾客及既有 600 秒有效期；积分码 120 秒。未引入面向任意顾客的匿名通用支付单。
### Final Decision
APPROVE。后端相关 142 项、前端钱包回归 127 项及末次定向 57 项通过，构建与本地图片扫码端到端通过；真实手机相机未验证。详见同日验收文档。
