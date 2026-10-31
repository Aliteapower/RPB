# 员工考勤与资料页面优化：生产发布

后续布局复验：同日已完成登录后桌面与手机视口检查，修复公共容器宽度及保存栏样式，并仅更新前端为 `06b889e6`；后端仍为本记录的 `50bebc35`。见 [登录后验收及修复记录](WORKFORCE_UI_BROWSER_ACCEPTANCE_20261031.md)。

2026-10-31，D:/RPB。用户明确授权“提交部署这几次优化”。本轮合并发布本聊天的考勤设置与员工编辑页两轮优化，未混入钱包或其他产品的未提交内容。

## 结果

GO：功能提交 `e4aae29f462d79d2b92a097f99fbc112563b5b75`，配套原有回归操作路径更新 `50bebc357dc53c790fc046e6a7ef5cd9b424b214`。两者已推送 `codex/paynow-payment-product-line-staging`；实际前后端 RELEASE_SHA 均为 `50bebc35`。

- 审批人显示当前账号姓名，缺失时显示姓名不可用，不展示 UUID。姓名查询限制租户和目标员工，原审批记录与快照不回写。
- 考勤页按“考勤设置 / 居家地址 / 变更历史”切换；明确当前、未来和已结束策略；连续等价记录合并展示但原始日期与版本完整保留。
- 员工编辑页保持单页，聚焦基本资料，任职和账号、薪资、证件按需展开；新员工必填任职信息默认展开。收起保留输入，校验失败展开对应区域，区分员工资料与独立业务操作的保存。

## 制品与验证

- 白名单包含 19 个功能/文档/测试文件以及 2 个既有前端回归文件。钱包 8 文件等 35 份排除材料的 SHA-256 前后完全一致。
- 干净提交归档构建，3870 份提交文件与候选一致，允许 Git CRLF/LF 归一化。V113 只调整候选内的换行以保持线上旧文件字节，SQL 文本未变；全部迁移资源最终逐字匹配线上。
- 干净候选 `npm ci`、Vue 类型检查、Vite、Maven package 均通过。运行时依赖与生产逐字相同，无依赖升级；npm 报告的既有 8 项漏洞（3 moderate、5 high）未在本轮扩大处理。
- 前端两组相关回归分别 47/47、203/203，通过共 250 项。旧测试新增先点击地址功能页或展开薪资的步骤，继续验证原有权限、原请求重试、薪资新标签页与员工草稿保留；没有删除断言。验证适配器补充 JSON 模块加载，保存在 target，不改运行代码。
- 后端复用同一代码状态已核实的 108 项测试（45 + 3 + 59 + 1，含继承执行）；候选全部 2562 个运行类逐字等于这些测试使用的类。相对线上只变化 `WorkModeDtos$WorkModePage`、`WorkModeRepository`、`WorkModeService` 三个类。
- 构建 JAR SHA-256：`d991b1c330fc72d236bd173de1ce01a76e58aac7cee6de459ff8d8cd9ef7af4a`。
- 前端归档 SHA-256：`cb5d739027503f42e634d3676ecb4b8e8cf8318d51331cd55974e12b10ae1a0e`；index：`5eb7c6ec5ad68655164a751dc21b02439e2565f30009b8a0ae4eae436d2fd277`。

## 生产切换与核对

目标仅 `43.134.69.75 /opt/rpb`、`rpb-backend` 与 `rpb_acceptance`。切换前重新核实前端 `474c72a4`、后端 `d23a94f4`、Flyway `113|113|0`。

备份 `/opt/rpb/backups/20261031-workforce-ui-50bebc35-before`，包含原完整前端、JAR、版本标记、环境及停服数据库 dump。dump SHA-256：`3bb53371db9af9333b054e79b4bb69095d5c713a8bdaa546e4bbbbcd9d9d6aee`，pg_restore 目录检查通过；本轮未进行数据库恢复。

阶段制品 `/opt/rpb/releases/20261031-workforce-ui-50bebc35`。停服备份后切换后端，前端保留旧不可变资源、原子切换 index。420 份前端文件逐一哈希相等。241 张业务表完整指纹、列结构前后相同，`changedTables=[]`；无新迁移，Flyway 仍 `113|113|0`。环境与 nginx/PostgreSQL/Redis/Docker 进程身份不变。

后端 active，PID `3670119`，InvocationID `60a834e351254ab19f07505f3d3ee574`，NRestarts=0。本次实例日志 ERROR 为 0，最终核对时身份稳定。

公网 5 个页面路由返回新 index，7 份相关 JS/CSS 实际字节匹配；7 个受保护读取匿名 401，2 个缺写保护的 PUT 返回 403。仅执行读取和拒绝路径检查，未进行真实员工资料、审批、打卡、密码、证件、工资或付款操作。

## 边界与回退

未完成登录后的桌面、移动端浏览器视觉验收，不将构建/组件测试或公网资源检查描述为视觉或真机验收。先前已知、未修改的 phase4 工资单旧请求断言仍单独记录于两轮开发文档，不声称全部仓库测试全绿。

本轮无 schema、环境、权限或写入模型变化，回退可先停后端，恢复备份 JAR、index 和两个版本标记，保留现有静态资源及数据库，再启动并核对健康。发布脚本已包含该应用回退流程，正式切换未触发回退；数据库不得自动回滚。

证据：`target/workforce-ui-release-20261031/` 的 `scope.json`、`artifacts.json`、`resource-normalization.json`、构建/测试日志、`production-before.json`、`deploy-result.json`、`public-result.json`、`final-result.json`。服务器同名 `/home/ubuntu/workforce-ui-release-20261031/` 保留切换及全库聚合指纹证据，不下载真实业务数据或环境内容。
