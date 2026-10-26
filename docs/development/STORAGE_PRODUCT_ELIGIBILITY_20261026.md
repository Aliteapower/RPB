# 存酒选品遵循商品目录开关

## 根因及规则
商户 /wine-storage/products 传入 review=true 导致跳过全部可用性过滤；既有商品 associate 只检查实物类型，漏掉寄存渠道和上下架。商户表单还会将查询中未出现的原商品直接回填为可选项。
新存酒（顾客申请、商户代登记及审核实际收存）只允许当前租户/门店的 active 实物、启用分类、门店 listing enabled 且 channels 含 storage。SQL 先过滤再限量，商户顾客规则一致。已有寄存快照、历史查询和领取交付不依赖当前商品开关。

## API Contract
GET /api/v1/stores/{storeId}/wine-storage/products 保持 DTO、路径、权限，改为严格可寄存商品集合。
新增 GET 同前缀 /products/{id}：wine_storage.view，返回既有 Product DTO；复用租户/门店和产品可用性校验，查无商品404、不可用409 CATALOG_UNAVAILABLE，认证/权限沿用401/403。用于独立核对申请原选商品，避免用搜索结果缺失推断不可用，也避免将已停用商品作为下拉选项回填。
实际收存既有商品重新校验，防止缓存旧ID或伪造请求绕过开关。新建手填酒品维持显式新增及保存规则流程。
无迁移，无新权限；只影响新收存，不回滚、不覆盖任何已有寄存记录或规则快照。

## 测试清单
商户/顾客集合一致；无渠道、停用listing、未在本店启用、下架/归档、服务产品、禁用分类不显示且收存拒绝；其他租户隔离；筛选在limit之前。原商品不可用不回填、不能提交；原商品仅与搜索词不匹配仍能显示已验证选择。既有酒在目录关闭后仍可正常领取。

## API / Code Review
保持原 products 接口和布尔 review 调用签名兼容，review 不再放宽候选条件。共享 STORAGE_FILTER 在 SQL 限量前生效；单品按 ID 查询仍校验 wine_storage.view 和租户/门店。既有商品收存先锁产品再复核可用性，不能用旧 ID 绕过目录开关。服务类商品即使错误勾选 storage 也不能用于新收存。
Controller 仅调用应用服务，DTO 不泄露价格或持久化对象。既有 StorageErrorAdvice 已将目录不可用映射为 409；未新增全局异常捕获或访问放行。历史领取、账本、快照不读取当前商品可用性。P0/P1：本轮未发现未解决项。

## UI Review
原申请商品按 ID 独立核对，可选项仅由服务器确认可用的酒品组成；不再无条件回填原商品。不可用时提示重新启用/选择并阻止提交；只是搜索词不匹配时，已核实的原商品可保留。网络查询失败单独报错，不误报为目录停用；旧搜索响应与卸载后响应不能覆盖新状态。
目录开关改变后刷新选品；即使客户端仍缓存旧 ID，收存提交也会被服务器拒绝。

## 验证
- 先用真实 PostgreSQL 复现两个失败：商户候选包含未开 storage 的商品，以及 listing disabled 商品。
- 49 项后端测试通过：StorageProductEligibilityTest 6、StoragePostgresTest 30、StorageProductQueryTest 1、StorageAccessTest 2、StorageRequestSecurityTest 1、CatalogBarcodePostgresTest 9。覆盖所有候选条件、租户/门店、伪造/陈旧 ID、代登记及审核收存回滚，目录关闭后原有存酒仍可领取。
- 最终 265 项钱包/卡/存酒/条码前端组合回归通过，相关页面 25 项单独通过；npm run build 和 git diff --check 通过。
- 首次全量回归与构建并行时，wallet-personal-charge 的 Vite SSR 模块加载超时导致 7 项 hook 失败；确认仅终止本轮卡住的测试子进程，等待构建完成后串行重跑，同样源码全部 265 项通过，没有修改钱包代码来规避失败。
- 原有 12 项无关改动 SHA-256 保持不变。本轮只使用本地临时测试 schema，未修改生产数据。

## Release Notes — 2026-10-26（本地修复，未提交部署）
Fixed：未开放寄存酒、门店停用、下架/归档、禁用分类及服务类商品不再成为新存酒候选；提交阶段再次校验。已有存酒记录和领取不受影响。
Migration/Permission：无数据库迁移，无新增权限、环境变量或产品开关改写。
Rollback：恢复本轮之前的应用与前端即可，不恢复旧库、不回滚存酒交易。显式登记未知新酒品的原有流程保留。
证据：target/storage-product-eligibility-*.log。

## 发布授权
2026-10-26 用户明确授权提交部署；本轮仅发布单商品单条码和寄存商品资格修复。沿用已通过的 63 项后端、267 项前端和构建证据。无迁移，部署前备份、预设回退并在独立 QA 租户在线验收。
