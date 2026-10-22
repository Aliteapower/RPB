# 每日营业记录产品线

## 范围与来源

- `app_key=daily_ledger`，名称“每日营业记录”。原站点 v21，源码 `C:/Users/ZhangXianLi/营业额系统/cloud-site`，已核实 HEAD `92ee3d15e8eb5ea13c312fe177740e950c01db8c`。
- Java 21 / Spring Boot / PostgreSQL / Flyway / Vue 3 / TypeScript，原站点仅作为业务与迁移来源，不作为运行依赖。
- 复用 Platform → Tenant → OperatingEntity → Store、账号、员工管理与平台计费。集团是拥有多个经营主体与门店的租户，不另建一套租户。
- 按现有门店计费粒度默认 SGD 30/月、360/年；沿用平台收款登记与订阅开通，不假造在线支付成功。
- 蜀香 `sx` 为测试租户，密码按用户指定配置；不在代码中存放明文凭证，不覆盖已有同名租户。

## 模块边界

后端 `dailyledger` 独立 bounded context：domain 负责金额、配置校验、记录规范化与抽款快照；application 编排授权、事务、幂等和报表；persistence 只做租户/门店限定 SQL；api 显式请求和响应。前端 `src/modules/daily-ledger` 分离 API、类型、表单、设置、报表及自动保存。

不改订位、排队或商户 PayNow 业务。员工添加/停用/改密复用现有租户员工管理。配置、草稿、提交、审计都以 tenant_id + store_id 限定；门店复合外键阻止跨租户引用。

## 原功能保持

早午晚餐段启停；底钱、非现金与现金支出项目、Logo；币值数量、额外零钱、备注与字段录入时间；累计盘点；非最终班次新增抽大钞，最终班次冻结前班抽款；草稿自动保存、立即同步、清空和恢复；员工提交后禁止修改，管理员可更正并留审计；历史日期冻结配置；历史快捷日期、年份、图表、渠道合计、CSV。草稿不覆盖已提交报表快照。

金额使用 BigDecimal、两位小数。营业额 = 盘点现金 + 已抽大钞 + 非现金 + 已付现金开支 - 底钱；总交款 = 盘点现金 + 已抽大钞 - 底钱；旧 accounting_version < 5 记录维持旧口径。只统计指定最终班次已提交快照，不累加三个累计班次。

## API 契约

基础路径 `/api/v1/stores/{storeId}/daily-ledger`，tenant_id 只能取已认证 actor，不接受客户端指定。

| 方法与后缀 | 权限 | 行为 |
|---|---|---|
| GET /settings | daily_ledger.view | 设置及版本 |
| PUT /settings | daily_ledger.settings.manage | 设置 CAS 更新及审计；不改历史配置 |
| GET /days/{date} | daily_ledger.view | 当日冻结配置、工作记录、提交快照 |
| PUT /days/{date}/records/{period} | daily_ledger.write | version + client_save_id；draft / submit；CAS、事务、抽款来源校验 |
| GET /history?start=&end= | daily_ledger.view | 最多366天，仅授权门店 |
| GET /years | daily_ledger.view | 有记录年份 |
| GET /audit?date=&period= | daily_ledger.view | 历史快照 |

请求使用显式 DTO，动态项目映射仅存在于限定记录/配置文档内。400 `REQUEST_INVALID`；401 `UNAUTHENTICATED`；403 `FORBIDDEN` / AppGate 原拒绝码；409 `VERSION_CONFLICT`、`IDEMPOTENCY_CONFLICT`、`SOURCE_CHANGED`。同一保存编号与相同规范化输入重试返回成功；不同输入冲突；过期版本不能写入。每次日写入锁定门店账本设置行及当日行，避免首次配置与最终抽款并发竞态。

## 数据库与迁移

新增设置、日期配置、记录、审计表，均有租户/门店复合外键及时间字段。日期唯一性包含门店；记录唯一性包含日期与餐段；保存编号在门店范围唯一。提交快照与工作草稿分别持久化。Flyway 增量建表/注册产品/价格/回填权限；不在全局 migration 中导入真实营业数据或固定测试密码。

原数据以只读导出保存在忽略目录 target 下，迁入前检查完整性与哈希、来源版本；显式本地导入工具拒绝覆盖已有账本和同名租户。保留原员工名字、原时间及旧账号标识作为来源信息，不迁移登录会话。目标账号使用 RPB 的安全策略。原站点保持不变。

## 验收与风险

必须覆盖：金额及旧口径；配置冻结；未提交不入报表；管理员草稿保留已提交快照；员工锁定；幂等重试及冲突；并发版本；前班抽款变化；跨租户/门店拒绝；AppGate 产品禁用、租户未开通、门店关闭；真实 PostgreSQL FK 与持久化；前端构建及浏览器验证。使用 target/local-postgres-current.txt，先核实运行时归属，不替换其他工作区服务。生产发布需要另行授权。
