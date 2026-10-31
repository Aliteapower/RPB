# 当前寄存清单

在现有商户寄存页面新增 inventory 任务，不要求先选顾客。使用独立只读查询及分页组件，避免继续扩大 Home 和顾客历史组件。

当前库存定义为 remaining > 0 且实物仍由门店持有，包含已过期但未取走的实物；已取完排除。汇总按顾客 ID 去重、合计剩余瓶数，并单列已开瓶及过期瓶数。各卡片统计整个筛选结果，不仅当前页。酒妹、位置取收存时快照，顾客姓名与电话取同租户当前档案；缺失姓名显示未知。

GET /api/v1/stores/{storeId}/wine-storage/inventory?query=&status=all&page=0&size=50

仅商户 wine_storage.view，沿用 StorageAccess.staff(...,false)，关闭新业务时仍可查历史库存。不可由顾客或平台管理员访问，租户来自登录态，门店来自路径。query 最长 120 字，按字面搜索酒名、顾客姓名/电话、位置/仓位、标签和酒妹姓名；status 为 all/stored/expired。page 从 0 开始，size 1–100。非法参数返回 STORAGE_INVALID/400。

响应包含 items（酒名、顾客、剩余/原瓶数、开瓶、寄存及到期时间、状态、位置/仓位、标签、酒妹），summary（customerCount/bottleCount/openedBottleCount/expiredBottleCount/itemCount），page/size/hasMore。空结果统计为 0。分页及汇总在同一只读快照内完成，按寄存时间和 ID 倒序稳定排序。无迁移、写命令或新增权限。

测试清单：部分领取/取完排除、过期边界、开瓶统计、人数去重、跨租户/门店、姓名关联范围、搜索及分页汇总、无权限/错误参数、空数据、前端入口不依赖选客、字段/双语、重试、翻页、切店及过期响应保护。保留原 12 项无关改动。本轮实现完成后，用户已明确授权提交部署；发布证据见下。

## 实现与审查

新增 StorageInventoryController / StorageInventoryQuery / StorageInventoryRepository / StorageInventoryDtos，控制器只处理身份和参数，查询服务复用 StorageAccess 的 wine_storage.view 历史权限，仓储完成带租户、门店范围的 SQL。事务使用 REPEATABLE_READ 只读快照，让统计与分页一致；时间取同一 Clock.instant。排除 physical_disposition=disposed，包含过期但仍持有的实物。无数据库迁移、写业务、App Gate 商业范围扩大或存取酒模式变更。

商户 inventory 任务通过独立 MerchantStorageInventory.vue 和 inventory.css 实现；设置、选客、存取酒仍使用原入口。顶部刷新同时更新清单；查找与状态过滤、页码从 0 开始，最多 100000，size 1–100。空结果、加载、错误及重试有独立状态，切店/离开后旧响应丢弃，权限丢失清除清单。汇总覆盖筛选后的所有记录，开瓶每瓶计 1，不估算剩余液体体积。

本地安全链仅增加 GET /api/v1/stores/*/wine-storage/inventory 精确路径，由存酒身份/应用服务继续认证和授权。未增加其他方法、顾客路径或通配资源访问。

## 验证

- 后端合计 13 项检查通过：StorageInventoryPostgresTest 6（含继承的 4 项目录基线）、StorageInventoryQueryTest 3、StorageAccessTest 2、StorageInventoryLocalSecurityTest 1、StorageRequestSecurityTest 1。PostgreSQL 指针为 D:\RPB\target\local-postgres-current.txt，端口 61644；新建临时隔离 schema 后清理，未操作既有业务 schema。
- 真实 PostgreSQL 覆盖剩余数量、已取完/处置排除、过期边界、开瓶、人数去重、分页稳定顺序与全结果汇总、字面搜索和租户/门店隔离。应用/API 覆盖 view 权限、跨店/跨租户/平台管理员拒绝、历史不依赖新业务授权、非法参数和未登录；安全链验证其他方法/猜测路径/顾客路径拒绝。
- 62 项前端回归、npm run build 和 git diff --check 通过。前端覆盖无需选客进入、字段/统计/翻页、搜索编码与重置页码、空/错误/重试、切店旧响应和权限丢失清空。
- Chrome 组件夹具预览核对中英文文本、冻结业务名称和店铺时区日期；实际 390 像素宽度为两列摘要及详情，无横向溢出；桌面为四列摘要及三列详情。预览使用夹具响应，不作为后端连接或手机实机验收。浏览器截图接口出现超时/拼接，截图不作为验收证据；DOM/几何记录在 target/storage-inventory-preview/english-mobile-layout.json。
- 原 12 项钱包及其他无关文件 SHA256 不变。功能 53b7c094 已提交、推送并部署。

日志为 target/storage-inventory-{red,backend,security,frontend,frontend-red,build}.log，验证摘要 target/storage-inventory-20261027/verification.json。

## 排错与结论

测试先因缺失查询类编译失败，页面新增测试 3 项失败，随后实现后通过。构建发现模板汇总键类型错误，已改为显式 typed tuples。安全链测试初次缺少 MVC 公共 App Gate 依赖，按既有 WalletLocalRuntimeSecurityTest 的配置补齐测试依赖后通过，未更改业务授权以绕过失败。

API Review / TDD Review / UI Review / Code Review：通过，无未解决 P0/P1。无业务数据回填，顾客姓名来自当前档案，位置及酒妹来自收存快照；“剩余瓶数”不是原申请瓶数。回退本次应用与前端即可，不需要回退数据库。生产只读验收已在用户明确授权后完成。

## Production Readiness / GO

已检查测试、隔离、只读 API、本地安全路由、兼容性、配置和回退。无新增权限、迁移、写审计或业务状态变化；页内统计与数据使用同一只读快照。由干净提交构建并比对发布包后，仅切换 rpb-backend 与前端；备份和失败自动回退已准备。

线上 28 项登录态检查与清单字段/统计/搜索/分页/拒绝检查通过；60 页面、33 资源、64 匿名拒绝通过。152 组业务指纹及模式设置不变，数据库 V084、环境及邻接服务不变，后端进程稳定且无 ERROR。完整版本、备份与证据见 docs/release-notes/2026-10-27-storage-inventory.md。手机实机界面未另行验收。
