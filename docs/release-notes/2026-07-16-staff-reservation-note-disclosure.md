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

## Rollback Notes

- 回滚本次前端组件、双语文案和契约测试提交即可恢复原展示。
- 无数据库、API 或权限回滚步骤。
- 若已部署前端，可恢复上一个前端静态资源版本并重新加载页面。
