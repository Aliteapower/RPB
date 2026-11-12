# 打卡许可多选与外勤名称发布记录

## 范围与状态

2026-10-31，D:/RPB。本地实现、独立审查、真实HTTP验收、生产备份恢复演练及发布后核对已完成。用户已授权本轮提交、推送、部署。

管理员设置可并存的办公室、居家、外勤许可及默认方式；员工每次选择班次快照允许的方式。外勤可填写至多200字符的地点／客户名称，和服务器实际打卡时间一起保留。办公室上班、外勤下班归入同一出勤，不新增工时或工资公式。

个人及岗位无实质变化保存不增加策略记录／版本／审计；精确命令回执仍保存。居家待办和历史分开，区分批准待生效、申请过期及同日冲突；不修改旧日期、不自动处理任何真实或QA申请。

## 模块、契约及迁移

复用 workmode、positions、attendance、原核准与工资模块；新增纯许可解析 WorkModeOptions。见 [接口与错误码](../api/2026-10-31-workforce-clock-options.md)、[规格](../superpowers/specs/2026-10-31-workforce-clock-options-design.md) 及 [实施计划](../superpowers/plans/2026-10-31-workforce-clock-options.md)。现有 [考勤方式MD](WORKFORCE_WORKMODE_20261030.md) 已追加增量说明。

V113只新增nullable许可数组、事件实际／请求方式和事务内命令回执；无旧数据回填、不改V001–V112、不自动扩大许可。旧缺新增字段仍按原单方式解释。原App Gate、本人打卡、完整员工管理范围、公司门店隔离及居家几何隐私投影继续执行。无新增公开接口、环境变量或权限授予。

已确认／发布工资、付款、历史考勤及自由员工全额基本工资规则保持；跨公司归属及已发布金额更正规则没有改动。Reservation、Queue、Walk-in、Seating、Cleaning业务源码未触及。

## 本地验证证据

- 本地数据库严格使用 `target/local-postgres-current.txt` 的D:/RPB运行环境，61644/postgres。独立副本 `workforce_browser_ceeab57877054d7f94a3125637dd8280`，新服务8104／5204；原验收服务和fixture保留。
- 后端累计59个唯一用例通过，含PG、V112→113迁移、旧JSON重放、权限／嵌套隐私、核准、考勤及free全额工资。岗位补修先出现真实RED，再定点10/10 GREEN。证据 `target/workforce-clock-options-20261031/backend-test-summary.json`、相关XML及日志；两任务审查报告保留在本轮SDD目录。
- 前端相关184/184通过；干净候选 `vue-tsc` 与Vite exit0。扩展1039项首次1036通过：两条候选验证测试因干净归档尚无target目录失败，补目录后该文件3/3通过；剩余一条未触及基线 `scripts/tests/workforce-phase4.test.mjs:29` 仍期待工资单仅请求旧端点，但已发布补充工资组件会调用个人补充工资端点。断言及其组件／夹具均与BASE2d8bdffe一致，本轮不扩大修改；不声称全扩展套件全绿。详见 `target/workforce-clock-options-release-20261031/frontend-verification-summary.json`。
- 真实HTTP43请求、13组行为断言通过：办公室IN→办公室休息→外勤休息结束→外勤OUT共1 Session／4事件，名称与服务器时间保留；精确重试、权限／定位拒绝、no-op历史和审计不增长、固定回执及过期版本、未来居家批准和同日冲突均验证。240表467条旧行按原列／主键摘要全部不变，包括工资付款。见 `target/workforce-clock-options-20261031/integration-report.md`。
- 干净候选JAR的2562个运行类逐字等于通过测试的运行类；V113字节一致。JAR SHA256 `37e4a5676eca343b65308dc902e8d4ca1bb5b37ca3864ca5b276cfac92eabc56`；制品证据 `target/workforce-clock-options-release-20261031/tested-artifact-proof.json`。

## 未验证与剩余风险

CUA浏览器创建／读取页面持续超时，没有真实窄屏视觉或手机GPS验收；组件换行与表格滚动仅经组件测试／构建核对。克隆没有既有过期pending地址，HTTP过期批准未覆盖，使用固定Clock的后端PG场景验证。没有真实外勤、真实申请审批或生产打卡写入。

原有历史重复策略行保留。旧员工许可不自动改变；真实配置变更仍遵守原次日和未来日期顺序。名称仅为业务说明，不作为GPS证据，也不推断独立的客户拜访时长。

## 发布与回退约束

仅白名单成果提交；钱包8文件等35份既有排除材料按哈希保留。生产先核对原324179b9／V112和相邻服务，再执行隔离备份恢复、V113升级与重放验证，停服取新备份后切换制品。

V113本身无旧数据更新；发生新许可、多选、事件方式或options快照／回执写入后，不能盲切旧包。异常恢复必须先停后端，再检查新数据；停止或检查失败不启动旧包；有新数据保留数据库供兼容修复，不自动恢复生产数据库。无新数据才按经过验证的旧制品恢复。生产脚本以 `sudo -n python3` 执行，备份目录0700。

## Production Readiness

GO：本轮功能、权限、兼容、审计、制品、迁移／恢复及发布后核对通过；无未处理Critical／Important审查问题。既有测试断言及视觉缺口按上文保留，不宣称全套测试或真机验收通过。


## 生产结果

- 功能提交／运行版本：`d23a94f43465956b5946ac234944554a05d865dd`，已推送当前codex分支；前后端RELEASE_SHA一致，制品哈希一致。
- 隔离恢复：新鲜生产V112备份恢复后升级1项，validate通过，重跑0项；239张旧业务表原字段不变。V113新备份完整恢复241表、2序列及全部结构相等。私有演练库保留并禁止连接，未恢复或修改生产业务数据库。首次执行发现服务器仅有JRE，探针改用本地同Java21编译后重试，未启动业务进程或更改生产数据。
- 正式切换：Flyway `113|113|0`；239张旧业务表原字段不变，`oldBusinessChangedTables=[]`；新增命令表0行，旧许可未自动扩张。所有418前端文件核对一致，邻近nginx/PostgreSQL/Redis/Docker进程与环境文件未变。
- 服务：rpb-backend active，PID3626821，InvocationID `f80c4affd425420f8a9acb8b88987df8`，NRestarts0，新实例ERROR行0。
- 公网：6个页面入口返回本次index，6份受影响脚本逐字哈希相等；11个受保护接口匿名401，无保护写403。仅只读／拒绝路径验证，没有真实申请审批、打卡、付款或工资写入。
- 停服新备份：`/opt/rpb/backups/20261031-workforce-clock-options-d23a94f4-before`；阶段制品：`/opt/rpb/releases/20261031-workforce-clock-options-d23a94f4`。停服数据库备份SHA256 `19da4fe1d97958ce2c6e834ccd9bdb14c97349c44ee7eacbd881c310b8161e13`。
- 最终恢复保护修复并独立复查：先停服务再判断新数据，8项模拟测试通过；脚本SHA256 `f9583e7a7507e8a773cc1436da8f13b72da91de258e89c9d933714368227433f`。未演练生产回退或自动恢复生产数据库。
- 证据：`target/workforce-clock-options-release-20261031/{source-proof,tested-artifact-proof,rehearsal-result,deploy-result,final-result,public-check}.json`。3852份提交文件与干净候选一致（仅Git换行归一化），全部2562运行类匹配已验证类；35份排除材料含钱包8文件哈希最终未变。
