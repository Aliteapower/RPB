# 共享目录与卡业务接口合同

实施基准：`docs/development/共享产品目录与钱包卡业务开发方案.md`。

## 实施裁定

- 沿当前非 main 功能分支开发，保留既有 LF/CRLF 状态及未跟踪文件；不创建另一个数据库运行时。
- 中英至少一种、首次仅本店、SGD、购买即生效、购买当天计第 1 日、未用卡可全退、已用卡不开放退款；不实现余额购卡，仅确认线下收款。这些采用方案中的保守默认值，不扩大未来业务范围。
- 免费集次卡首版每次加一次，消费集次必须有来源编号；奖励独立持有。按方案配置循环、有效天数和每日上限。
- catalog 是租户共享资料，员工可维护的范围受 catalog 权限控制；门店价格/可售设置需该门店授权。其他渠道仅提供调用合同，不开放尚未实现产品线的交易。
- 卡服务拥有自己的数据；wallet 接入层负责现有顾客身份、AppGate 和收款适配。

## 路径、安全与命令

目录：`/api/v1/stores/{storeId}/catalog`；所有管理命令经员工身份、tenant/store、catalog.view/manage/publish 校验。目录数据租户共享，新增/编辑价格和投放限定请求门店。上架/下架只影响新业务。

员工卡：`/api/v1/stores/{storeId}/wallet/cards`；顾客卡：`/api/v1/public/stores/{storeId}/wallet/cards`。通过现有员工或顾客会话识别身份，客户端不能指定操作人。写请求要求 `X-RPB-Wallet: 1`，目录写请求要求 `X-RPB-Catalog: 1`，拒绝跨源/跨站，禁止缓存。

所有可重试命令带 `idempotencyKey`（非空，最长 100）；摘要包含资源 ID、门店及请求。重复同请求返回原结果，不同请求 409。另有原单/来源/奖励轮次/收据去重。恢复结果查询不得将未确定请求当失败重试。

分类：GET/POST `/categories`；产品：GET/POST `/products`、PUT `/products/{id}`、POST `/products/{id}/status`；报价：POST `/products/{id}/quote`。产品资料含编号、双语、分类、实物/服务、单位、图片 URL；门店报价含以分计 SGD 金额、税模式和精确税率、版本与渠道许可。

卡 GET `/plans`、POST `/plans`、POST `/plans/{id}/status`；GET `/holdings`、GET `/entries`；GET/PUT `/settings`；员工 GET `/customers?query=...` 与 POST `/identity` 用于受控顾客查找。顾客只返回自己的持卡与流水。

集次：POST `/enroll`、POST `/stamps`；购卡：POST `/purchases`、POST `/purchases/{id}/confirm`、POST `/purchases/{id}/refund`；核销意图：POST `/requests`、POST `/requests/{id}/code`、POST `/scan`、POST `/requests/{id}/confirm`、POST `/requests/{id}/cancel`；员工核销 POST `/authorized-redeem` 必须有顾客针对具体意图生成的一次性授权，顾客 POST `/requests/{id}/authorize`。顾客直接确认与员工消费授权走相同执行事务。

商户码支持 stamp/redeem；创建码绑定方案/数量/可选持卡，120 秒有效，保存哈希；扫码仅查看。顾客确认 stamp 可绑定自己的已领卡；redeem 必须明确选择自己的卡。已完成意图可由原顾客/员工查询结果。GET `/requests/{id}`、GET `/commands/{operation}/{key}`、POST `/commands/{operation}/{key}/reconcile` 提供只读状态与受控恢复。

卡类型 stamp/count/period；数量正整数；总量 nullable 表示不限制，日限额 nullable 表示不限制。日额度不累计。时区按发卡冻结；左闭右开有效期。锁后时间跨日拒绝陈旧日期报价。

状态更新带 expectedVersion；卡方案发布后不可改旧规则，使用新方案版本/新方案。未启用商品不能参与新方案/购卡，旧权益按快照履约。

金额不使用浮点。税率十进制如 0.10，仅作为测试输入；inclusive 总额固定、exclusive 加税，HALF_UP 到分。每笔购卡保存报价快照。

## 错误

400 请求非法；401 未登录；403 权限/产品线拒绝；404 无同范围资源；409 版本冲突、重复来源、额度不足、码到期、已使用、报价失效、退款不允许。稳定业务前缀 CATALOG_/CARD_，国际化客户端按 code 显示，响应不暴露 SQL 或明文码。

## 数据库约束与回退

新增迁移，不修改历史迁移；复合租户 FK、非负数量/版本、状态约束、命令/轮次/来源/收据唯一，日桶按权益+日期唯一。无跨租户引用。关闭新交易保留历史和原单恢复入口；回退代码保留新增表及历史，不回滚已确认收款。

## 实施补充

- 产品 GET 返回 `listingEnabled/listingVersion`；POST `/products/{id}/listing` 请求 `{priceCents,channels,enabled,expectedVersion,idempotencyKey}`，只改当前门店。共享资料/分类/全局上下架额外限定 tenant_admin，店长不能借 A 店 URL 下架 B 店。
- 持卡、流水、购卡列表每页最多 500，支持 `?before=<上一页最后一条ID>`；持卡和流水同时可加员工 `customerId`。员工选顾客后从服务端查询该顾客持卡；前端提供加载更早记录。
- 顾客授权时校验并持久化 `authorizationDay`；员工扫码不决定业务日，即使提交新一天也不能挪用原授权。最终锁后再次校验冻结日期。
- 集次流水增加单调序号与 `stamp_reward_round`；撤回只接受最新未冲正集次，撤销其关联的未使用奖励；已撤销奖励不会阻止单次非循环卡重新正确集次。
- `/purchases/{id}/exception`：经理请求 `{payment:PaymentConfirm,reason,idempotencyKey}`，登记已经发生的原单实收但不发卡。状态 exception，可从原单退款到 exception_refunded；关闭新交易后仍受原单恢复权限保护。
- 购卡新收款渠道必须存在于现有钱包计划科目中，包含自定义科目；原异常收款恢复不冒充新购卡。外部凭证按结算类型/账户/参考号统一登记，禁止跨钱包/卡或自定义别名重复。
- 员工 `/summary?from=YYYY-MM-DD&to=YYYY-MM-DD`：独立返回实收/退款、各产品单位的集次/奖励/核销/冲正及纯周期卡未用日额度作废量。

## 存酒设置内的商品管理查询

`GET /api/v1/stores/{storeId}/catalog/storage-products?query=&page=0&size=12`，返回 `{items,total,page,size,hasMore}`。沿用 `catalog.view` 和会话 tenant/store 范围；query≤120，按酒名/编号字面搜索，page=0..100000，size=1..100，非法参数 `CATALOG_INVALID/400`、未登录401、越权403。仅筛选请求门店 channels 含 storage 的商品，包含已停用/下架/归档条目；在分页前过滤，不受原总目录500项上限影响。只读 REPEATABLE_READ 查询保证计数及分页一致，排序 code/id。既有 `/products` 响应不变。

入口内嵌原商品编辑器与状态管理；`catalog.manage` 控制资料/门店维护，租户共享资料需 tenant_admin，`catalog.publish` 控制全局状态。写入仍经原 editor/listing/status 的版本、幂等与审计校验；本店停用影响该商品在本店所有渠道，仅取消 storage 勾选才仅关闭寄存酒。历史寄存快照不改写。无新增权限、迁移或写接口。
# 寄存酒自动短编号补充（2026-10-27）

POST /api/v1/stores/{storeId}/catalog/products 及 /catalog/products/editor：新建且channels包含storage时，product.code可留空或null，保存事务内分配租户范围未使用的1至5位数字（1..99999），响应返回最终编号。自定义编号沿用原最多80字符约束与CATALOG_CONFLICT/409重复拒绝；编辑已有商品仍须携带编号，不自动替换。幂等重放返回原商品，不重复分配。自动号段用完返回CATALOG_CODE_EXHAUSTED/409，可改用自定义编号。

存酒收存关联创建商品使用同一分配器，替代此前WS-UUID；所有商品新建使用同一租户事务锁协调自动与手动编号，保留unique(tenant_id,code)。分配只查本租户全部商品（含归档），不改历史编号、快照或数据库结构。catalog.manage、tenant_admin和门店访问边界不变。

前端仅存酒新建草稿默认选择已启用且中文名称精确为“酒水”的分类；未找到则手选，不新建分类，不覆盖已有商品/用户选择。编号留空保存后生成，支持自行输入；默认瓶与寄存酒渠道沿用。

## 日额度过期扣总次数（2026-10-27，本地待发布）

员工购卡订单顾客信息补充（2026-10-28）：`GET /api/v1/stores/{storeId}/wallet/cards/purchases?before=...` 保留现有订单扁平字段与分页，新增可空的 `customerName`、`customerPhone`。先以原 `wallet.cards.view`/租户/门店规则读取本页订单，再仅批量查询这些订单顾客在当前租户内的有效资料；不返回邮箱或登录资料。资料缺失/已归档时返回 null，不丢弃历史订单。顾客端接口及收款/退款命令响应不变。前端显示姓名及电话、仅姓名或仅电话；都缺失时显示“顾客资料不可用”，不拿截断 UUID 冒充顾客名。无迁移、无新权限。

同一员工列表新增 `refundable:boolean`，由服务端按租户、门店、本页订单批量判断：issued 订单关联 active 卡，且从未发生 `redeem`，才可退卡；即便核销后来已冲正仍不可退。异常实收未发卡订单 exception 沿用原异常退款流程。退款按钮仍要求 `wallet.cards.refund` 权限；资格缺失/false 时禁用，不能打开退款表单。资格为显示提示，最终 POST `/purchases/{id}/refund` 仍在卡行锁内重新校验并拒绝已使用卡，防止页面刷新与核销并发导致误退。`daily_expire` 是未用额度过期事实，不冒充实际使用；现有未使用退款规则不变。

已确认：从本次规则上线日起计算，历史不追扣。迁移为已有持卡冻结各自时区的上线业务日；新发卡从发卡日计算。仅次数卡/周期卡且同时存在有限剩余总次数和每日限额参与扣次。每天按卡片冻结时区结束后，未用额度扣除 `min(每日限额 - 当日净使用量, 剩余总次数)`，最低为零；当日已用部分不重复扣，未到期的当天不提前扣。按有效期左闭右开结算，整卡到期后不再产生新增日额度。

新增 `card_entries.kind=daily_expire`，显示“当日未用过期（非消费）”，带业务日、过期量、总次数前后值和系统结算来源；与消费、退款、集次、积分分账。既有持卡/流水/统计路径和权限不变；读取持卡及流水/报表前补齐已过去业务日，执行核销/退款前在同一卡行锁内结算，后台定时补齐。反复读取/多实例/重试均不重复扣次；多天未登录按日补账。跨日冲正恢复的旧日份额仍过期，不能恢复为今天可用的总次数。

未设置每日限额的次数卡、集次卡及奖励卡不扣未用日额度；总次数不限的周期卡仍保持不限，既有未用额度报表口径保留。现有未使用卡退款政策不改，过期不冒充实际消费。

旧卡不改发卡快照，不追扣上线日前额度；前端不自行减余额。日结独立于产品开关，关闭新业务后仍按既有权益期限结算。回退应用不得删除过期事实或回滚顾客已发生的消费。
