# 钱包营销、资金组件与积分数据及迁移设计

2026-10-23。**设计草案，不是可执行迁移。**本轮不创建SQL迁移、不运行DDL/DML、不变更数据库。

关联：[总方案](钱包营销与手动积分开发方案.md)、[API契约](../api/2026-10-23-wallet-marketing-points-plan.md)。未来迁移编号取实施时最新Flyway版本之后，不能将当前V058视为永远不变的编号基线。

## 1. 全局约束

- 所有租户数据带tenant_id；业务归属至少通过复合FK覆盖 tenant、program、customer/account/lot，禁止仅按UUID关联而遗漏租户。
- 金额为SGD整数分，积分为整数；数据库CHECK约束不负余额、冻结不大于余额、金额和数量上限。金额乘法/累计计算在Java使用安全整数或BigInteger，不能用double。
- 配置及状态表有created_at、updated_at、version；资金、积分和审计只追加不可原地改历史，不提供业务DELETE接口。
- 一个计划的门店必须属于同一发行主体，沿用wallet_program_stores已有约束，积分映射同样校验主体。
- 金额确认、幂等快照、分录/组件、余额、订单状态和审计处于同一数据库事务。报价、付款声明和二维码识别不增加可用余额。

## 2. 活动与档位（拟新增）

| 表 | 核心字段/职责 | 关键约束与索引 |
| --- | --- | --- |
| wallet_marketing_settings | tenant/program、default_deduction_policy、policy_version、config_version、self_topup_enabled、points_enabled、campaigns_enabled | unique(tenant,program)；policy仅proportional/principal_first；功能开关不是新订阅 |
| wallet_topup_offer_sets | tenant/program、kind(base/campaign)、campaign_revision_id?、revision、status、created_by | base无campaign引用，campaign必须引用；published版本不可原地改 |
| wallet_topup_offers | tenant/program/offer_set、id、principal_cents、bonus_cents、sort_order、enabled | principal>0、bonus>=0、合计<=上限；unique(offer_set,principal_cents)；基础档bonus=0 |
| wallet_campaigns | tenant/program、name、status、current_published_revision、version | unique(id,tenant,program)；无硬删除 |
| wallet_campaign_revisions | tenant/program/campaign、revision、start_at、end_at、priority、terms_snapshot、created_by | unique(campaign,revision)；start<end；UTC；发布使用计划级锁校验同优先级时间不重叠 |
| wallet_topup_quotes | tenant/program/store、principal_type/id、offer/revision、P/G/合计、渠道、policy快照、expires_at、consumed_topup_id? | 绑定本人或员工选择的account；quote只可被单个充值单消费；索引owner+expires_at |

同级重叠使用数据库可验证的约束或计划级事务锁+重复查询保证并发正确，不只靠页面检查。实施时优先评估现有扩展是否可用；不能为了排他约束未经评估新增生产扩展。禁用/归档活动与新报价创建共用计划配置锁，明确谁先提交生效。

活动seed以稳定template_key标识，逐计划初始化基础版本和草稿模板；初始化重试幂等，不能覆盖商户已改的档位或自动发布。生效活动在同一计划所有授权门店一致，不引入门店各自赠送比例。

## 3. 既有充值与余额表增量

### 3.1 wallet_topups

保留amount_cents为实收本金，新增principal_cents、bonus_cents、credit_total_cents、quote_id、offer_id、offer_version、campaign_id/version、offer_snapshot、policy_snapshot、origin、customer_declared_paid_at、credited_at。

- amount=principal，credit_total=principal+bonus，bonus>=0；offer/policy快照包含用于追溯的名称、比率分子/分母、策略和版本，关键计算字段仍存有类型的列，不仅存任意JSON。
- origin为staff/customer，历史补staff。quote_id同租户唯一；origin=customer必须有报价和可追溯customer。
- 自助待单部分唯一索引：(tenant_id,program_id,customer_id) where origin='customer' and status in ('pending_collection','verified_pending_credit')。
- exception/cancelled/credited不占新待单额度；异常进入经理善后队列，不能因异常退款已结束而永久卡住顾客。
- 顾客声明只在pending允许，重复声明保持首次时间；不创建collection、lot或ledger。
- 历史已确认/未确认充值均补principal=原amount、bonus=0、legacy_no_campaign，不能按上线当天活动补赠。

### 3.2 wallet_accounts

增加principal_balance_cents、bonus_balance_cents、principal_reserved_cents、bonus_reserved_cents及funding_model_version，保留balance_cents/reserved_cents/version。

必须始终满足：

```text
balance = principal_balance + bonus_balance
reserved = principal_reserved + bonus_reserved
0 <= principal_reserved <= principal_balance
0 <= bonus_reserved <= bonus_balance
account各余额组件 = 该账户全部资金批次对应组件之和
```

账户合计分录校验继续有效，不用新表替换旧账本。版本随资金变动推进；预览所依赖的账户、批次、报价版本均需验证。

## 4. 资金批次与组件记录（拟新增）

### 4.1 wallet_funding_lots

每次成功充值一个批次；主键(id,tenant,program,account)，引用原topup与offer/policy版本。持久化初始P0/G0、当前P/G余额及冻结、入账次序、策略、版本、origin(topup/legacy_pool)、原充值来源。

新充值 unique(tenant,topup_id)，一个原单重放不能发第二批赠送。legacy_pool每账户至多一个，金额来源及cutover版本有明确watermark。入账排序使用账户内单调序号或账务版本，不能仅依赖可能相同的created_at。

批次状态不得阻止原消费退款：退出后当前余额可能为0，但原消费退款可以按原分摊恢复其实际消耗的P/G；不恢复原来已撤销的未消费赠送。状态由权益/冻结和操作记录推导，不能硬删除批次。

### 4.2 wallet_entry_allocations

每个新总账Entry关联一到多条组件分配，字段包括tenant/program/account/entry/lot、action、principal_cents、bonus_cents、原消费allocation_id?、原退款引用、序号。

- 每个分配principal>=0、bonus>=0、两者合计>0，方向由对应entry和action明确决定。
- sum(组件本金+赠送)=该总账entry.amount_cents；每个entry恰好对应一组完整分配。
- 每次入账、扣款、退款和撤赠都可从原来源定位，unique(entry,lot,action,source-allocation)防重复；金额累计退款不得超过原分配。
- 原账本仍保持一个账户version对应一笔总账余额变动。联合本金/赠送的同一资金动作只产生一笔合计Entry，避免原account_version唯一约束冲突。
- 充值入账合计为P+G，收款collection仍只为P。旧kind=topup_credit继续表示权益入账，报表必须读取组件/收款字段而不是把它全部当外部现金。
- 充值退本金同时撤赠时，topup_refund合计为退本金+撤赠，组件action区分cash_principal_refund和bonus_revocation；实际外部退款金额只取原退款分项。
- 只有赠送需要退出而本金=0时，追加明确的bonus_revoke正金额总账种类及组件，保留“无零金额资金流水”。新kind同步更新数据库枚举CHECK、账务正负号和全部读取/对账逻辑。

事务提交前通过应用不变量与数据库延迟一致性检查核对账户、批次、总账、组件。旧代码只改balance而不改组件时必须被CHECK/延迟约束拒绝，不能默默损坏资金分账。

### 4.3 账单、授权与退款

- wallet_bills新增principal_paid_cents/bonus_paid_cents，wallet_paid=两者之和。
- wallet_authorizations新增allocation_snapshot/digest、allocation_version等，旧未消费报价在切换时失效，不能带着旧统一余额算法继续支付。
- wallet_refunds及退款预留明细新增现金退本金、钱包本金返还、赠送返还/撤销、批次分配和来源模式。钱包冻结合计可以大于原渠道现金退款额，两者不能共用一个含义不清的amount。
- 新建wallet_refund_allocations（refund、lot、原entry_allocation、P/G预留及状态），按source累计退款/撤销上限校验。原渠道退款collection/part唯一性规则保持。
- 赠送清零权益退出可保存wallet_bonus_exits记录（lot、bonus、reason、actor、entry、idempotency引用），不得捏造0金额外部退款。

## 5. 分摊算法规格

### 5.1 批次选择与锁顺序

在可见计划内按入账次序选择有可用余额的批次，跳过冻结部分。总扣款D=min(账单剩余应付,账户总可用)，从前到后最多扣完D。多个余额账户的指令不在本期范围。

所有资金用例统一锁序：幂等事务锁 → 按ID排序的账户锁 → 按原单类型/ID排序的订单锁 → 按入账顺序/ID排序的批次锁 → 对应授权/分配记录锁。活动配置独立使用计划配置锁，不在已锁账户后反向等待；读取已冻结活动版本无需持有活动编辑锁。积分不参与资金事务锁链。

服务端取得最后一个必要授权锁之后再次检查码/报价时效，保持现有防过期并发保证。并发退款/扣款不能分配同一可用分。

### 5.2 proportional

对新活动批次使用原始比例P0:(P0+G0)，不能用全账户或其他活动比例。为防止多次小额逐单四舍五入偏向本金，使用累计目标：

```text
N = 该批次净消费总分（原扣款减已消费退款，不含充值现金退款）
NP = 该批次净消费本金分
d = 本笔拟从该批次扣除的总分
targetP = ROUND_HALF_UP((N+d) * P0 / (P0+G0))
candidateP = targetP - NP
lower = max(0, d - availableBonus)
upper = min(d, availablePrincipal)
debitP = clamp(candidateP, lower, upper)
debitG = d - debitP
```

原分配及退款持久化后计算N/NP或维护可重建缓存；绝不能以“当前余额差”猜测净消费。乘积用BigInteger/精确除法，尾差由确定性规则分配；最后一笔可把可用批次恰好耗尽。G0=0直接走本金。

普通部分充值退款、消费部分退款造成组件比例暂时偏移时，clamp保证不超现有P/G，不能创造余额；继续按原比例累计目标校正。legacy_pool永远本金，历史退款追加本金不参与赠送比例。

测试要求包含拆成大量0.01消费、一次大额消费、随机拆单/乱序退款后的守恒和耗尽结果，不能只覆盖100/10整数示例。

### 5.3 principal_first

每个批次内 debitP=min(d,availablePrincipal)，debitG=d-debitP。先选批次，再按该批次规则分摊；不能把所有新旧批次本金先扣光后才用第一批赠送，否则会违反“按充值批次使用”的既定规则。

### 5.4 消费部分退款

按照原账单分配记录的稳定顺序恢复，不按当前活动重算。对每条原分配使用其累计已退总额与原P/G，按累计目标确定本次退P/G，最后一笔归还剩余分。必须同时满足：

```text
累计返还本金 <= 原扣款本金
累计返还赠送 <= 原扣款赠送
本次返还本金+赠送 = 本次钱包退款
全部退完后，每条原分配恢复到原实际扣掉的组件数量
```

外部支付原单退款按现有原凭证分配，不能改变其现金/PayNow/信用卡性质。

## 6. 充值退出、冻结与后续消费退款

活动批次剩余权益退出：预览锁定可退本金Pr和赠送撤销Gr，确认开始冻结Pr/Gr，实际原渠道退Pr后总钱包权益减少Pr+Gr。任一原渠道分项未完成时保持预留；仅未执行退款可取消并释放原预留。

例：P0=10000/G0=1000，比例消费5500 → 剩5000/500；退出后现金退5000、赠送撤500，账户该批次0/0。随后原消费全退5500 → 恢复原扣款5000/500，可再次消费或对恢复权益再办理批次退出；不能恢复此前已撤销的另外500赠送。

一般新充值批次满足：

```text
初始P0 = 当前P余额 + 净消费P + 已实际现金退P
初始G0 = 当前G余额 + 净消费G + 已撤销G
冻结属于余额的一部分，不额外加到上式
```

原渠道累计现金退款<=原实收本金，赠送不会成为cash退款。退出确认期间发生原消费退款，只恢复原分配，不能扩大已经预留的现金退款或擅自重算退款额；有新增恢复权益时保留为该批次可用余额，另外处理。

## 7. 独立积分数据

| 表 | 核心字段 | 关键约束 |
| --- | --- | --- |
| loyalty_programs | tenant、issuer、name、status、version | 计划不存货币兑换比例 |
| wallet_loyalty_bindings | tenant、wallet_program、loyalty_program、issuer | 当前一对一，复合FK/验证保证同租户同主体；适配归wallet.integration |
| loyalty_accounts | tenant、program、customer、balance、version | unique(tenant,program,customer)，0<=balance<=2147483647 |
| loyalty_entries | tenant/program/account/store、type、quantity、before/after、reason、actor、reference、reversal_of? | type决定正负；quantity>0；before/after守恒；流水只追加；原记录最多被冲正一次 |
| loyalty_redemptions | tenant/program/account、entry_id、description、status、reversed_by? | 每次兑换恰好一笔扣分entry，取消通过冲正，不创建配送/地址字段 |
| loyalty_commands | tenant、principal、operation、key、request_hash、response | 同身份/动作/key唯一，包含计划/门店摘要；不依赖钱包资金命令表 |
| loyalty_audit | tenant/program/store、actor、action、source、details、created_at | 每个关键命令恰好一份审计 |

积分账户/流水通过loyalty自身复合关系约束，顾客引用共享customer，不复制手机号、密码、邮箱。发生门店必须属于binding所对应的合法计划范围。钱包资金停用与积分状态分别表示，但开放写入仍依wallet商业授权适配。

积分事务：幂等锁 → 幂等开零账户或取账户 → 积分账户行锁 → 校验版本/余额/上限 → 写entry及redemption/反向关系 → 改余额版本 → 审计 → 幂等结果。原加分冲正先锁原记录，统一与账户锁序兼容，避免与兑换/冲正产生逆序锁；最终统一采用先账户后按ID原记录。

积分没有与现金的原子交换，不引入跨钱包资金/积分的分布式事务。历史资金不足或退款不自动加扣积分，只有商户明确的手动命令能改积分。

## 8. 迁移策略：保留历史，不虚构批次

### 8.1 为什么不倒推历史充值批次

旧账本只有总余额，旧充值退款可能使用当时全账户可用本金，并无可靠的原充值批次消费分摊。不能凭FIFO假设重写旧消费归属、历史JSON或旧退款金额。

### 8.2 扩展与切换步骤

1. 盘点最新迁移、记录数、活动中订单/授权/退款/冻结、当前总账一致性；账务异常账户先列出并阻止其新交易，不用迁移掩盖差异。
2. 新增表与兼容列，默认不开活动和积分写入；扩展阶段所有旧余额视为本金，赠送为0。
3. 在受控写入窗口切换：暂停本计划资金写入并排空旧事务，锁定/串行迁移账户。记录cutover账户version/entry watermark，为每个账户建立一个历史本金池，余额=原balance、冻结=原reserved，初始没有赠送。
4. 历史Entry不修改；组件账从本金池cutover快照+切换后新组件分录重建。历史页面按原分录全部本金显示，并标注历史数据，不生成伪造收款或额外入账Entry。
5. 原待充值单补0赠送快照，之后确认按旧本金入账并建立一个新普通批次；历史已充值单的退款余额来自可退原实收上限及可用历史本金池。若池余额不足，明确拒绝，而不借用新赠送金或伪造原批次资金。该历史原单退款边界变化必须在发布说明和验收中列出。
6. 历史待充值退款预留映射至历史本金池；历史部分支付/已付账单的旧钱包组成全部本金。历史消费退款恢复本金池，以原单退款上限约束，不恢复赠送。
7. 让新资金组件代码接管所有写入口；验证/启用账户组件CHECK及跨表延迟一致性检查。作废旧未消费报价或推进账户版本，客户端必须重新核对。QR不存金额，原WP/WC/WB协议不变。
8. 逐账户核对原余额/冻结与新组件/本金池完全一致，原收款、总账、单据金额与审计不改；恢复写入，再开放可编辑基础档位和草稿活动。
9. 发布活动、自助申请和手动积分按功能开关逐项开放；只有管理员明确发布活动后才产生赠送，不自动追溯加权益。

数据量小也不能一边旧代码写入一边无锁回填再假定一致。迁移作业按账户批处理可重试，保存作业ID/断点，unique legacy_pool避免重复，失败时可安全停在兼容阶段而非覆盖历史。

## 9. 回退与向前修复

- 没有新组件/赠送/积分写入、且仍处兼容扩展阶段时，可按验证过的兼容条件回旧应用，保留新增表及备份，不自动删DDL。
- **出现任何新组件写入后，禁止直接切回只理解旧统一余额的jar。**特别是赠送、积分或批次退款存在时，旧代码无法安全处理其权益、审计和展示。
- 紧急处理优先关闭新活动、顾客申请及积分写入入口，保留新模型下的安全查询和受控原单善后；必要时整体暂停资金写入，部署支持组件模型的修复版本。
- 只有明确无后续业务写入且已评估完整影响时，才能考虑一致时间点的整库恢复；不能自动用发布前dump覆盖已成功交易。
- 回退演练必须包括旧jar写入被约束拒绝、已发布活动关闭但旧权益仍保留，以及新前端/旧缓存客户端升级提示。

## 10. Database Review Report（设计阶段）

### Migration Scope / Table Check
活动与报价、资金组件/批次及独立积分新增表，既有账户/充值/账单/授权/退款扩展；无本轮可执行迁移。各表归属、写入来源及历史解释已明确。

### Constraint Check / Index Check
复合租户外键、单报价单订单、单自助待单、单充值批次、原记录唯一冲正、余额组件/冻结及原渠道退款上限需要数据库和应用共同验证；按tenant/program/customer/store/createdAt的实际列表和外键访问建立索引，不能只加单列UUID索引。

### Tenant Isolation
统一顾客仍租户级，资金和积分当前绑定同一发行主体计划。配置变更要求全部影响门店权限；任何客户端传入的program/issuer不能绕过服务器上下文。

### Risk / Recommendation
高风险点为历史本金池、三种扣款路径统一分摊、部分消费退款、批次退出后消费退款、旧幂等JSON兼容及旧jar回退。实施前先写原生PostgreSQL不变量与并发回归，按扩展/受控切换/校验/开放顺序实施。必须把本设计转换成经验证的精确DDL、迁移脚本与测试后才具备发布条件，不能将本报告解释为已验证生产迁移安全。
