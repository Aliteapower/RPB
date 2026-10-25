# Release Notes

## Version / Date
2026-10-25 staff-storage-registration；用户已授权提交部署，发布执行中，最终记录见文末。

## New
商户在“帮顾客存酒”选定顾客后，可直接登记，无需顾客预先申请。支持选择目录酒品或登记新酒品、瓶数、整瓶/开瓶、来源、当前保存规则、收存照片、位置、标签和备注。商户必须确认已说明保存规则、获顾客同意并收到实物。

## Changed
新增 POST /api/v1/stores/{storeId}/wine-storage/registrations，契约见 docs/api/2026-10-25-storage-staff-registration.md。独立 StorageRegistrationService 编排登记，复用原 StorageApplicationService.receive 校验与入库；同事务失败回滚。原顾客申请及商户核实申请收存仍可使用。

## Fixed
代登记不冒用顾客身份，不伪造顾客申请照片或顾客自助同意。保存 staff.registered 审计、当前操作人、商户确认和真实收存照片；原申请 rulesAccepted=false，收存后直接 stored。

## Migration
无迁移。复用原租户/门店范围表、复合外键、快照、照片唯一关联和幂等命令。

## Permission
沿用 wine_storage.receive、新业务 App Gate 和有效顾客校验。无新增权限授权。新接口不在 public 路径，顾客身份不能代办。

## Risk
前后端需配套发布。没有修改取酒授权、实际交付、有效期计算、钱包金额/积分或预约/排队/Walk-in/座位/清洁流程。仅本地数据库和模拟页面验证，未触及生产记录；真实手机拍照上传未验收。

## Rollback Notes
本轮未发布。后续可回滚本轮前后端代码，已完成代登记作为原有 stored 申请和收存物品继续查询/取酒，审计事件保留。无需数据删除或数据库回滚，不整体恢复工作区。

## Review / Validation
- 接口先行定义，测试先添加并确认因 Registration/StorageRegistrationService 未实现而失败，再补实现。
- 已核查事务传播 REQUIRED、外层幂等回放、照片原子关联、操作者与顾客边界；不构造 CustomerAuthPrincipal。
- 后台真实 PostgreSQL 测试使用 target/local-postgres-current.txt 指定 61644 端口，在自动生成的隔离 catalog_cards_* 测试 schema 内执行并清理，不修改业务 schema。
- 最终验证结果以 target/storage-registration-backend-final.log、storage-registration-frontend-final.log、storage-registration-wallet.log、storage-registration-build-final.log 为准。

- 最终后台 31 项通过（含 20 项 PostgreSQL 测试），存酒前端 17 项通过，构建通过。桌面中文及 390px 手机页面检查了直接入口、目录选酒和自动匹配规则、必填照片及商户确认；没有调用浏览器业务写接口或真实相机。
- 钱包全量测试 97 项通过、4 项既有失败：顾客退出登录 2 项、顾客钱包首页 2 项；失败详情见 target/storage-registration-wallet.log，本轮未修改对应钱包实现。
- 原有 12 项非本轮改动经 SHA256 比较保持不变。

## 钱包测试修复与发布准备

已按用户要求修复此前 4 项失败：4 个用例缺少 wine-storage/home 的专用响应，错误复用了积分余额对象，导致存酒摘要渲染和组件卸载失败。补充真实形状的 Home 夹具，保留原有退出、晚响应和积分断言，额外检查顾客存酒入口。不更改生产钱包实现。修复后该文件 101/101，钱包前端全量 216/216 通过（node --experimental-transform-types --test --test-concurrency=1 src/test/frontend/wallet-*.test.mjs），日志 target/wallet-all-release-tests.log。此前 4 项失败记录为修复前证据，不是当前未解决项。
