# Release Notes

## Version / Date

- Version: staff reservation note disclosure
- Date: 2026-07-16

## New

- 当日预约卡片在备注非空时显示紧凑的“有备注”入口。
- 点击入口后在当前预约卡片内整行展开“预约备注”，再次点击可收起。
- 公网预约与租户员工创建的预约共用同一展示规则，不按预约来源区分。
- 中文与英文分别提供“有备注 / 收起备注 / 预约备注”和对应英文文案。

## Changed

- 仅调整租户员工当日预约卡片的 UI 展示；现有预约状态、分享、到店、入座、取消等操作保持不变。
- 空值、纯空格备注不显示入口；多行或长备注保留换行并允许安全断词。
- 手机、平板竖屏和平板横屏继续复用同一响应式预约卡片组件。

## Fixed

- 修复预约备注已由当日预约 API 返回、但租户员工页面不可见的问题。

## Migration

- 无数据库迁移。
- 无数据回填。
- 当日预约 API 已有 `items[].note` 字段，本次不修改 API 契约。

## Permission

- 无新增或变更权限。
- 无 App Gate、租户隔离或门店范围变更。

## Validation

- TDD RED：新增备注披露契约测试后，2 项断言按预期失败。
- TDD GREEN：`ReservationNoteDisclosureUiValidationTest` 2 项通过。
- 受影响回归：23 项测试通过，0 失败、0 错误。
- 更宽回归：25 项中 24 项通过；唯一失败为任务开始前已存在的 `ReservationShareInfoUiValidationTest.tenantAdminRouteAndWorkbenchUseBackendGeneratedManualCopyShareText`，与本次预约备注改动无关。
- 前端构建：`npm run build` 成功，357 个模块完成转换。
- 浏览器组件验证：公网有备注、员工有备注均可独立展开和收起；员工空备注没有入口。
- 响应式验证：390px 手机、768px 平板竖屏、1180px 平板横屏均无水平溢出，展开区域保持整行宽度。

## Risk

- 风险较低，范围限定在预约卡片展示层。
- 备注可能包含较长文本，已通过 `white-space: pre-wrap` 与 `overflow-wrap: anywhere` 限制布局风险。
- 备注内容通过 Vue 文本插值输出，不使用 `v-html`。

## Deployment

- 已于 2026-07-16 将提交 `0f7b2d41e79c9f21a302f46107ee2f506568434a` 的前端产物部署到生产共享目录 `/opt/rpb/frontend`。
- 本次为全站共享前端发布，不是仅发布 `lsc106`；`booking`、`lsc106`、`lsc83`、`20000000`、`platform` 五个入口均加载新入口 `/assets/index-BWyijP9Z.js`。
- 新预约页面资源 `/assets/ReservationTodayViewPage-DNVV6fYC.js` 与 `/assets/ReservationTodayViewPage-Du9vXmfn.css` 均返回 `200`，并包含备注入口、展开区域、整行布局与安全换行规则。
- 部署包 SHA-256：`4398bca6322ffcd64f27203d8dc2c3ce12766c4e073f2f0d76933d9e286c5d4e`。
- 部署备份：`/opt/rpb/backups/20260716-132517-0f7b2d41-frontend`。
- 本次为前端-only 部署：后端保持 `active`，未重启后端，未运行 Flyway，未修改数据库、权限、API、环境变量或生产业务数据。
- 生产健康检查：五个登录入口均返回 `200`，`lsc106` 当日预约路由返回 `200`，未登录 `/api/v1/auth/me` 返回 `401`。

## Rollback Notes

- 回滚本次前端组件、双语文案和契约测试提交即可恢复原展示。
- 无数据库、API 或权限回滚步骤。
- 如需生产回滚，恢复 `/opt/rpb/backups/20260716-132517-0f7b2d41-frontend/frontend` 到 `/opt/rpb/frontend`，并确认入口恢复为 `/assets/index-CteLXukj.js`。
