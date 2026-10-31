# 商户查看顾客 PayNow 凭证

2026-10-25；D:\RPB；已按用户“提交部署”授权发布生产。

## 原因与修改

商户交易详情已挂载收款凭证组件，但历史查询仅返回已关联实收的凭证，顾客提交后尚未确认的记录被过滤。查询现在按原tenant/store/source范围返回已绑定凭证及顾客提交凭证，未绑定的员工扫描草稿仍不展示。

充值和账单交易详情显示顾客上传的原图入口、付款参考号、银行Ref及核对结果，待核实项明确标记“顾客已提交，尚未核实收款”，不显示人工确认的实际收款账户。已确认项保留原确认信息。页面增加刷新、加载、空记录及错误状态，中英文同步。

## API、数据库和代码审查

- 复用原GET历史查询与原图接口，保留wallet.view权限、源单据检查及租户/门店过滤；选用、确认凭证仍要求原确认权限。
- OR条件完整加括号，不能绕过tenant/store/source约束。列表只查元数据，不加载原图字节；原有充值和账单索引继续适用。
- 无表结构、迁移、权限、环境变量或依赖变更。无账务命令变化，查看、刷新及原图读取不增加余额或实收。
- 原响应结构不变；未核实项confirmation为空，前端不会将上传等同于到账。员工确认草稿不进入历史列表。

## 验证

- 先复现失败：真实指针PostgreSQL中顾客凭证已保存，但商户列表为空；新增前端回归也验证原空状态和状态标识缺失。
- 44项前端回归通过，覆盖未核实/已确认、原图入口、OCR不匹配、刷新、新源单据与跨门店过期响应、错误状态、中英文；类型检查及Vite生产构建通过。
- 30项后端回归通过：19项真实PostgreSQL交易测试、8项员工/顾客凭证API测试、3项请求安全测试。数据库严格使用target/local-postgres-current.txt指向的61644端口及随机隔离schema，测试后清理测试schema，不改共享业务数据。
- 新增真实数据断言覆盖wallet.view员工读取待核实原图、无权限/匿名/跨租户/跨门店拒绝、未知单据、充值及账单源隔离、员工草稿排除、确认后只展示一次、查看前后余额及实收不变。
- 代码审查通过：改动限于原只读查询和既有凭证组件；权限、原图响应头、确认事务及幂等流程未修改。证据：target/wallet-proof-visibility-backend-red.log、wallet-proof-visibility-red.log、wallet-proof-visibility-backend-tests.log、wallet-proof-visibility-tests.log、wallet-proof-visibility-build.log及surefire-reports。

## 风险与回退

需同时部署前后端，旧后端仍会过滤未确认凭证。本轮未执行生产顾客图片读取、收款确认或密码操作，未取得浏览器/真机视觉验收证据。

回退本次查询条件和凭证组件、语言包修改即可恢复原行为，不恢复数据库，也不删除已上传凭证。

## 生产发布记录

- 功能提交 `05bf684e72089a145924edf7b7207fbd607bdca5` 已推送 `origin/codex/paynow-payment-product-line-staging`，仅8个相关文件；原有8个钱包组件换行状态及未跟踪材料保留，未纳入制品。
- 干净Git归档2484文件核对通过，1989个源码文件与已测试工作区一致；独立安装锁定依赖后47项前端测试、类型检查、Vite构建及Maven package通过。此前30项后端结果按源码一致性核验后复用。
- 与线上旧JAR逐项比较，仅WalletCollectionProofRepository.class发生实质变化；73个资源仅CRLF/LF差异，配置、迁移语义与全部依赖条目不变。Flyway保持070/70/0。
- 发布目录 `/opt/rpb/releases/20261025-proof-visibility-05bf684e`，前后端版本标记均为功能提交。备份 `/opt/rpb/backups/20261025-proof-visibility-05bf684e-before`，包含旧JAR、完整前端、版本标记及1083528字节数据库dump，pg_restore目录验证通过。
- JAR SHA256 `26cfda83d3209d05e7efe762ba8b5ae4bba2dc72f370b6ccf517d81e66ac53ec`；前端归档SHA256 `024598b4b5f72215bb5a44f1e12bf08728f4fb357e4acb4f3b760ce03000d906`；index SHA256 `9f29d1487021d1d6dee2027f62c1062111c74842ee7dc79191a460b7bb73a7f8`。
- 持既有发布锁，短时隔离nginx到RPB请求，停止RPB后备份并比对116张原业务/配置表指纹；新程序就绪后数据指纹一致。资源先复制、index原子切换，进程稳定后恢复流量；自动回退未触发，临时规则已撤销。
- 4个域名、12个页面入口匹配新index，20000000域名224个公网资源逐字节匹配制品；匿名auth/me、商户凭证历史及原图接口均401。未使用生产会话查看私人原图或确认交易，真机视觉验证限制仍保留。
- 后端active，PID1436636，InvocationID `5b25b68536674301a5bd4e7708d2f25d`，NRestarts0；独立最终检查进程身份稳定，无新ERROR或Exception。nginx、PostgreSQL、Redis及Docker状态和进程身份保持。

生产回退：持同一发布锁、隔离RPB入口、停止RPB，恢复备份JAR、index和两份版本标记；启动并确认就绪后恢复流量。旧assets已保留，不恢复数据库覆盖发布后的真实数据。

证据：target/proof-visibility-source-proof.json、proof-visibility-clean-tests.log、proof-visibility-clean-build.log、proof-visibility-clean-package.log、proof-visibility-artifacts.json、proof-visibility-deploy.log、proof-visibility-public-proof.json、proof-visibility-final.log。
