# Release Notes

## Version / Date
workforce-local-readiness / 2026-10-29，D:\RPB；本地开发记录，未提交、推送或部署。

## New
新增整体验收、真实Flyway增量升级验证、150文件候选清单和本地回退演练证据。

## Changed
持久化/内部错误额外返回服务器生成的 X-RPB-Request-Id，错误状态、错误码和响应体保持兼容。

## Fixed
修复workforce错误缺少可关联日志的问题。仅记录UUID、方法、路由模板、异常类型和SQLState，不记录异常消息、堆栈或工资/个人资料。

## Migration
本轮无新迁移。完整候选包含既有V088..V091；实际V087旧资料增量升级、重复执行0项及旧应用读取验证通过。

## Permission
无新增权限、AppGate授权或订阅变更。权限默认分配/显式工资委派与禁用产品拒绝新业务有本地验证。

## Risk
82项真实API检查、15项当前定向测试、9项回退检查、三类CSV独立解析及候选前后端构建通过。真实工资、原生Excel、实际手机和打印验收未完成；生产基线未核实。未做全项目回归或容量验收。预订/排队/Walk-in/座位/清洁无业务改动；8个钱包本地修改保留且未混入候选。上线判定NO_GO。

## Rollback Notes
诊断修复可随workforce后端恢复为已确认兼容的旧产物，不涉及数据库回滚。完整模块回退需先通过现有产品设置关闭新业务、核实在途写入及旧版兼容，再切回真实生产旧产物；保留V088..V091和工资付款历史，禁止删表。操作步骤及本地演练限制见 docs/development/WORKFORCE_PRODUCTION_READINESS_20261029.md。
