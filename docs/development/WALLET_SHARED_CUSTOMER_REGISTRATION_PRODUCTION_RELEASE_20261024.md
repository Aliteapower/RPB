# 共享顾客管理与顾客自助注册生产发布

日期：2026-10-24，新加坡时区。工作区 D:\RPB；用户明确授权“提交部署”。

## 发布范围与版本

功能提交 `2a7468087b7aaa61430721f750f1c65a78bb0597`，分支 `codex/paynow-payment-product-line-staging`，已推送 origin。包含钱包风格的共享顾客管理、PDPA 新增登记与历史查询、通用与钱包顾客自助注册入口和二维码。具体行为及限制见同日开发验收与 API 合约。

精确提交 52 个相关文件；10 个原有组件换行改动、3 份其他未跟踪复验文档及私密文件保留，未读取、打包或上传私密文件。没有提交依赖升级或其他产品业务改动。

结论：GO，前后端与 V067 已发布，独立公网核验通过。此记录单独提交，不改变运行功能 SHA。

## 测试与制品

- 既有本轮选定 41 项 Java 测试通过；原生 PostgreSQL 与实际 API／浏览器证据见开发验收。相关实现未再变化，复用已核实结果；未声称全量后端测试通过。
- 提交归档 2401 个文件校验 Git blob（允许 Windows CRLF 归一化）；371 个前端实现／测试源文件与已通过测试的验收目录一致。
- 干净提交目录重新执行 npm ci、162 项钱包／顾客密码前端测试、vue-tsc、Vite 构建和 Maven 后端打包，全部通过。
- npm ci 仍报告已有 7 项 high 依赖告警，依赖清单未变化；未执行自动升级。
- JAR SHA256：`f738ab7d09c32bb2d57566b2136af817e781b7cf9b88ef3b8b026abf97d2ce5c`。
- 前端归档 SHA256：`807eec9a7d55c5ee0a32c9774d65023f8c8772f8b0539a224ec42f7c4aa42048`。
- index SHA256：`7f3d6e9694dbbd40e9a3f57123b60e52a616d8784549c7bc2c57b663e10a4b68`。

## 迁移预演、备份与切换

目标 ubuntu@43.134.69.75，应用 /opt/rpb，rpb-backend:8080，数据库 rpb_acceptance。持有现有 `/home/ubuntu/rpb-ledger-release.lock`，核实旧后端 92f3fcf6、旧前端 aa0d8c83、旧 jar/index 哈希与 Flyway 066，未干扰其他发布。

先将生产 dump 恢复到不可公开连接的独立数据库 `rpb_customer_privacy_rehearsal_20261024`，执行 V067 并校验原业务指纹不变、登记表零行。初次预演 postgres 用户不能读取 /home/ubuntu 下 SQL 文件，随后改用调用用户 stdin 传输执行；未改变文件访问权限或生产数据。预演备份位于 `/opt/rpb/backups/20261024-customer-privacy-rehearsal`。

正式发布目录 `/opt/rpb/releases/20261024-wallet-customers-2a746808`。备份 `/opt/rpb/backups/20261024-wallet-customers-2a746808-before`，0700，包含旧 jar、前端、版本标记、944859 字节可读取的完整数据库 dump 和前后业务指纹。

短时阻断 nginx 到 RPB 的流量后停止目标应用，在停止边界备份；原子替换 jar，启动应用，由 Flyway 正式迁移 066→067。日志确认应用启动 16.514 秒、V067 执行 0.039 秒。校验数据库、接口就绪、进程与日志后发布前端 index，保留旧 assets，更新两个版本标记为完整功能 SHA，最后恢复流量。未重启 nginx、PostgreSQL、Redis 或 Docker。

## 生产核验

- 前后端版本均为 `2a7468087b7aaa61430721f750f1c65a78bb0597`；Flyway `067|67|0`，登记表零行，没有补造历史确认。
- 四个域名 booking、platform.booking、20000000.booking、sx.booking.yumstone.sg 返回准确新 HTML；七个页面地址和 210 个静态资源逐字节匹配制品。
- 六个原有身份／门店／顾客／钱包／交易／订位接口发布前后状态与 JSON 语义一致。九个既有未认证／跨站保护探测符合原规则。
- 新注册告知返回当前版本；缺失同意、取消勾选、旧告知版本返回 400，跨站提交返回 403，匿名读取商户告知／登记历史返回 401。未提交有效生产顾客注册，也未使用真实登录会话维护顾客或操作交易。
- 浏览器打开实际新注册页，姓名／称呼／电话／电邮及默认勾选的 PDPA 告知正确。发布前打开的标签缓存了旧入口，使用版本参数重新请求后正常；已有标签需刷新。截图：`target/wallet-customer-production-register.png`。
- 114 张原业务与配置表发布前、发布后及最终核验指纹一致，资金／积分／顾客未被验收写入。按原口径排除会话、审计、验证码、限流等自然变化表。
- rpb-backend ActiveState active，PID `1102381`，InvocationID `2cdf2dd9c1ca4204982b57b5f8b4518c`，NRestarts 0；独立最终观察进程身份不变，无新 ERROR。邻居 nginx、PostgreSQL、Redis、Docker 状态与进程身份均不变；临时流量规则已撤销。

## 兼容、风险与回退

旧打开的新增顾客表单缺少 PDPA 字段时必须刷新；编辑现有顾客无新增确认要求。自助注册沿用用户确定的手机号后六位初始密码，暂未短信验证，登记记录不证明手机号所有权或法律合规。未验证真实手机摄像头；此前注册二维码仅做图片解码。

未触发回退。未来回退时持同一发布锁、短时阻断目标流量、停止 rpb-backend，从上述备份恢复旧 jar、前端 index 和两个版本标记，再启动并验证旧服务；保留添加的 V067 登记表和记录，不删除证据，不恢复 dump 覆盖新业务数据。发布脚本已包含同等自动程序回退保护。

证据：target/wallet-customer-release-artifacts.json、wallet-customer-clean-frontend-tests.log、wallet-customer-release-build.log、wallet-customer-release-package.log、wallet-customer-rehearsal-resume.log、wallet-customer-production-deploy.log、wallet-customer-assets-proof.json、wallet-customer-api-proof.json、wallet-customer-new-api-proof.json、wallet-customer-production-final.log、wallet-customer-final-readonly.log。
