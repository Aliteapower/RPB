# 存酒操作记录显示操作者姓名（2026-10-27，已发布）

## 根因与修复

截图中的操作明细直接渲染 event.actorId，Home.events 也没有操作者姓名字段，因此显示 UUID。

Event 响应增加可空 actorName，保留 actorId。StorageRepository.events 以一次带租户和门店范围的查询关联账号/顾客姓名，避免前端逐条查人员或额外申请人员目录权限。只读投影不修改账号、顾客或历史审计事实。

- 商户操作：按同一租户的 auth_accounts 读取 display_name，空白时退回 username。
- 顾客申请、撤销、授权及顾客来源领取意图：使用同租户、同事件顾客的档案 display_name；已知事件种类区分账号与顾客身份，避免简单按 UUID 混合姓名。
- 停用/软删除的账号保留姓名查询，不恢复登录或业务权限。
- 找不到姓名时 actorName 为 null/缺省，主界面显示中英文“未知操作者 / Unknown operator”，不把 UUID 当姓名。
- 名称取当前档案，不伪称操作时姓名快照。旧审计 ID、内容、时间不变，无历史回填。

商户操作明细及兼容旧页面同时改用 actorName。当前顾客记录页没有操作者显示入口，未扩展其界面。

## API / Database Review

契约已补充 docs/api/WINE_STORAGE_API.md。Home.events 的可空新增字段保持兼容；Event 六参数构造仍保留，旧序列化事件没有 actorName 也能读取。即时写命令及旧幂等响应可无姓名，查询 Home 时统一解析姓名。

关联账号必须 a.tenant_id=e.tenant_id，顾客必须同时符合租户、actor_id 和事件 customer_id。保留原事件查询的 tenant_id/store_id/customer_id 条件及原 Home 权限。没有新端点、写权限、迁移或业务状态变化。

## 测试与排错

先增加前端姓名/缺省回归，原实现 51 通过、1 失败；后端因缺少 actorName 访问器编译失败。

最终：
- mvn -q '-Dtest=StorageEventNamesPostgresTest,StorageJsonTest,StorageAccessTest' test：新姓名 PostgreSQL 套件 6 项、原 JSON 套件 6 项、权限 2 项通过；随后新增旧事件兼容用例，定向运行 StorageJsonTest，7 项通过。共 15 个不同后端用例通过，无失败/跳过。
- PostgreSQL 使用 D:\RPB\target\local-postgres-current.txt 指向的 61644，隔离临时测试 schema，未改业务库。覆盖姓名、停用软删除、空名退回账号名、跨租户/门店/顾客隔离，以及同号员工/顾客在已知事件种类下正确解析。
- node --experimental-transform-types --test src/test/frontend/wine-storage-page.test.mjs src/test/frontend/wine-storage.test.mjs src/test/frontend/storage-merchant-scope.test.mjs：58 项通过，覆盖姓名显示、缺省文案、中英文与 UUID 不再作为主显示。
- npm run build、git diff --check 通过。
- 原有 12 项无关文件 SHA256 不变。

日志 target/storage-actor-name-backend.log、storage-actor-name-json-compat.log、storage-actor-name-frontend.log、storage-actor-name-build.log；结果摘要 target/storage-actor-name-20261027/verification.json。

## Code Review / 剩余风险

只改存酒记录投影、兼容 DTO、两处操作者渲染及测试文档；保留审计事实、权限、事务、顾客归属、租户和门店范围。未发现未解决的 P0/P1。

姓名来自当前档案，改名后历史界面随档案显示新名；无可靠档案时显示未知，不猜测姓名。功能 e2aa7d3a 已提交、推送并部署，生产只读验收通过，手机实机界面未另行验收。

## Production Readiness / GO

本轮按 production-readiness 检查后发布：既有 15 项后端用例通过；干净提交再次通过构建和 58 项前端回归。无新迁移、权限、App Gate、环境变量或写事务变更，姓名查询保留租户/门店/顾客范围，旧 DTO 与审计事实兼容，已准备自动程序回退。14 个 DTO 的构建字节差异经 javap -c -p 比较方法及签名一致，真正行为变化限于 Event 和 StorageRepository。

线上 42 条姓名记录、15 项登录态读回、56 页面、33 关键资源、60 匿名拒绝通过；152 组业务指纹与发布前一致。原服务进程身份持续稳定且无 ERROR，数据库 V084 不变，环境和邻接服务不变。完整备份和验收结果见 docs/release-notes/2026-10-27-storage-actor-names.md；本地证据 target/storage-actor-name-release-20261027/final-check.json。Code Review: APPROVE，无未解决的 P0/P1。
