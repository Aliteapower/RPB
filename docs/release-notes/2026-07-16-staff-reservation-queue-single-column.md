# Release Notes

## Version / Date

- Production frontend deployment
- 2026-07-16

## New

- 无新增业务能力。

## Changed

- 租户员工 H5 的预约页面在平板横屏与更宽视口下，日期/快捷操作区和预约列表区改为全宽上下排列。
- 租户员工 H5 的排队页面在平板横屏与更宽视口下，排队管理区和消息/排队列表区改为全宽上下排列。
- 保留排队列表在 1200px 及以上视口内部双卡片排列，避免扩大本次外层工作台布局调整的影响范围。
- 手机 H5 原有布局、页面顺序和交互保持不变。

## Fixed

- 修复平板横屏下预约和排队页面被拆分为左右窄栏、内容利用率不足的问题。

## Migration

- 无数据库迁移。
- 无 PostgreSQL 表结构、数据或本地运行时配置变更。

## Permission

- 无 App Gate 权限新增或变更。
- 无租户、门店或员工授权逻辑变更。

## Risk

- 变更仅删除两个页面的桌面外层双列 CSS 覆盖，风险集中在预约和排队页面的平板/桌面视觉布局。
- 未修改 Vue 模板、状态管理、接口调用、i18n、错误处理或 Reservation / Queue 业务流程。
- 预约、排队以外的 Walk-in、Seating、Cleaning 页面不受影响。

## Verification

- `StaffPrimaryWorkbenchTabletUiValidationTest`：4 项通过。
- 员工工作台相关回归测试：38 项通过，0 失败。
- `npm run build`：通过。
- 本地 1280px 横屏实测：预约与排队外层工作区均为单列上下排列、上下区域等宽，无横向溢出。
- 竖屏路径沿用原有单列样式；响应式契约测试确认已移除 1024px 外层双列覆盖，且手机端样式未被修改。
- 全量 `mvn test` 已尝试，但被本次范围外的既有问题阻断：分享文案字符编码断言失败，以及共享 PostgreSQL 中历史 `codex-*` 门店被 `audit_logs` 外键引用导致集成测试清理失败；未清除或修改不属于本任务的历史数据。
- 干净 detached worktree `4d65505d` 重新执行员工工作台相关回归测试：38 项通过，0 失败；`npm ci` 审计 0 个漏洞，生产构建转换 357 个模块并成功完成。
- `booking`、`lsc106`、`platform` 三个生产登录入口均返回 `200`，并加载新入口 `/assets/index-CteLXukj.js`。
- 新预约与排队 CSS 资源均返回 `200`，且不包含已废弃的外层双列规则；后端服务保持 `active`，未登录 `/api/v1/auth/me` 返回 `401`。
- 登录后的生产视觉验收尚未执行：Chrome 会话已过期且登录页包含滑块验证码，需取得针对该验证码的确认后才能继续。

## Deployment

- 已将提交 `4d65505d507592d3e4e81c2875d6622aa3056263` 的前端产物部署到 `booking.yumstone.sg` 与 `lsc106.booking.yumstone.sg` 共用的生产前端目录。
- 生产入口由 `/assets/index-CEyPae27.js` 更新为 `/assets/index-CteLXukj.js`。
- 部署归档 SHA-256：`e64cc16f201f6b38fccadd2f9566dd02995e30aa5f17c8c2f196ad77d0574a3a`。
- 部署备份：`/opt/rpb/backups/20260716-120417-4d65505d-frontend`。
- 本次为前端-only 部署：未重启后端，未运行 Flyway，未修改数据库、权限、API、环境变量或生产业务数据。

## Rollback Notes

- 如需生产回滚，以 `/opt/rpb/backups/20260716-120417-4d65505d-frontend` 恢复 `/opt/rpb/frontend`；回滚后确认生产入口恢复为 `/assets/index-CEyPae27.js`。
- 代码回滚本次提交即可恢复预约与排队页面在 1024px 及以上视口的外层左右双列布局。
- 无需回滚数据库、权限、配置或数据。
