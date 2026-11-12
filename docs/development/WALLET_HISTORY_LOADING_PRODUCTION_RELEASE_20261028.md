# 卡业务记录顾客信息及钱包首页加载修复生产发布

2026-10-28，新加坡时间。D:\RPB，分支 `codex/paynow-payment-product-line-staging`。前后端功能版本均为 `0b02c1707913633ade0684f961b82c6514ad98ed`，已推送、部署并完成线上验证。只提交本次 14 个指定文件，原有 12 个未提交文件 SHA256 保持一致。

## 上线结果

- 员工卡业务记录显示真实顾客姓名及电话；只有一项时显示现有资料，资料缺失时明确提示，不以顾客 ID 兜底。批量补全只查当前租户、本页有效顾客，原日额度结算、权限、分页和冲正逻辑保持。
- 钱包首页按稳定的路径/query 标识进行自动跳转，资料和权限刷新不再反复取消同一目标的慢跳转。拒绝、异常或等待超过十五秒显示重试入口，手动切换不被旧跳转拉回。
- 没有新增迁移、权限、环境变量或配置项；既有购卡信息显示、已使用卡不可退款、员工权限功能保留。

## 构建与生产准备

Production readiness：GO，已部署。干净 git archive 的 2,950 个源文件逐一校验 Git blob。253 项前端测试、19 项后端定向测试、Vue 类型检查、Vite 构建、Maven package 均通过；PostgreSQL 使用原工作区 target/local-postgres-current.txt 指定的 61644 端口和隔离测试 schema。

新旧 JAR 仅 4 个相关 class 条目变化：新查询及其 DTO、员工控制器及其嵌套 DTO；原嵌套 DTO 的 javap 指令相同，只有调试信息变化。依赖、配置及迁移字节一致。发布与自动清理共用锁，预检核对旧版本及制品摘要，失败会恢复旧程序和前端入口，不覆盖业务数据库。

## 线上验证

- 独立 QA 租户接口验证 22 条卡业务记录，22 条姓名/电话与同租户有效顾客一致，原次数前后值、类型和来源保持。顾客过滤、游标空页、顾客端响应字段不变、匿名/顾客身份及跨店拒绝均通过。
- 保留既有购卡校验：3 笔订单顾客信息正确，2 张有实际使用记录的卡不可退。没有提交退款、收款、充值或核销命令；临时验证会话已撤销。
- 四个域名 32 个页面入口、9 个相关资源校验通过，4 项匿名订单读取返回 401。279 个新前端文件与线上逐一一致，8,856 个旧资源 inode 保留。
- 153 项业务表/配置指纹前后一致，沿用既有 QA 租户、会话和审计排除口径，不宣称全库字节一致。Flyway 保持 `087|87|0`。
- rpb-backend active，PID `2571923`，InvocationID `2b9368f382544a4fa8390f396e50cf96`，NRestarts=0；发布及浏览器验收后身份稳定，未发现新增 ERROR。nginx、PostgreSQL、Redis、Docker 身份及环境配置不变。

Chrome 验收使用用户明确授权的已登录标签，域名 `20000000.booking.yumstone.sg`。首次刷新发现会话失效，用户自行重新登录后继续：

- 登录重定向正常进入 `/wallet/collect`，显示金额键盘及消费收款内容，无持续“加载中”。
- 卡业务记录页 6 条可见记录显示顾客姓名、电话；顾客标签不显示 ID，原交易来源/原单编号保留。
- 从记录返回收银台、从手机号充值入口返回收银台均正常进入 collect；刷新后仍可用。
- `/wallet/topup?entry=phone` 正常显示手机号查询表单。未进行实际顾客查询、摄像头或扫码，不冒充相机验收；顶部扫码充值/积分流程的导航竞争由自动化回归覆盖。
- 收款金额始终为零，未执行业务提交。最终用户标签保留在正常收银台。

## 备份与回退

服务器 `43.134.69.75`，服务 `rpb-backend`，数据库 `rpb_acceptance`；应用 `/opt/rpb/app/reservation-platform.jar`，前端 `/opt/rpb/frontend`。

制品：`/opt/rpb/releases/20261028-wallet-history-loading-0b02c170`。完整回退：`/opt/rpb/backups/20261028-wallet-history-loading-0b02c170-before`，含旧 JAR、完整前端、版本标记、环境配置及 database-at-switch.dump，pg_restore 目录校验通过（1,284 行）。

若需回退，恢复备份 JAR、前端 index 及版本标记，仅重启 rpb-backend。旧版本为 `b3542f051d8279c4671710a40d47116ba25f5a73`。保留 V087 和全部已发生业务事实，不恢复旧数据库覆盖新交易，不删除已发布资源和审计。

## 摘要与证据

- JAR SHA256：`cc79fd7fc7e41275d9749004568794ae3276c90c161fe3c828c4bfdb90da25ed`
- 前端归档 SHA256：`fab4e73174eed99b8ada2cc615b3ecfb7b38bf6c5f2b6a98ecc6d63037b0ab8d`
- index SHA256：`63b973c51231f26ad188087119a301d0fa6cc46824d23b405093165265708be6`

本地证据 `target/wallet-history-loading-release-20261028/`，包括 source-proof、jar-differences、测试/构建日志、deploy.log、live-check.json、final-check.json 和不含顾客个人资料的 browser-check.json。服务器同名证据目录位于 `/home/ubuntu/`，包含数据库目录验证、业务/服务前后快照及 final-service.log。
