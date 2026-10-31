# 酒水寄存 API

基址：商户 `/api/v1/stores/{storeId}/wine-storage`；顾客 `/api/v1/public/stores/{storeId}/wine-storage`。顾客沿用现有 customer session cookie，商户沿用 CurrentActor。所有返回使用专用 DTO。UTC ISO8601 时间，响应包含门店时区。

| 方法/后缀 | 调用方 | 语义 |
|---|---|---|
| GET /home | 双方 | 规则、申请及原明细、物品、历史意图、流水、权限和新业务开关 |
| GET /products?query= | 双方 | 当前门店 storage 可选产品（中英文/编号），商户审核可查询租户候选 |
| POST /photos | 双方 | multipart image, customerId(仅商户)、phase=application/receipt/followup，返回附件ID |
| GET /photos/{id} | 双方 | 身份/归属校验后返回图片，不缓存 |
| POST /applications | 顾客 | lines: productId 或 nameZh/nameEn、quantity、opened、source、photos、note；rulesAccepted=true；可选原领取ID及明细ID |
| POST /applications/{id}/cancel | 顾客 | 仅 pending |
| POST /applications/{id}/reject | 商户 receive | 原因必填，仅 pending |
| POST /applications/{id}/receive | 商户 receive | 全部 lines 逐项确认，产品关联/新建、数量、状态、来源、ruleId、收存照片、位置和标签；修正理由必填 |
| POST /rules | 商户 rules.manage | categoryId、name、months，追加新版本 |
| POST /intents | 双方（商户 deliver） | selections: itemId、quantity；商户另传 customerId。返回明确明细及一次性授权码 |
| POST /scan | 双方 | code，商户解析顾客码，顾客读取商户意图；不自动授权或扣减 |
| POST /intents/{id}/authorize | 顾客 | code，顾客明确授权商户意图 |
| POST /intents/{id}/cancel | 双方 | 未完成可取消，不扣减 |
| POST /intents/{id}/deliver | 商户 deliver | 仅 authorized，最终实时校验，整批原子扣减 |
| POST /items/{id}/corrections | 商户 correct | reason、note；仅追加纠错，不修改原始数量及期限 |
| POST /items/{id}/photos | 商户 receive | photos、note；绑定本顾客新上传的 followup 附件，追加证据流水 |

业务变更 JSON 命令须 `idempotencyKey`（1-100字符）；只读解析 `/scan` 不需要键。上传是追加式附件资源，确认时每个附件 ID 只允许绑定一次。成功重试返回原结果（不代表当前业务状态，刷新 home 查询当前状态）；同键异参 409。请求摘要规范化 Map 键序，支持进程重启后的持久重试。所有失败事务回滚且不缓存失败结果。404 不泄露跨归属资源；403 无权限/新业务关闭；400 字段或照片不合法；409 状态/数量/期限/授权失效，批量失败含 `problems:[{itemId,code}]`。无跨门店批量操作、无通用身份码扣减、无延期/删除/到期绕过入口。

`home` 包含当前门店名称、时区和同租户可选门店（商户还限制分配门店），顾客申请、物品及流水均只返回本人记录。瓶数是 1-10000 的 JSON 整数，已开瓶固定 1；月份是 1-120 的整数，不接受小数、字符串或 null 的隐式转换。每次申请最多 50 项、每批领取最多 50 项、每项照片最多 10 张，JPEG/PNG 最大 5 MiB、3000 万像素。

收存 `lines` 必须覆盖原申请所有明细。一个原明细可分成多个已开瓶实际记录，每瓶数量 1，分别上传照片。商户选正式产品后，响应会填充实际产品 ID 和名称；创建建议产品的原输入仍保留在原申请及审核台账。仅最新规则版本可用于新确认，旧物品读取冻结版本。

共享目录的 `ProductWrite.priceCents` / `Listing.priceCents` 可为 null，仅限 storage 渠道或不开放渠道；空价产品税务保持 null。目录更新空价建档资料不会转成 0。销售调用现有 `ProductCatalogQuery.requireUsable` 和报价校验，必须完成价格、税务及渠道配置。

## 门店存取酒模式（2026-10-27）

Home 增加 `operationMode`（`customer_verification` 默认 / `staff_registration`）和 `settingsVersion`。
POST `/api/v1/stores/{storeId}/wine-storage/settings`：`{operationMode,version,idempotencyKey}`，仅 tenant_admin 且具备 wine_storage.rules.manage，产品开启时可改。版本冲突 409；非法模式 400；无权限 403。配置按租户、门店隔离，变更审计。
POST `/api/v1/stores/{storeId}/wine-storage/direct-deliver`：`{customerId,selections:[{itemId,quantity}],idempotencyKey}`，要求 wine_storage.deliver，仅直接登记模式可新执行，否则 409。整批原子扣减，返回 completed Intent（无 code），记录租户办理，绝不记录顾客授权。重试原键返回原结果，不重复扣库存；模式切回后仍可重放已成功命令。
两模式保留商户直接存酒及旧申请审核。直接登记模式顾客端以查询记录为主，隐藏新申请/出码，并由后端拒绝新顾客申请/新出码；旧授权及旧单仍按原规则收尾。产品关闭后仍允许受控取回历史存酒，禁止新增收存及设置修改。切换不改历史快照、不豁免旧待授权单。

## 存酒照片选填（2026-10-27）

顾客申请、重新寄存、商户收存及直接代登记，明细 photos 可传空数组，不要求照片即可保存。字段仍为数组，null/非法照片不接受；有照片时继续校验租户、门店、顾客、阶段、数量、重复及使用状态。补拍照片接口的操作目的为追加照片，仍要求至少一张。历史照片不修改；无需迁移。重新寄存可不拍照，但选择上传时不可复用原照片。

## 操作者姓名（2026-10-27）

Home.events 增加可空 actorName，原 actorId 保留。姓名在当前租户范围内按事件操作者身份批量关联查询：商户读取账号 display_name，空白时使用 username；顾客操作读取本人顾客档案 display_name。停用/软删除的账号仍可用于历史审计姓名查询，不授予登录或业务权限。姓名无法匹配时返回 null/缺省，前端显示未知操作者，不把 UUID 当作姓名。按当前档案解析，不回写历史事件或伪称操作时姓名快照；无迁移。原查询权限、顾客归属和门店范围不变。
# 当前寄存清单

存酒设置的酒品管理复用商品目录契约：`GET /api/v1/stores/{storeId}/catalog/storage-products?query=&page=0&size=12` 返回 `{items,total,page,size,hasMore}`，仅本店勾选 storage 的商品（包含停用/下架），在分页前过滤，搜索酒名/编号，query≤120、page=0..100000、size=1..100。原 `catalog.view` 及租户/门店权限校验，非法参数 `CATALOG_INVALID/400`，未登录401、越权403。维护仍使用原 catalog editor/listing/status 接口，不增加写权限，不回写历史寄存快照。

`GET /api/v1/stores/{storeId}/wine-storage/inventory/export?query=&status=all&locale=zh-CN`：使用相同 `wine_storage.view`、租户/门店与历史权限，输出真正 `.xlsx`（统计汇总、酒品明细两张表）。query/status 与清单相同，locale 仅 `zh-CN|en`；导出全部筛选结果，与 page/size 无关。表内含门店、时区、导出时间、筛选条件及服务器权威统计。明细最多 50,000 条，超过返回 `STORAGE_EXPORT_TOO_LARGE/422`，不截断；非法参数 `STORAGE_INVALID/400`。响应 `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`、attachment、private/no-store，顾客会话 401、越权 403。不会修改库存或审计事实。

`GET /api/v1/stores/{storeId}/wine-storage/inventory?query=&status=all&page=0&size=50`：商户 `wine_storage.view` 只读查询，关闭新业务后仍可查库存。顾客会话不可使用。租户从会话取得，门店权限沿用 StorageAccess。

只统计 `remaining > 0 且实物仍由门店持有`，包含过期未取走实物。`query` 最长 120 字，按字面搜索酒名、顾客姓名/电话、位置/仓位、标签及酒妹；`status=all|stored|expired`，`page=0..100000`，`size=1..100`，非法参数 `STORAGE_INVALID/400`。响应 `{items,summary,page,size,hasMore}`；summary 为整个筛选结果的 `{customerCount,bottleCount,openedBottleCount,expiredBottleCount,itemCount}`，空值为 0。items 保留寄存时酒名/位置/酒妹快照及原/剩余瓶数，顾客姓名按同租户当前档案解析；时间为 UTC Instant，由页面按门店时区展示。不改历史库存或权限。
