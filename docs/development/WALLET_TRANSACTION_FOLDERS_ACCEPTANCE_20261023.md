# 钱包交易分类开发验收

日期：2026-10-23。基线：08717c31，上一轮业务发布 a4340a9d。本轮仅开发、测试，无提交、推送或部署。

## 实现

- 商户及顾客保留一个交易记录入口，文件夹页签为消费／充值／积分，各自支持全部／收入／支出。消费包含消费退款，充值包含充值退款及赠送权益冲正，积分包含增加、扣减、兑换和冲正。
- 日期、门店、编号、顾客、状态及子类型条件折叠显示；分类切换保留共同条件，重置子类型和游标；收支切换重置游标。详情返回保留 URL 条件。
- 后端统一查询先按分类、查看者收支方向和权限过滤，再完成稳定分页、完整结果计数及汇总。商户消费收款为收入，顾客对应消费为支出；积分方向取真实余额增减。统计按当前分类展示，金额与积分分开，待处理及取消不计已完成汇总。
- 复用现有只读交易投影、业务详情及数据，不增加记账、扣款或充值逻辑。

## 测试证据

本地 PostgreSQL 使用 D:\RPB\target\local-postgres-current.txt 指向的 61644 运行环境；运行页面使用本线程原有隔离 schema wallet_transactions_0983618362a147a0b77cb19477406228。未使用公共业务数据记账。

| 验证 | 结果与证据 |
| --- | --- |
| 先红后绿 | 分类参数未实现前测试失败，target/wallet-folders-red.log；实现后通过 |
| 后端 | WalletTransactionsPostgresTest、WalletTransactionsApiTest、WalletPersonalPaymentPostgresTest、WalletLocalRuntimeSecurityTest，4 组 41 项通过；target/wallet-folders-backend-final.log |
| 前端钱包回归 | 113/113 通过；target/wallet-folders-front-regression.log |
| 构建 | vue-tsc 与 Vite 构建通过；target/wallet-folders-build.log |
| 隔离运行 API | 7 项检查通过；target/wallet-folders-e2e-20261023/proof.json |
| 查询无写入 | 7 张资金、积分相关表前后条数和内容指纹一致；同一 proof.json |
| 浏览器 | 商户消费 2 条、充值 3 条、积分 4 条；积分支出筛选仅 2 条，详情返回仍为 points/out；顾客消费支出仅 1 条，切积分保留 out，只有 2 条积分支出 |

API 运行检查覆盖：分类不混排、充值本金 100／赠送 10／权益 110 且排除待处理取消、积分独立统计、双方收支方向、积分双向冲正、分页仍统计完整结果、游标不能跨分类或收支复用、无效组合拒绝、待处理充值深链自动进入充值分类。

浏览器截图位于 target/wallet-folders-e2e-20261023/：merchant-topup.png、merchant-points-out.png、customer-consumption-out.png。仅本地桌面浏览器验证；未将其视为真实手机相机验收。本轮为查询页面变更，未重新进行真机相机付款验收。

## API、迁移与权限

- GET transactions 增加 category 和 direction，响应增加实际 category；见 docs/api/2026-10-23-wallet-transaction-folders-contract.md。
- 旧 API 客户端省略 category 仍保留 all 的原查询语义；新界面固定传 auto 或具体分类，没有混排分类页签。旧分页游标需要重新查询。
- 交易条目的 direction 明确为当前查看者视角；资金原始权益变化字段保留，商户收支与顾客权益变化不混淆。
- 无数据库字段、索引或迁移变更，无第二套账本，无新增权限；租户、门店、顾客本人及资金／积分独立权限过滤不变。
- 不恢复旧独立列表路由，不增加别名或重定向；统一入口及现有详情路由保持。
- Reservation、Queue、Walk-in、Seating、Cleaning 无业务代码变更。

## 代码审查

本轮自审结论：APPROVE。检查了分类来源权限、租户与本人范围、商户／顾客方向、充值退款归类、SQL 参数绑定、汇总与分页条件一致、游标条件绑定，以及前端路由条件恢复。未发现阻塞问题。

## 风险与回退

- 未执行生产发布或生产数据验证；大规模历史数据查询性能本轮未压测。真实手机页面和相机未重测。
- 本轮仅查询及展示变更；可按文件差异恢复本轮 API 查询、前端分类及对应测试、合约，无数据回写。不得回滚已有个人钱包码、付款授权或其他未提交工作。
- 原有工作区改动保留；本轮临时验证仅使用独立 8087／5187 服务，不替换原 8081／5176 运行服务。
