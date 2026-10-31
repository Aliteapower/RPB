# Release Notes

## Version / Date
2026-10-25 merchant-storage-workflows，待提交、未部署。

## New
- 商户端独立的三个任务：帮顾客存酒、帮顾客取酒、存取酒记录。先查询并明确选择顾客，再办理该顾客的业务。
- 新增寄存模块顾客只读查询接口，复用租户顾客目录；仅返回 ID、姓名、电话。契约见 docs/api/2026-10-25-storage-staff-customer-lookup.md。

## Changed
- 待收存申请、可取酒品、申请/领取/操作记录限定到当前顾客；保存规则折叠为辅助设置。
- 取酒按选择酒品、核对清单、顾客授权、商户确认实物交付分步操作；不自动扣减或交付。
- 收存保留顾客先申请的原流程；后续已按用户要求新增商户直接代登记，见 2026-10-25-staff-storage-registration.md。
- 保留原有照片、修正、保存期限、幂等恢复与门店切换能力。

## Fixed
- 避免商户在混合顾客酒品列表中误选，空结果和下一步有明确提示；收存信息与原申请相同时可快捷填写核对说明。

## Migration
无数据库迁移，无新依赖。

## Permission
沿用 wine_storage.view 和 receive/deliver/rules.manage/correct 原权限。顾客查询额外要求 receive、deliver 或 customer.lookup 中至少一项，保留租户和门店边界；不授予新权限。实际收存仍经过原 App Gate。

## Validation
- StorageCustomerQueryTest、StorageAccessTest、StorageRequestSecurityTest、StorageJsonTest：11 项通过，见 target/storage-merchant-query-tests.log。
- 存取酒前端组件与选择逻辑：22 项通过，见 target/storage-merchant-workflow-regression-final.log。
- 钱包回归：14 项通过，见 target/storage-merchant-workflow-wallet-final.log。
- npm run build 通过，见 target/storage-merchant-workflow-build-verified.log。
- 只读本地模拟页面验证：桌面中文顾客查询、显式选择、取酒与记录入口跳转；390px 手机英文布局无横向溢出。没有调用真实业务写接口，没有验证真实相机、照片上传或生产存取酒。

## Risk
- 新商户页面依赖本次后台顾客查询接口，未来发布需要前后端配套。
- 未执行真实 PostgreSQL 集成验证；本轮新增后台为只读目录查询。未改预约、排队、Walk-in、座位、清洁流程。
- 原工作区钱包组件和文档已有修改保留；前一轮未提交顾客端分步页面保留。

## Rollback Notes
未发布。后续发布需记录前后端版本；回滚前端即可恢复原商户页面，新增只读接口可暂保留，无数据库回滚步骤。不得用工作区整体回滚清除其他已有修改。

## 生产部署补记

本轮已随功能提交 `590b54c1` 完成前后端生产部署，包含商户直接代登记。钱包此前 4 项失败已修复，全量 216 项及钱包/存酒组合 233 项均通过。发布范围、备份、线上验收和限制见 `2026-10-25-staff-storage-registration.md` 的生产发布结果；上文未部署/4 项基线失败等表述为开发阶段历史记录。
