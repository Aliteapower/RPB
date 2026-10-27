# 存酒新增顾客与独立设置入口前端发布记录

日期：2026-10-27（Asia/Singapore）。用户明确授权“提交部署”。

## 发布结果

- 功能提交 bff3ca788aca4048ff2eb8678e23b4578c06d6be 已提交、推送 origin/codex/paynow-payment-product-line-staging 并部署前端。
- 后端继续为 d6816db5adede3846ab53bed8900763307a9f52c，不重新打包或重启。Flyway 保持 084|84|0。
- 发布范围仅 D:\RPB 对应 ubuntu@43.134.69.75 上 /opt/rpb/frontend、/opt/rpb/app/FRONTEND_RELEASE_SHA。未操作其他项目。
- 不可变前端制品 /opt/rpb/releases/20261027-storage-customer-settings-bff3ca78。
- 租户管理员在找到顾客旁可新增统一顾客档案，保存后自动选中；入口仍受原权限和门店范围控制。首页设置改为页头入口，支持独立设置视图、返回及刷新。

## 提交范围与干净构建

仅提交本次 11 个文件，包括共享顾客创建组件/composable、存酒设置组合及前端回归和说明。没有后端、API、权限、数据库或依赖修改。

Git archive 逐项核对 2,823 个 Git blob；从干净源码执行 npm ci、158 项相关前端回归、vue-tsc/Vite 构建，全部通过。npm ci 报告 0 漏洞。未把 8 个既有 Wallet 组件、3 份验收文档和私有资料文件共 12 项无关内容带入提交或制品；最终 SHA256 与发布前一致。

| 对象 | SHA256 |
| --- | --- |
| 前端归档 | be22601cc08bf2e6fb1f9f82b1582f9be1cd74078764c7c22136bcd441a94c15 |
| 前端 index | c37981f42555f0778f93933d5a119b6123b5a44632aa2ec91559bc9aa67fe75a |
| 未更换后端 JAR | f222beeeea623ece81396d4e72fd2eee11aedcc8129f04c5284fd4e3930927a2 |

## 备份与切换

使用既有独占锁 /home/ubuntu/rpb-ledger-release.lock。切换前验证原前后端 d6816db5、JAR/index、V084 和应用就绪。

前端完整备份 /opt/rpb/backups/20261027-storage-customer-settings-bff3ca78-before，含原前端目录和前端版本标记；备份逐项与原目录一致。先添加新哈希资源，再原子切换 index；所有非 assets、非 index 文件先要求与原文件相同。失败时恢复旧 index 和前端标记，不改后端或数据库。本轮未执行回退。

本次无业务写入、迁移或环境变化，因此只做前端备份，没有创建或恢复数据库备份。验证创建的专用 QA 临时会话均在 finally 撤销。

## 验证

- 四个生产域名共 56 个页面入口返回新 index，含新增的 task=settings 深链接；33 项公开静态资源逐字节一致。
- 60 项匿名拒绝通过，其中 48 项 401、12 项原契约 403；4 项匿名空目录通过。新增顾客隐私告知入口的匿名读取也被拒绝。
- 15 项既有专用 QA 租户认证读取通过：统一 customer-profile-v2 告知、租户顾客档案、存酒顾客查询、存酒、保管员、待办、商品及钱包/卡项等；原钱包余额 3,800 分、计次卡剩余 28 次未变。
- 本轮没有通过生产接口创建顾客、登记存酒或执行资金操作。新增表单的保存自动选中、同意确认、重复点击、手机号冲突、跨门店晚响应及设置隔离行为由干净构建的 158 项前端测试覆盖，既有服务端 API 未变化。
- 267 个制品文件与正式目录逐一一致；6,575 个旧静态资源仍存在且字节未变。
- 切换前、发布后和最终复核 152 项业务/配置指纹一致。口径排除专用 QA 租户、会话、限流、审计等易变数据，并完整比较平台/权限配置；不表示整库字节一致。
- rpb-backend、Nginx、PostgreSQL、Redis、Docker 的状态、PID、InvocationID、重启次数全部与发布前相同。后端仍 PID 2115321、InvocationID 5f4812629c754319b57a73837ef55897、NRestarts 0。
- 后端 JAR 哈希、标记、环境文件和 V084 未变，readiness 维持预期 401，观察及最终复核无新增 ERROR。

## Production Readiness 与回退

GO，前端发布和最终复核完成。发布记录的后续文档提交不改变线上前端功能 SHA。

回退时恢复本次备份的 index 和 FRONTEND_RELEASE_SHA，保留新旧哈希资源，不重启后端、不修改顾客或存酒记录。新增顾客依然是原统一档案，没有独立存酒顾客数据需要迁回。

真实手机、微信/iPhone/Android 人工操作未验收；此前中文 393px、约 322px 英文的模拟浏览器布局与生产读取检查分别记录，不等同真实生产顾客创建验收。

本地证据 target/storage-customer-settings-release-20261027/；服务器证据 /home/ubuntu/storage-customer-settings-release-20261027/。前端备份仅在服务器保护目录，不进入 Git。
