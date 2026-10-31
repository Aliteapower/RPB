# 存酒商品条码与选酒优化

## 契约与边界
共享产品目录拥有条码，一个租户内一个 GTIN 只能关联一个产品，可关联多个包装条码。8/12/13/14 位校验有效的 GTIN 统一补齐为 14 位，保留前导零；UPC 与 EAN 等价表示不产生歧义。V079 仅新增映射表，复合外键隔离租户，不改变价格、存酒数量或原有 API。

- GET /api/v1/stores/{storeId}/catalog/products/{id}/barcodes：catalog.view，返回 {productId,barcodes}。
- POST 同路径：tenant_admin + catalog.manage，{barcode,remove,idempotencyKey}。添加/移除均幂等，重复键不同请求 409；已被其他产品占用返回 CATALOG_BARCODE_CONFLICT/409。审计保留操作，移除不更改历史申请。
- GET /api/v1/public/stores/{storeId}/wine-storage/product-options：顾客身份及原 wine_storage 门禁；query（最多120字符）、categoryId（可空）、page（0..100000）、size（1..12，默认6）、barcode（可空）。返回 {items,hasMore,page,categories}。分类、门店可用性、租户条件在 SQL 分页前生效，barcode 为精确匹配；不返回其他门店未上架、停用产品。
- 原 /products 列表保持兼容。

## 交互
默认仅六项，分类/搜索变更回第一页；支持前后翻页。扫码和手输商品条码都可查找已有商品。摄像头解码在本机执行，支持 EAN8、EAN13、UPC-A、ITF14；不依赖 Safari 原生 BarcodeDetector。扫描成功停止摄像头；隐藏页面/卸载释放摄像头。
未收录条码提示填写酒名，条码作为申请备注保留，不允许顾客写产品目录。管理员在产品编辑中维护映射后，后续顾客可直接识别。图像识别失败支持重试和手动输入。扫描不触发寄存提交。

## 验证清单
真实 PostgreSQL：条码等价、冲突、幂等、租户隔离、门店及上架限制、分类分页无遗漏、权限拒绝。
前端：分页和过滤重置、乱序查询、扫码命中、未命中转手填、条码保留、摄像头生命周期、真实条码图片解码；原存取酒和钱包回归。

## 数据库审查
映射表主键 tenant_id+barcode，product_id+tenant_id 复合外键，产品反查索引；created_at、created_by；绑定删除均有 catalog_audit。升级不回填推测数据，应用回滚可保留新增表。

## API Review Report
Endpoint：新增端点均位于 /api/v1；原 DTO 与原列表保持兼容。门禁复用 CatalogAccess.requireGlobal/catalog.manage 和 StorageAccess.customer，不新增权限、不扩大匿名访问。固定错误：CATALOG_INVALID/400、CATALOG_NOT_FOUND/404、CATALOG_BARCODE_CONFLICT/409、CATALOG_STALE/409；认证/权限复用既有 401/403。GET 无副作用；POST 复用 CatalogCommands 持久幂等与事务审计。

## Code Review Report
Scope：共享目录条码管理、顾客选酒和通用摄像头租约。P0/P1：本轮未发现未解决项。摄像头原实现原样移到 shared-commerce/scanning，钱包兼容导出继续使用，避免产品目录反向依赖钱包。目录一维码识别与钱包/取酒二维码解码器分开。跨租户、上架/渠道、并发唯一绑定分别由查询条件、复合外键、行锁和条码事务锁保护。

## UI Review Report
顾客默认六项，支持分类、关键词、前后翻页；紧凑条目保留名称/编号/选择按钮。390px 本地模拟页面验证分页和条码匹配加入清单；扫码组件支持手输和照片，错误、加载、未匹配均有提示。未执行真实 iPhone/微信摄像头验收。

## Release Notes — 2026-10-26（已授权发布）
New：商品编辑增加条码维护，可扫码写入；顾客扫条码带出目录酒名。
Changed：顾客选酒独立组件，分类及数据库分页，避免一次展示所有酒名。
Migration：V079 仅新增条码映射表及索引；没有猜测或批量覆盖商品条码。
Permission：无新增权限，目录修改仍仅租户管理员且需 catalog.manage。
Risk：需先为目录酒品维护真实包装条码；摄像头物理设备兼容性待真机验收。npm audit 当前报告七项既有依赖高风险，涉及 Vue/Vite 及传递依赖；条码新增依赖未被该报告列出，本轮未扩大范围更新其他依赖。
Rollback：应用回退到本轮之前的兼容版本即可；新增映射表可保留，不删除历史存酒资料。不回退到不支持 NULL 价格的旧目录版本。

## 验证结果
- 50 项相关后端测试通过：CatalogBarcodePostgresTest、CatalogAccessTest、StorageAccessTest、StorageCustomerQueryTest、StorageRequestSecurityTest、StorageJsonTest、StorageScanDeliveryServiceTest、StoragePostgresTest。V079 在指针所指 PostgreSQL 的临时独立 schema 验证，无业务库写入。
- 钱包/存酒/条码前端组合 245 项通过；随后补充商品目录扫码写入测试，受影响页面共 18 项再次通过。
- 条码真实 EAN 像素解码测试通过；摄像头租约原有生命周期测试通过。
- 390px 浏览器本地模拟验收：每页六款、下一页、手输 UPC 与目录匹配后加入填写瓶数清单；没有执行真实业务寄存或扣减。
- 原有 12 个文件与上次发布保留清单 SHA-256 一致；git diff --check 通过。
- 构建/测试日志位于 target/storage-barcode-*.log。用户已授权提交部署；发布前验证通过，部署结果另行记录。
- npm run build 最终通过；条码解码器独立按需分包约 389 kB，普通 vendor 约 316 kB。

## Production Readiness Report
Decision: GO。验证、迁移增量安全、权限和租户隔离均已检查；不新增环境变量。发布前备份并在生产备份临时库演练 V079，失败恢复原应用与前端，保留新增表而不回滚业务数据。真机摄像头兼容性仍待设备验收。
