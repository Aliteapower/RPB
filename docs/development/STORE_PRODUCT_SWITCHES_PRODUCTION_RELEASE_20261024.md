# 门店产品线开关生产发布

2026-10-24，新加坡时区；工作区 D:\RPB。用户明确授权“提交部署”。

## 发布范围

功能提交 `137096e1a36769398bd8a46507cf09822576faf6`，已推送 origin/codex/paynow-payment-product-line-staging，共25个相关文件。租户管理员在“产品线管理”按门店关闭、重新开启钱包积分和每日营业记录；运行访问仍由AppGate控制，重新开启不能绕过商业授权。订阅同步保留明确关闭状态，其他门店、订阅及历史数据不改变。

原有8个钱包组件换行改动、其他未跟踪复验文档和私密文件保留，未纳入提交或制品。此发布记录后续单独提交，不改变运行功能SHA。

## Production Readiness Report

### Checklist

- 本地51项后端定向测试通过；扩展钱包真实JPA／按店购买夹具后，18项受影响测试复验通过，未变更的33项AppGate结果复用。
- 原生PostgreSQL严格使用D:\RPB指针数据库61644/postgres，随机隔离schema覆盖开关持久化、重复请求只产生一次审计、商业资格、门店及租户隔离、关闭后的订阅同步和续费保护。
- 干净Git归档2478文件核对Git blob一致（允许Windows CRLF归一化），src源码与已测试工作区一致；干净目录重新npm ci、15项前端测试、vue-tsc、Vite构建、44项不依赖数据库的后端测试及Maven package全部成功。
- 新旧JAR逐项比对，仅12个预期类条目改变／新增；迁移、配置、其他类和依赖条目不变。
- 没有新增迁移、环境变量、权限或依赖；复用管理员角色、tenant.admin.manage、门店访问范围及既有AppGate审计；管理入口不依赖被关闭产品。
- 目标、旧版本、哈希、发布锁、备份可读性、停止边界数据指纹、自动回退、就绪和日志均验证。

### Blocking Issues

无。

### Non-blocking Issues

浏览器通道虽恢复连接，本地Chrome导航／截图仍超时，未获得真实手机／桌面中英文布局截图；组件渲染和交互测试通过，但不代替视觉验收。没有使用真实生产会话操作开关。npm ci报告既有7项high依赖告警，本次依赖未变，未自动升级。

### Decision

GO。已完成发布和公网只读／匿名验证；视觉检查限制如实保留。

## 制品与发布

- 目标：ubuntu@43.134.69.75，/opt/rpb，rpb-backend:8080。
- 发布前后端版本2a698874，前端版本e81919d1；发布后两者均为完整功能SHA `137096e1a36769398bd8a46507cf09822576faf6`。
- JAR SHA256：`f002960bc5914524438d49896886a2f49967584bfb19445217520862ebd4fa95`。
- 前端归档SHA256：`70989de39f6de3dbc37582281ee367095b97c5eea904b84be0fa53594686f2b8`。
- index SHA256：`b052593211ee8408dd14d98f0b007aaddebee3caea62ad121722c10e002df306`。
- 发布目录：`/opt/rpb/releases/20261024-store-products-137096e1`。
- 备份目录：`/opt/rpb/backups/20261024-store-products-137096e1-before`，0700权限，包含旧JAR、完整前端、版本标记、971174字节数据库dump和前后指纹；pg_restore目录检查通过。

持/home/ubuntu/rpb-ledger-release.lock，短时阻断nginx到目标后端流量，停止应用后备份，原子替换JAR并启动。检查就绪、原业务指纹、Flyway、日志及稳定进程后复制前端资源并原子替换index、更新版本，再恢复流量。旧assets保留；nginx、PostgreSQL、Redis、Docker不重启。未触发回退。

## 线上结果

- booking、platform.booking、20000000.booking、sx.booking.yumstone.sg共4个域名、16个页面入口和892项静态资源逐字节匹配干净制品；包括新的admin/product-lines路由。
- 12项匿名API检查符合预期：管理列表GET、开关PUT及auth/me均401，管理API返回UNAUTHENTICATED。没有有效开关写入、购买、资金、积分或营业记录操作。
- 116张原业务／配置表在停止边界发布前后指纹一致，包含门店开关、订阅及账务数据。按既有口径排除会话、限流、审计等自然变化表。
- Flyway保持070/70/0，无迁移。后端active，PID1235333，InvocationID400992ad55714033977735a94e811e68，NRestarts0；独立核验进程身份不变，启动日志无ERROR。
- nginx、PostgreSQL、Redis、Docker状态及进程身份保持；临时RPB流量规则已撤销。用于本地页面检查的fixture服务已停止。

## Rollback Plan

持同一发布锁并阻断目标后端流量，停止rpb-backend，从备份恢复旧JAR、前端index和两个版本标记，启动并确认就绪后恢复入口；资源先恢复再原子替换index，不恢复数据库覆盖业务数据。脚本包含失败时同等回退保护。

门店开关数据保留；旧版订阅同步可能重新开启已关闭门店，回退旧程序后如需续费应注意该兼容限制。旧标签页需刷新读取新菜单与资源。

证据：target/store-products-preflight.log、store-products-artifacts.json、store-products-clean-npm.log、store-products-clean-tests.log、store-products-clean-build.log、store-products-clean-package.log、store-products-deploy.log、store-products-live-proof.json、store-products-final.log。
