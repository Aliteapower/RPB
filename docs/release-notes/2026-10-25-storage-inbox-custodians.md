# Release Notes

## Version / Date
2026-10-25 storage-inbox-custodians；已按用户授权提交、推送并完成前后端生产部署，V078 已应用。

## New
- 商户存酒首屏直接展示本店顾客待审核申请，包括顾客身份、原酒品/瓶数、提交时间和原申请图文。点击即进入该申请审核，不要求手动查找或选择顾客。
- 支持核对收存或填写顾客可见原因后拒绝申请。人工代登记折叠为独立入口，避免首屏提示顾客必须先选人。
- 本店管理员维护酒妹/保管员名单：姓名/称呼、类型、启用、编辑/停用。名单不创建登录账号、不授予操作权限。
- 审核收存与直接代登记均可选择人员，默认“不指定”、可留空。收存时冻结姓名和类型，后续改名、停用不会改变历史。

## Changed
- 用户明确确认无需顾客再次授权：商户核对时可改实际瓶数，完成收存后顾客可见原申请瓶数与实际收存瓶数、核实明细及说明。例：原申请12，实际收存10；原申请不覆盖。沿用原实际收存事务和库存校验。
- 顾客与商户记录增加清楚的实收结果。原申请说明保留原文，原照片支持页内放大、继续放大/适合屏幕、鉴权原图、Escape关闭和焦点返回；默认完整显示竖图。
- 旧请求无 custodianId 时不增加JSON字段，避免旧未确定命令重放的规范哈希变化。旧 Snapshot 缺少 custodian 可正常读取。

## Fixed
- 不再把待办申请藏在顾客查询之后；审核与代登记分区。
- 保存/照片上传期间阻止切换到其他待办，防止办理中途卸载表单。

## Migration
新增 V078__wine_storage_custodians.sql：人员表、管理审计、门店启用名称唯一索引、复合范围FK和待审核索引；原收存/items表无结构变更。人员不可硬删除；历史JSON增加可空快照。

## Permission
wine_storage.custodians.manage 只补给 tenant_admin/store_manager，并同步新建管理员账号的 AppGate 默认权限集合；后端再次检查角色和门店范围。普通员工不能维护名单。
待办要求 wine_storage.view + receive，仅查询本店最早100条待办，办理后补齐。名单默认仅启用人员；includeDisabled=true 需管理员。人员维护不写交易，实际收存仍执行原商业门禁、照片及幂等校验。

## Validation
- 36 项后台通过：24 项真实 PostgreSQL 寄存测试、5 项 JSON、2 项 Access、4 项 CustomerQuery、1 项请求安全测试。包括新增迁移、名单增改停用、乐观版本、跨店/跨租户人员拒绝、普通员工拒绝、可选人员收存、姓名快照、幂等与旧JSON/请求兼容。
- 237 项钱包与存酒前端组合测试全部通过；包括无需选人审核、改12为10且原申请不变、选填人员/不选人员、名单权限、拒绝原因、顾客结果、照片放大/关闭及只读约束。
- npm run build 与 git diff --check 通过；最终制品 CSS 已确认包含竖图完整适屏修复。
- 只读环回模拟 API 验收：桌面与390px手机、中文和英文、首屏待办、直接打开审核、原申请说明/照片放大、关闭焦点返回、实际瓶数12改10、可选人员默认不指定与选择、管理员名单入口。模拟写入均405，不连接生产，不声称真实手机拍照验收。
- 指针数据库来自 D:\RPB\target\local-postgres-current.txt 的61644端口。迁移及业务写验证在自动生成并清理的隔离 catalog_cards_* schema 内执行，不修改实际业务schema。
- 一轮数据库回归遭遇瞬时 too many clients；只读检查确认连接已释放后重跑，最终36/36通过，没有调高连接数、重启数据库或中断其他任务。
- 证据：target/storage-inbox-custodians-{backend,frontend,build}-verified.log，target/storage-inbox-custodians-frontend-latest.log。

## Review / Risk
API、数据库、权限、事务/共享锁、JSON兼容、模块边界和UI自审完成。名单编排、待办查询、照片预览和收存结果展示分别独立；未修改钱包收款/积分、预约、排队、Walk-in、座位和清洁流程。
前后端及 V078 需配套发布；未验证真实摄像头和生产业务写操作。待办最多100条，按最早优先，办理后刷新补齐；照片仍由原鉴权接口控制。

## Rollback Notes
本轮已发布。回退保留 V078 表、权限、管理审计和已冻结的人员信息，不删业务数据；先回退前端入口和显示改动。回退后端前须验证旧版本对新增JSON字段的兼容，避免未验证的旧制品读取历史失败。不得整体回滚工作区已有改动。

## 生产发布结果

- 功能提交 `f4f500c917b6f06147a088d06087f14e6dfb65a2`，已推送 origin/codex/paynow-payment-product-line-staging；线上前后端均为该版本。
- 从干净 git archive 构建，npm ci、237项钱包/存酒前端组合测试、TypeScript/Vite构建及Maven打包通过。后台36项测试与发布提交代码一致，复用当前证据；没有混入工作区另有12个文件的改动。
- V078在生产数据库隔离副本 `rpb_storage_inbox_rehearsal_f4f500c9` 演练通过：145项既有业务快照一致，新名单/审计为空，仅管理员新增权限。副本和dump保留为本轮演练证据，无改动实际业务数据库。新增权限从其他权限快照中单独排除，原有 wine_storage 权限仍受快照保护。
- 实际旧590b54c1 JAR的DTO与依赖已单独抽出，在项目未自定义的Spring Jackson builder默认配置下读取新增人员快照和确认JSON成功，证据 rollback-compat.log；这是序列化兼容验证，不宣称完整旧服务回退启动演练。回退流程保留V078新增表和数据，不恢复业务数据库。
- 服务器 43.134.69.75，发布目录 `/opt/rpb/releases/20261025-storage-inbox-f4f500c9`，备份 `/opt/rpb/backups/20261025-storage-inbox-f4f500c9-before`，包含旧JAR、前端、标记及数据库dump（5,017,382 bytes，经pg_restore列表验证）。
- Flyway为 `078|78|0`。仅重启rpb-backend；nginx/PostgreSQL/Redis/Docker身份不变。新PID 1639145，InvocationID `0dc3d6baed49464f9925167083f00609`，发布及浏览器验收后仍稳定，当前进程无ERROR。
- 13项已登录QA接口检查通过：产品、卡、钱包、商户/顾客存酒历史、顾客查询、商业关闭时新代登记拒绝、新待办及人员名单/管理员列表、顾客身份不能访问商户人员。只新增/撤销本轮独立测试会话，未处理真实资产交易。
- 4域名32条公开页面路径与发布首页字节一致，11项相关静态资源与制品一致；20项匿名顾客/商户寄存、顾客查询、待办、人员接口均401。
- 生产浏览器实际验证：首屏直接显示原有12瓶待办，不查顾客即可打开审核；原图可适屏放大/继续放大并关闭；实际瓶数临时改10可编辑，返回后原待办仍12；人员默认为“不指定”，管理入口及空名单可加载。顾客端自己的空记录及390px布局正常。未提交收存、拒绝、人员创建/停用，未上传照片或执行交付。
- 浏览器验收后重新生成业务快照，145项仍与发布前一致；人员表0条，非管理员获新权限0条。原有12个本地文件SHA256未变。

制品SHA256：

- JAR `7e728ba47c91f9a2d9006e61856b5e146369bca22316035d260b497d1d85c40e`
- 前端归档 `d5fc3303ffed7481b1638655b3afde2161d25b942f410ceda9bc152f81fa48c5`
- 首页 `2a5dbb69980cecf2920d7be64d95d386f6f8acb5a3015daf30564a33c55134b0`

证据：本地 `target/storage-inbox-release-20261025/`，服务器 `/home/ubuntu/storage-inbox-release-20261025/`，包括 scope.json、artifacts.json、frontend-tests.log、frontend-build.log、maven-package.log、rollback-compat.log、rehearsal.log、deploy.log、live-readback.json、final-check.json、after-browser.log。临时发布脚本、制品及真实照片不提交。
