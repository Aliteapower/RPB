# 每日营业记录生产发布验收

2026-10-22，用户明确授权提交、部署并核对蜀香数据与其他产品线。

## 版本与发布目标

- 发布代码：`6ddda2816e37`，已推送 `origin/codex/paynow-payment-product-line-staging`。
- 从干净detached检出 `target/deploy-worktree-6ddda2816e37` 构建；前端使用 `VITE_RPB_HOST_PREFIX_BASE_HOST=booking.yumstone.sg`。
- 生产jar SHA-256：`907a01b24e9ea45d63eb240dab47a7ca867142628c0aad2e3c19faa7ceaea43f`，与上传前一致。
- 服务：`rpb-backend`，`/opt/rpb/app/reservation-platform.jar`；前端 `/opt/rpb/frontend`。公网index与干净构建逐字节一致，引用资源均200。保留旧hash资源，兼容已打开页面。
- 发布后MainPID=401720、InvocationID=`571b2f0453584e01ad3946117dcdfb44`、NRestarts=0；多次核对保持一致。正常服务未设置local或导入profile。
- 登录入口：`https://sx.booking.yumstone.sg/login`。DNS、HTTPS及证书覆盖已验证；证书保留全部原域名，仅增加sx域名，既有其他pending域名未改变。

## 数据与账号

- 显式ledger-import进程先在本地指针数据库新schema完成真实导入并正常退出，再对生产执行同一jar。生产只执行新增V056，56项Flyway迁移校验成功；未修改历史迁移。
- 来源快照SHA-256：`99bca2678c69a0f0c53d7b6fce4bb356615c063f006305d483fd0f30274a9dab`。
- tenantId=`7971bc36-4ba4-4226-93cb-0c73e249f9c0`；storeId=`e4ef2f66-ab88-4c22-884b-02bb802eba40`；租户/门店代码均sx。
- 管理员sx启用，密码按用户指定设置，公网真实登录成功。员工lwj/李文娟姓名、员工号与署名保留；旧密码算法不兼容，账号按迁移规则停用，待管理员重设启用。
- 原站4日期、7工作记录、6提交快照、89历史审计，以及配置、Logo、备注、操作人和时间，经真实公网API逐字段比对一致。跨门店访问403。线上没有2099年本地验收记录。
- 定价SGD30/月、360/年；蜀香monthly订阅30 active，记录注明测试开通、非真实收款。

## 线上浏览器验收

用户自行完成线上滑块与登录后，检查每日营业记录、餐段与日期、历史图表、设置弹窗、员工账号列表及CSV导出。

10月8日营业额5421.50、交款1762.00、非现金3649.50及各渠道回单正确。10月历史为3个最终提交日、营业额10947.90、交款3576.90，真实浏览器CSV下载内容一致。控制台无错误。验收只读，没有修改或提交真实账目。

截图：`target/daily-ledger/production-ledger.png`。CSV为当前用户Downloads中的 `daily-ledger-2026-10-01-2026-10-22.csv`。

## 其他产品线与测试

- 226项Java定向测试通过，无失败/错误/跳过；含账本、真实PostgreSQL、一次性导入Runner、AppGate、订阅，以及订位创建/到店/转排队、排队、入座、清台和员工首页回归。9项前端行为测试、类型检查、构建通过。
- 既有租户20张订位、排队、清台、收款、订阅、门店及产品配置表的行数和完整行内容指纹，发布前后保持一致。新产品权限回填为V056预期变化，不纳入旧业务数据指纹。
- 老成都及老四川指定门店的公开订位context均200；老成都租户入口200。老四川有多门店，租户级入口按既有规则409/multiple_enabled_stores，指定门店入口正常。
- 收款terminal-config与账本设置匿名访问均按AppGate返回403；auth/me匿名401。未放宽认证或权限，没有执行真实支付。

## 备份与回滚

备份目录 `/opt/rpb/backups/20261022-daily-ledger-before` 包含原jar、原frontend、PostgreSQL完整dump、源快照与发布前后业务指纹。源快照和dump仅供受控服务器访问，不入Git。

需应用回退时，从此目录恢复旧jar和前端并重启rpb-backend，再检查auth/me与旧产品入口；保留V056及已产生账本，避免覆盖新营业数据。数据库dump用于灾难恢复，不能直接覆盖发布后的合法业务写入。

## Production Readiness Decision

GO：代码已提交并发布，账号、迁入数据、公网页面及原产品边界验收通过。剩余事项为旧员工密码重设启用；本次未接入新支付网关或实际扣款。
