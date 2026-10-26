# Release Notes

## Version / Date
2026-10-26，本地实现完成，未提交、未部署。

## New
租户管理员在酒水寄存管理维护当前门店的常用存放位置，可新增、修改名称。默认提供“前台寄存处”，不需要为读取默认值写入数据库。

## Changed
审核收存与商户代登记共用位置下拉框，默认选中默认位置，允许选择“不指定”。具体仓位单独自由填写，也可留空。管理后可在表单刷新常用位置。

## Fixed
收存冻结所选位置名称；后续修改位置名称不改变历史。原有自由文字位置继续按原值展示。未影响预约、排队、到店、排位及清洁流程。

## Migration
V081 仅新增按 tenant/store 隔离的位置和审计表、约束与索引，不改写已有业务记录。新增 ReceiptLine.storageLocationId 与快照 storageLocation 为可选字段；null 字段省略，保持旧命令幂等摘要兼容。

## Permission
维护要求 tenant_admin 角色及现有 wine_storage.rules.manage 权限，并经过 App Gate。选用要求现有 wine_storage.view 权限。无新权限、无角色授权迁移。

## Verification
- PostgreSQL 指针端口 61644；完整 StoragePostgresTest（28 项）、StorageJsonTest（6 项）、StorageRequestSecurityTest、StorageLocationApiTest、StorageAccessTest（2 项）通过。
- 最后调整后重跑新增 PostgreSQL 用例、JSON/API/安全用例及 CardArchitectureTest，通过。包括重复提交、名称与版本冲突、跨店/租户、普通员工禁止维护、Gate 拒绝、客户端伪造名称与历史冻结。
- 前端存酒/扫码/相机相关 67 项通过，npm run build 通过，git diff --check 通过。
- 只读本地浏览器夹具验证默认值、空仓位与 393px 手机布局；未通过生产业务表操作或手机实机证明。
- 保留清单中 12 个原有无关文件的 SHA256 一致。
- 日志、只读夹具及截图：target/storage-locations-20261026/。

## Risk
上线需前后端配套及 V081。常用位置按当前门店维护，不在不同门店间共享；本轮仅提供新增和改名。实际手机操作未验收。

## Rollback Notes
发布时保留上一版前后端包。回退应用可保留 V081 两张新增表，不删除位置与审计数据、不重写历史快照。旧前端仍可使用原 location 文本。若已产生新记录，旧版本界面可能只显示仓位，排查时须保留 snapshot.storageLocation。
