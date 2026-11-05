# 商品条码操作优化 — 2026-10-26

## 需求与交互
商品条码区归入当前商品的明细卡，明确显示商品名和编号。扫码先带入可编辑输入框，不自动写库；手输与扫码均通过“保存条码”显式保存，无需拍照或选择文件。已保存条码可点“修改”带入输入框，再保存原子替换。照片识别折叠为可选辅助方式，不设 required。顾客点“扫瓶身条码选酒”展开后自动开启摄像头，不再二次点击启动。

## API 契约（兼容扩展）
原 POST /api/v1/stores/{storeId}/catalog/products/{id}/barcodes 增加可选 replaceBarcode 字段：{barcode,remove:false,idempotencyKey,replaceBarcode?}。省略时保持原添加/移除行为；提供时在同一事务校验旧码属于当前产品、新码未被其他产品占用，再完成替换。新旧码按稳定顺序加事务锁。旧码已变更返回 CATALOG_STALE/409，新码被占用返回 CATALOG_BARCODE_CONFLICT/409；失败保留旧码。remove 与 replaceBarcode 不能同时使用（CATALOG_INVALID/400）。权限、门店/租户隔离、返回 DTO 不变；无数据库迁移。
replaceBarcode 为 null 时不参与 JSON 序列化，保持旧命令的幂等哈希和重放兼容。重试复用原键及原参数，不允许未知结果期间切换内容。

## 测试清单
- 扫码只填草稿，修改后显式保存；无照片也可保存。
- 已保存条码编辑发送 replaceBarcode；失败保留草稿和旧关联，成功清理草稿。
- 商品条码组件位于对应 article 内，显示商品名/编号，不串商品。
- 顾客扫描入口 autoStart，摄像头请求只发一次，关闭/卸载释放。
- PostgreSQL 原子替换、重复键重放、新码冲突、旧码失效、租户权限、旧请求 JSON 兼容。
- 中英文手机布局、加载/错误、钱包和存酒回归。

## API Review Report
Contract/Permission：POST 兼容扩展，requireGlobal/catalog.manage 与租户/门店校验原样保留，无新权限或运行时放行。旧请求 null 字段不参与序列化，原幂等哈希兼容；事务失败不移除原码。API 与旧前端兼容，新前端的替换操作须配套本轮后端。Error Mapping：400/409 复用现有错误码。

## Code Review Report
Scope：仅条码编辑和启动交互，不修改产品价格、存酒收存/扣减或钱包。P0/P1：本轮未发现未解决项。新旧条码稳定顺序加锁；替换、唯一归属校验和产品审计均在同一事务。前端未知结果保留原幂等键与原参数，期间禁止改写草稿；确认冲突时草稿保留供修正。

## UI Review Report
390px 中英文浏览器模拟验证：条码区位于“青岛啤酒 / Tsingtao Beer”商品卡内，显示名称、编号；修改后未选任何文件即可保存；照片识别为折叠可选入口。扫描只填草稿，可再修改后保存。自动扫码由挂载立即请求摄像头，同时按需加载解码器；隐藏或卸载释放摄像头。产品录入文字、单位与条码数据不随语言切换改写。

## TDD Review / 验证
- 新测试先运行复现自动开始、草稿确认、已存条码不可编辑三个失败，再实现。
- CatalogBarcodePostgresTest 9 项和 CatalogAccessTest 1 项通过，覆盖原子替换、冲突保留原码、旧码失效、重试、权限/租户和旧请求 JSON。使用 target/local-postgres-current.txt 所指运行时的临时独立 schema。
- 钱包/存酒/条码前端组合回归 251 项通过。最后调整摄像头并行加载及补充未知结果重试/语言切换测试后，受影响组件和页面 25 项再次通过。
- npm run build、git diff --check 通过；原有 12 个无关文件 SHA-256 与保留清单一致。
- 真实 iPhone/微信摄像头权限、对焦和照片兼容性仍待真机验收，未用浏览器模拟替代。

## Release Notes — 2026-10-26（已授权提交部署）
Changed：扫码带入可编辑草稿；条码保存不依赖照片；已存条码原子替换；顾客点击扫描一次即启动；商品条码移入对应明细卡。
Migration/Permission：无新迁移，无新增权限或环境变量。
Rollback：前后端一起回退到本轮前的兼容版本，保留已有条码表和全部业务数据。发布须后端先更新再更新前端，避免旧后端将替换请求误作普通添加。
Evidence：target/catalog-barcode-usability-*.log；仅本地模拟商品保存，没有改动生产商品或业务数据。

## Production Readiness Report
GO：接口兼容、事务及幂等、权限、相关测试和构建均已检查；本次无迁移，无环境变量或权限变更。发布时先后端再前端；备份旧包、前端及数据库，失败恢复旧应用和前端，不覆盖业务数据。真机摄像头仍待设备验收。
