# 顾客卡码开发审查

## 范围

`D:\RPB` 卡模块：卡片右上角入口、独立二维码弹窗、顾客出码接口及商户既有授权扫码执行。未改 Wallet 其他业务、产品目录、数据库结构和生产配置。

## API / 权限审查

- 契约：`docs/api/2026-10-26-card-presentation-contract.md`。
- 控制器只调用应用服务；服务复用 CardAccess、CardCommands、RedemptionRepository 与既有卡/集次规则。
- 顾客出码先校验 App Gate/门店开关，再检查 tenant/store 下的卡所有人、状态、时区业务日及额度。
- 扫码按实际操作检查商户集次或核销权限；账目操作人使用扫码商户，顾客保留为确认人。
- 出码不扣次，执行时加锁并重新校验；码单次使用，命令同键返回原结果。过期与业务日期变化均拒绝执行。
- 顾客创建的授权拒绝通过顾客 confirm/authorize 接口执行，避免自行集次。
- 无迁移、无新增权限、无接口删除；保留原商户出码流程。

## TDD 与验证

- 实现前新增 PostgreSQL 用例，首次因 PresentationCreate/接口不存在失败；实现后回归。
- 卡模块完整 PostgreSQL 测试：22 项通过；CardRulesTest 2 项通过；CardArchitectureTest 2 项通过。数据库使用 `target/local-postgres-current.txt` 指向的 61644，测试自建独立 schema，并自行清理；未更换数据库。
- 前端 `node --test src/test/frontend/cards-*.test.mjs`：9 项通过。包含编译后的 Vue 弹窗实际交互、数量绑定、集次固定数量、完成清码、过期手动刷新、错误提示、英文以及原扫码/失败恢复回归。
- 全量前端构建验证：`npm run build` 通过（含 vue-tsc）。
- 最终定向复核通过：出码三个数据库用例与 CardSecurityApiTest 四项，合计 7 项无失败；覆盖新增接口认证、防跨站、参数绑定，以及顾客自执行拒绝。
- `git diff --check` 未发现空白错误。Mockito 动态 agent 和 Surefire Windows classpath 提示属于工具链警告。

## 结论

P0/P1 未发现未解决问题。工作区原有钱包组件与未跟踪文档保留，未提交、推送或发布。

限制：没有执行手机相机、微信/iPhone/Android 实机扫码及移动端视觉验收；自动化界面验证使用 Vue 测试渲染器和模拟接口，真实持久化与权限使用 Java/PostgreSQL 测试验证。
