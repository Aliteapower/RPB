# 商品目录按功能分区与统一编辑

## 需求
商品列表、分类管理、上下架管理各自独立显示；商品编辑替代列表，产品信息和条码在同一表单，仅一个保存操作。没有保存前不会写入条码。扫描到几位，原始条码就存和显示几位，不补位、不截位；规范化值只用于内部等价匹配。历史记录无法恢复原始表示时不猜测截断，允许用户按瓶身修正后保存。

## API 契约
GET /api/v1/stores/{storeId}/catalog/products/{id}/editor -> {product,barcodes:[原始字符串]}，catalog.view。
POST /api/v1/stores/{storeId}/catalog/products/editor 新增；PUT /api/v1/stores/{storeId}/catalog/products/{id}/editor 修改，均 tenant_admin + catalog.manage。请求 {product:既有ProductWrite,barcodes:[原始字符串],expectedBarcodes:[载入时的字符串],idempotencyKey}；响应与 GET 一致。
产品字段验证复用 CatalogService；产品与条码在同一事务提交。原码快照、产品版本失效 CATALOG_STALE/409，条码被他品占用 CATALOG_BARCODE_CONFLICT/409；重复/非法条码 CATALOG_INVALID/400；无权限 401/403。失败不保留半次更新；同键重试返回原结果，不同参数同键409。新建可直接保存条码，无需再点编辑。
门店管理员仍仅使用原 listing 接口修改门店价格/渠道，无法修改全局产品或条码。旧产品及独立条码接口保留。

## 数据库
V080 新增可空 raw_barcode（8/12/13/14位数字，与原匹配键必须等价）。不覆盖历史匹配键、唯一索引或跨租户外键；历史 raw 为 NULL 时保留原记录显示，不推测删除前导零。新写入/修改准确保留用户原始字符串，后台匹配键不对外展示。可空设计兼容旧版本回滚。

## 测试清单
真实PG验证：新增带原码、修改产品与条码原子更新、不同位数精确保留、补零等价但显示按输入更新、冲突及失效快照回滚、幂等重放、跨租户/权限。
页面验证：分区互斥、编辑无重复商品信息、单次保存包含未失焦输入、条码草稿不单独发请求、失败保留草稿、成功回列表显示最新值、管理权限及中英文手机布局。

## API Review Report
新增 editor 端点复用 CatalogAccess 的 tenant/store 身份与 catalog.view/catalog.manage；未扩展本地运行时或匿名权限。保存只要求既有全局 manage 权限，不额外叠加 view。产品 version、门店 listingVersion、条码 expectedBarcodes 同时校验；重复键响应持久化，失败整体回滚。旧产品与独立条码接口保留；新写入的独立条码响应也返回原始值，同键的历史响应仍按原幂等记录重放。

## Database Review Report
V080 仅新增 raw_barcode 及原始值/匹配键等价约束；原 tenant_id 唯一索引、复合外键和业务关系不变，无历史值猜测或自动截位。旧数据 raw=NULL，仍保留原记录；用户按瓶身修正一次保存即可准确保留原始位数。可空列使旧应用回退时仍可写入，已保存 raw 值继续保留。

## Code Review Report
CatalogPage 仅处理区域切换和列表数据；CatalogProductList、CatalogCategories、CatalogAvailability 分别处理列表、分类、状态操作。CatalogProductEditor 与 useCatalogProductEditor 负责统一编辑、单次请求、草稿及失败重试；CatalogBarcodeFields 只编辑原始字符串，不独立写库。移除已无引用的独立 CatalogBarcodes 组件，避免两套保存方式。
后端 CatalogProductEditService 编排既有产品规则与条码所有权服务，事务覆盖产品、门店价格/渠道、条码和命令审计。新旧条码稳定顺序加锁，冲突或快照过时均不保留半次保存。P0/P1：未发现本轮未解决项。

## UI Review Report
390px 本地模拟验证：默认仅商品列表，分类管理不夹带商品与状态按钮；编辑时只有一份商品信息及条码输入，一次保存后以 13 位重新读取，未补零。保存结果及错误放在按钮附近。退出未保存编辑需要明确放弃，未知结果期间只允许重试原提交。新增和替换条码均可一次点击自动启动扫码；照片可选，不是保存条件。
店级管理者只能保存 listing，条码和全局字段只读。上下架/归档集中到单独区域并先确认目标商品，不在列表直接执行。

## 验证
- PostgreSQL 26 项相关测试通过（CatalogProductEditPostgresTest 11、CatalogBarcodePostgresTest 9、CatalogAccessTest 1、StorageCatalogPricingTest 5），使用指针所指本地运行时及临时 schema。
- 覆盖新增携带条码、8/12/13/14 位原样保存、13 与补零表示等价但原始显示更新、未失焦字段提交、冲突/失效回滚、持久幂等、租户与权限隔离、NULL 价格兼容。
- 钱包/卡/存酒/条码组合前端回归 261 项通过；后续受影响目录/存酒页面 23 项通过，含无二次扫码点击、失败保留草稿与同键重试、分区和店级权限、放弃编辑确认。
- npm run build、git diff --check 通过。原有 12 项无关改动按 SHA-256 保持不变。
- 浏览器为本地 HTTP 模拟验收，真实事务由 PostgreSQL 测试证明；未修改生产商品，未执行支付或核销。真机摄像头/微信/iPhone 兼容性仍待设备验收。

## Release Notes — 2026-10-26（已授权提交部署）
Changed：目录按操作分区；统一产品与条码编辑/保存；条码原样存显，不补位；结果反馈靠近保存按钮。
Migration：V080，须随新后端上线；无需新权限或环境变量。历史条码不擅自截位，按瓶身修正保存后使用原始表示。
Rollback：前后端一起回退兼容版本，保留新增列和全部业务记录，不恢复旧库覆盖交易。旧版本可能仍显示规范化值，但原始值数据保留，可前推修复。
Evidence：target/catalog-unified-*.log。列表仍沿用既有目录查询的 500 条上限，本轮未扩展该既有接口。

## Production Readiness
GO：相关验证、事务/幂等、权限、V080 增量迁移及回退兼容已检查。发布前完整备份及临时库恢复/迁移演练；先后端后前端。使用独立 QA 租户验证原始条码 14→13 位与统一保存/回滚，保留验收记录，不操作真实商品或资金。
