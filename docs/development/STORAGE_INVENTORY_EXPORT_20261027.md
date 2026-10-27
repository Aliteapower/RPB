# 当前寄存 Excel 导出

在当前寄存筛选区域提供导出 Excel。导出采用最近成功查询的 query/status，覆盖全部匹配记录，与翻页无关；修改筛选草稿但未查询时提示先查询。导出期间禁用重复导出、查询和筛选编辑，切店或离开后丢弃旧下载响应。

GET /api/v1/stores/{storeId}/wine-storage/inventory/export?query=&status=all&locale=zh-CN

复用 wine_storage.view、原租户/门店校验和历史查询口径；关闭新业务仍可导出，顾客端不可使用。复用同一仓储过滤/排序和 REPEATABLE_READ 事务，汇总和所有明细取同一快照。生成真正 xlsx，统计汇总、酒品明细两表，包含店名、门店时区、导出时间、筛选条件、人数/剩余瓶数/开瓶/过期数及全部明细。数字为数字单元格，电话/标签/顾客等业务文字为文本，公式形式的名称不作为公式执行。时间按服务端门店时区输出，界面标签支持中英文，业务文字保持原值。

导出最多 50,000 条明细，超出返回 STORAGE_EXPORT_TOO_LARGE/422，提示缩小筛选范围，不截断结果。沿用非法筛选 STORAGE_INVALID/400、未登录 401、越权 403。响应 MIME application/vnd.openxmlformats-officedocument.spreadsheetml.sheet，Content-Disposition attachment，private,no-store。无迁移、新权限或写业务。本轮仅实现和本地验证，不发布。

测试：跨页/部分领取与取完排除、过期筛选、人数去重、空报表、数字/文本类型及公式注入防护、时区/双语、50,000 上限、真实 xlsx 重开、下载错误/重复点击/筛选草稿/切店响应、原查询回归、API/安全链拒绝。

## 实现与验证

- 导出服务复用 StorageAccess、SharedStoreIdentityQuery 和既有 POI 依赖；仓储分页与导出共用 read 过滤/排序/汇总逻辑。REPEATABLE_READ 只读事务内固定导出时刻，先统计、检查 50,000 条上限再读取全部明细。报表统计以导出时库存为准，页面查询后若业务变化，文件会反映导出时的新库存。
- 独立 StorageInventoryWorkbook 使用 SXSSFWorkbook，100 行窗口、压缩临时文件并 finally 清理；两表含冻结首行、自动筛选、固定列宽、自动估算行高、数字格式和按门店时区格式化的日期。业务文字始终使用 setCellValue(String)，不写公式。未变更通用 Excel 接口或引入新依赖。
- StorageApi 二进制下载先判断 HTTP 错误，再读取 Blob，只有当前页面/门店响应仍有效才触发下载。下载链接临时加入文档，点击后移除并延迟释放对象 URL。导出错误不伪装成 Excel；401/403 清空旧清单，其他错误保留清单并允许重试。
- 本地安全链仅新增精确 GET /api/v1/stores/*/wine-storage/inventory/export；身份和 wine_storage.view 仍由应用服务校验，其他方法和猜测路径仍拒绝。无新增权限、迁移、商业 App Gate 范围或业务写入。

后端 18 项检查通过：StorageInventoryWorkbookTest 2、StorageInventoryExportTest 3、StorageInventoryPostgresTest 7（含继承目录基线 4）、StorageInventoryQueryTest 3、StorageInventoryLocalSecurityTest 1、StorageAccessTest 2。PostgreSQL 使用 D:\RPB\target\local-postgres-current.txt 的 61644，临时隔离 schema 验证并清理。105 条实物库存验证分页只显示 50 条但完整导出 105 条，按酒名过滤后导出 53 条且统计一致。其他原库存用例覆盖人数去重、过期边界、取完/处置排除和跨租户/门店范围。

真实 xlsx 文件经 POI 重开检查中文/英文、空结果、数字类型、电话与前导零标签、公式形式业务文本、门店时区、冻结行和筛选。XML 复核两表且无公式单元格。示例仅用于测试，位于 target/storage-inventory-export/example.xlsx，不包含生产数据。

前端 65 项回归通过，覆盖已应用筛选与跨页导出、不包含 page 参数、未查询草稿禁用、重复点击、组件离开丢弃旧响应、422 可重试及 403 清空。npm run build 与 git diff --check 通过。原 12 项无关文件 SHA256 保持不变。

排错：先确认缺少工作簿实现的编译失败和新增页面 3 个失败；实现后修复测试的 POI Row 名称冲突和 Mockito 可变参数匹配，相关检查通过。代码/API/TDD 审查无未解决 P0/P1；本次只读导出，不改历史事实。证据 target/storage-inventory-export/verification.json、target/storage-export-{backend,workbook-final,frontend,build}.log。

未提交、推送或部署，未做浏览器真实下载及手机实机验收；前端下载通过 DOM/Blob 测试验证。回退本次前后端代码即可，无数据回退。
