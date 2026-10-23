# 钱包统一交易查询：后端审查与验证

工作区 `D:\RPB`。仅开发、测试与本地打包；没有提交、推送或生产访问。原个人码 V064 和此前未提交实现保留。

## API 与权限审查

契约见 `docs/api/2026-10-23-wallet-transactions-contract.md`。新模块 `wallet.transactions` 独立包含公共 DTO、参数解析、业务 UNION 投影、数据库查询、服务与控制器；控制器不接触数据库，不新增记账入口。

员工资金、积分、退款/赠送退出分别执行 `wallet.view`、`wallet.points.view`、`wallet.refund.manage`，all 仅包含授权来源。管理员角色不扩大 explicit storeIds。顾客使用现有 cookie 身份及 active customer 校验，仅本人记录；拒绝身份覆盖或未知查询参数。历史停用门店可读，不要求新增交易商业资格。手机号员工掩码、顾客隐藏，顾客隐藏操作人。详情来源门店与路径一致；关联可跨已授权门店，继续限制租户、本人和类型权限。

新 GET allowlist 精确到列表与两段详情；其他 HTTP 方法不能借此进入。旧 GET 列表映射与 allowlist 已删除，操作和单笔详情保留。稳定 wallet 错误映射复用原 Advice。GET 不写入账本，不涉及幂等命令。

## 数据库审查

不新增迁移或改写历史。bill/topup/refund/points/独立 bonus exit 各自只有一条业务行；资金分录和收款凭证仅作为入账证据。已付款关闭/退款账单继续 completed；待处理和取消不计统计。消费展示原金额、统计实际钱包与外部实收；充值本金/赠送/权益分开，退款现金与钱包权益变化分开，积分不进入金额汇总。币种沿钱包已有 SGD 约束，不随门店结算币种改变。

支付、充值、退款和积分发生时间取原始证据，不使用退款后变动的 bill.updated_at；混合退款完成时间取最终外部退款或钱包记账证据。来源门店时区将本地日期转换成 `[start,endExclusive)`。完整过滤、统计、排序和 keyset 分页由 PostgreSQL 执行；cursor 绑定身份、门店、权限、日期和所有筛选。列表、门店选项、总数和汇总处于同一 readOnly REPEATABLE_READ 事务。

本地 PostgreSQL 使用 `target/local-postgres-current.txt` 的 61644、postgres、空密码，测试均创建随机独立 schema 并清理自身 schema。没有操作公共业务 schema。现有索引足以验证正确性，未无证据新增 V065；尚未以生产规模数据验证查询延迟。

## TDD 与验证结果

接口/数据/权限测试清单先于实现写入契约，测试沿实现同步增加。

完整要求回归命令：

```text
mvn -q -Dtest=Wallet*Test,*Wallet*Test,Points*Test,FundingAllocationTest,AppGateServiceTest,AppGateRequiredPermissionTest,PaymentIntentServiceTest,PublicBookingApplicationServiceTest,PublicBookingCapacityPolicyTest,LedgerRulesTest,LedgerBrandingServiceTest,LedgerApiTest package
```

33 suites、257 tests，failures/errors/skipped 均为 0。日志 `target/wallet-transactions-validation.log`。

之后将详情事件内部动作码转换为业务标签，并补充充值退款权益/现金分离、跨店积分冲正关联两条测试。最终代码定向 `WalletTransactionsPostgresTest,WalletTransactionsApiTest` package：14 PG + 3 API = 17 tests 全过，failures/errors/skipped 均为 0。日志 `target/wallet-transactions-final-validation.log`。合并当前相关测试集合为 259 个唯一测试。

真实 PG 重点覆盖：业务去重、完整汇总独立于分页、相同发生时间下五种来源混排、稳定游标及过滤变更拒绝、年/月/今天/跨年、两个门店不同时区、授权门店交集、租户/顾客/来源类型隔离、非钱包积分程序隔离、积分双向冲正、退款保持消费原时间与金额、混合退款最终完成事件、充值退出移除赠送、独立赠送冲正、停用门店历史与顾客身份校验、个人支付说明及秘密值不泄露、并发插入时只读可重复读分页与汇总一致。

本地 security 另覆盖未登录 GET 拒绝、旧列表关闭和非 GET 拒绝。原个人码、营销、分摊、退款、积分、App Gate、公共预订及日账本要求套件保留通过。

## 收尾与回退

后端审查未发现阻断项；`git diff --check` 通过。开发环境现有 Mockito 动态加载 agent/JDK 提示及测试 security 自动密码提示仍存在，不影响结果。完整浏览器验收由主任务执行，本说明不将单元测试等同浏览器结果。

最终包：`target/reservation-platform-0.0.1-SNAPSHOT.jar`，91,372,440 bytes，SHA256 `319C57F9D13C97F9F656899C766F3E3E5F99BFCE82BA2B83022EE1F0BB8A54D6`。无迁移回退需求；若回退功能，须成对恢复旧前后端列表入口，不能只恢复前端后调用已退休接口。不得回滚或覆盖其他未提交工作。未涉及预约、队列、到店或清洁业务状态变化。
