# 员工登录账号与密码维护

2026-10-30，D:\RPB。本地实现验证，不提交、推送、部署。保留已有工作区改动。

## 规格与模块边界

员工编辑页独立显示绑定账号用户名、状态、本产品访问授权及六位新密码输入。旧密码仅保存哈希，不回显原文；新密码可显示/隐藏，留空不修改。密码单独保存，不携带或清空员工资料草稿。新增员工仍用原创建/绑定流程，创建仅授予workforce.view，绑定已有账号保留其他原权限。

认证模块负责账号元数据读取、加密、账号版本和会话撤销；workforce独立登录管理服务负责AppGate、员工可编辑范围、管理员等级与审计。目标账号必须为当前租户非总部普通员工/门店管理账号；总部可管理下级账号，门店管理者只可管理普通员工，必须覆盖员工和账号全部门店，不可修改自己或管理身份账号。停用账号不得通过改密自动恢复。

## API契约

- GET /api/v1/stores/{storeId}/workforce/employees/{id}/login-account：employeeManage且员工完整可编辑范围，返回accountId、username、status、version、workforceAccess、canReset；未绑定返回空。绝不返回哈希/密码。
- PUT 同路径：expectedVersion、password；六位英文字母/数字，按既有登录规则忽略大小写。先锁员工及账号，校验绑定关系/权限/版本。成功返回无秘密元数据；原密码失效，全部既有会话撤销。相同密码且版本恰好+1的重试返回现状，不重复审计；旧版本或不同命令409，格式400、越权403、缺失404。身份/跨站写入沿用EndpointSupport，no-store。
- 不新增数据库字段或迁移；不改变账号状态、角色、门店范围或其他产品权限。审计仅元数据，不记录请求密码或哈希。

## 测试清单

实际PostgreSQL：创建后真实AuthApplicationService登录、principal仅workforce.view、租户/门店匹配；新旧密码切换、会话撤销、版本重试/冲突、停用/管理账号/跨门店/跨租户/普通员工拒绝、审计失败回滚。API检查身份/跨站/精确安全路径；前端组件检查账号显示、显示隐藏、留空不写入、保存失败/成功/草稿保留。

## 验证结果

证据目录 `target/workforce-employee-login-20261030/`。读取项目本地指针，使用61644/postgres的随机测试schema，完成后清理；不写现有业务schema及生产。

| 验证 | 结果 | 证据 |
| --- | --- | --- |
| 新登录管理PostgreSQL专项 | 5/5 | backend-green.log |
| 原创建/绑定账号回归 | 2/2 | backend-regression.log中WorkforcePostgresTest |
| 新接口身份、跨站及409映射 | 3/3 | backend-final.log |
| 新接口精确Spring Security路径 | 1/1 | backend-final.log |
| 前端完整回归 | 789/789 | frontend-final.log |
| Vue类型检查 | 通过 | typecheck-final.log |
| 前端生产构建 | 通过 | build.log |

后端专项实际执行AuthApplicationService登录并创建会话：新账号仅workforce.view、store_staff及分配门店；改密后旧密码拒绝、新密码按既有大小写规则可登录，原会话撤销。验证成功重试/旧版本冲突、普通员工/本人/越店/跨租户/停用/总部账号拒绝，门店管理员可修改普通员工但不可修改其他管理账号，总部可管理下级门店管理者，AppGate仍阻断。绑定已有钱包账号改密后原wallet.view保留且无新增授权；审计不含密码/哈希，审计失败回滚。

首次测试发现auth_accounts初始版本为0，已修正新接口校验为非负数；回归测试覆盖首次改密。另修正测试替身的AppGate重设方式及UI测试节点空值检查，最终均通过。

本地浏览器预览使用真实EmployeeForm与独立密码组件、内存示例API，禁止实际保存。检查桌面和390px窄屏，document.scrollWidth=clientWidth=375，无全局横向溢出。账号刷新后姓名草稿保留，账号信息及独立密码区域显示正常。截图employee-login-desktop.png、employee-draft-retained.png、employee-login-mobile.png；结束重置视口并停止本轮预览服务。该证据为页面预览，不是生产账号验收或完整HTTP登录端到端；登录依据来自真实PostgreSQL认证服务测试。

## API与代码审查

接口沿用EndpointSupport的no-store、身份和跨站写入校验；只增加精确GET/PUT安全路径。控制器不访问数据库；认证模块独立负责哈希与会话，workforce负责员工范围及目标账号权限，未改原EmployeeWrite契约或工资/考勤计算。写入在同一事务锁员工与账号，按账号版本拒绝过期覆盖，审计仅保存元数据。P0/P1未发现未解决项，本地交付通过。

## 迁移、回退及限制

本轮无迁移、新权限或产品开通。旧密码始终不可读取，只能设置新密码；停用账号不会因改密启用。账号密码为跨产品共用，已有多产品账号改密会影响其原有登录；新建仅打卡账号不会获得收款、钱包、预约权限。本轮不自动为旧账号补权限或变更产品商业授权。账号近期登录会更新版本，管理员遇到409需刷新账号再保存。

如未来发布后需回退，可回退本轮前后端版本；已更改的密码继续有效，被撤销的会话不恢复，不回写旧哈希。没有影响预约、排队、入座、清洁、钱包业务、历史核准或工资快照。

原有未提交工作已按SHA256核对：仅在已有LocalRuntimeSecurityConfiguration上追加本轮两条精确路径，前轮路径保留；其余已有改动未修改。本轮未提交、推送或部署。
