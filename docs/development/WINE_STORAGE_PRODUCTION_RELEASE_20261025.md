# 酒水寄存生产发布记录

2026-10-25，Asia/Singapore。用户在本地开发验收后明确授权“提交部署”。

## 发布结果

- 项目 D:\RPB，分支 `codex/paynow-payment-product-line-staging`，已推送 origin。
- 功能提交与线上前后端标记：`93c0c9bea9d7e507063ce511eb83f13df74c8df5`。
- 服务器 `43.134.69.75`，服务 `rpb-backend`，数据库 `rpb_acceptance`。
- 制品目录 `/opt/rpb/releases/20261025-wine-storage-93c0c9be`；运行路径仍沿用 `/opt/rpb/app/reservation-platform.jar` 与 `/opt/rpb/frontend`。
- 切换前完整备份 `/opt/rpb/backups/20261025-wine-storage-93c0c9be-before`：旧 JAR、前端、版本标记及 1,172,433 字节数据库 dump，pg_restore 清单校验通过。
- Flyway `077|77|0`：最新 V077，77 条成功记录，0 失败。
- 最终 PID `1591943`，InvocationID `5d54ef3869d4481b8c73083b9e6b367b`，active，NRestarts=0，复核身份稳定，无新增 ERROR。

## 提交及构建范围

仅提交寄存模块、目录空价兼容、钱包入口/专用扫码适配、迁移、测试与说明。原有 8 个钱包组件、3 份验收文档及 rpb信息.txt 共 12 个文件，发布前后逐一 SHA256 相同，继续保留在工作区。

从功能提交的干净 git archive 副本执行 npm ci、npm run build、mvn package -DskipTests，均成功。只在构建副本中还原与生产规范化内容相同的 71 个历史资源原始换行；主工作区没有改写。新旧 JAR 的 76 份历史迁移、94 项依赖全部字节一致。

| 制品 | SHA256 |
|---|---|
| JAR | `a0dfb73e8c03f74b89c9efe64ddf592a26dc7eef6f698fad77364714cedffd48` |
| 前端归档 | `aa77d01be29dd70b1a96546fccb1e8217c136c6c10ef0b7db7683c3a492667fd` |
| index.html | `c259a49d0d9815201d89cbcd38af3d61fc8929600f32421bea37aa36443f02c2` |

沿用当前业务代码已经通过的组合回归 268 项、专项 89 项及寄存专项 31 项，不将重叠测试相加。本次重新运行前端针对性测试 24 项，全部通过。详细本地 HTTP、数据库、并发、时区及扫码证据见 WINE_STORAGE_VERIFICATION.md。

## 数据保护与生产验证

先恢复生产完整备份到本次创建的独立副本，执行 V077 并比较 133 组既有数据指纹，结果一致；仅删除本次拥有的演练副本。正式切换在保护 RPB 请求入口并停止后端后备份，迁移前后及读回验收后再次比较 133 组指纹一致。

比较包含既有目录、卡、钱包资金和积分表；排除会话、验证码、审计等易变表，以及明确新增的 wine_storage 表、平台应用行与权限；auth_accounts 排除登录时间、更新时间及版本。不将该范围描述成全库逐字节相同。

通过真实生产服务，为已有明确标注 QA 的独立租户创建短期会话，只读确认：

- 目录双语产品正常，原次数卡仍剩 28 次，原 QA 钱包仍为 3,800 分。
- 顾客存酒首页正常，newBusinessEnabled=false、物品为空；关闭新业务仍可查询历史。
- 商户首页正常，经理权限包含 wine_storage.deliver；顾客产品检索接口正常。

本次没有在生产制造寄存、领取或资金交易，没有修改真实门店产品资料、价格、商业授权及开关。所有测试会话撤销，最终复核 QA 活跃会话为 0；寄存物品为 0，未定价产品为 0。

4 个既有域名的 44 个公开 SPA 路径与发布 index 字节一致，覆盖钱包、目录、卡和存酒入口。10 个目录/卡/钱包/寄存静态资源响应与制品字节一致；4 域名共 8 个存酒匿名 API 均返回 401。后端就绪接口返回预期 401。nginx、PostgreSQL、Redis、Docker 的 PID、InvocationID 和状态保持一致，仅重启 RPB。临时定向保护规则已撤除。

## Production Readiness Report

### Checklist

已核实模块/API/数据库自审、当前测试证据、AppGate/RBAC/归属、审计与幂等、生产副本迁移演练、完整备份、制品哈希、原数据比较、公共入口和进程稳定性。无需新增生产环境变量。

预订、排队、到店、座位、清洁没有业务代码或依赖变更，既有数据指纹及基础服务身份保持一致；本轮未在真实门店重新制造这些业务交易。

### Blocking Issues

无已知发布阻塞；首次切换完成，未触发回退。

### Non-blocking Issues

真实 iPhone/微信/Android 摄像头、相册与 HEIC 上传、硬件权限及负载未验收。扫码页面和手工粘贴验证不能代替手机摄像头验收。生产业务写入完整链路沿用已通过的本地真实 HTTP/数据库证据，本轮生产只读验证，不宣称生产完整收存/交付实测。

真实门店按现有商业授权与门店开关启用后才能开展新业务；本次发布没有自动开通。

npm audit 报告既有锁文件的 7 个 high 级依赖条目（Vue/server-renderer、Vite、PostCSS、source-map-js、nanoid、brace-expansion），package.json/package-lock.json 与发布前一致。所列直接公告涉及 SSR、开发服务器或构建工具；本次线上交付为 nginx 静态 SPA 与 Java 后端，不运行 Node SSR/Vite 开发服务。本轮未升级依赖或做完整依赖可达性安全审计，保留 npm-audit.json 供后续维护，不能将构建成功解释为依赖无漏洞。

### Rollback Plan

保留旧制品、完整备份和 V077 全部资料。保护期间的脚本可恢复旧 JAR、index 和版本标记，但先检查没有新增寄存物品及 NULL 价格，回退不删表或还原数据库覆盖交易。当前发布点满足该数据条件。

正式开展业务后，先关闭新业务并保留新版本历史和有效物品受控交付，优先前向修复。旧目录代码不理解 NULL 价格，不能直接降级；不能删除寄存事实、证据或把 NULL 改成 0，也不能通过回滚给已过期物品重置期限。

### Decision

GO；已部署并完成上述范围验证。

## 证据

本地 `target/wine-storage-release-20261025/` 保存 preserved.json、intended.json、resource-bytes.json、artifacts.json、构建/测试日志、发布脚本、deploy.log、final-check.json/log；服务器 `/home/ubuntu/wine-storage-release-20261025/` 保存副本演练、前后数据指纹、进程与邻接服务证据。临时凭证和构建副本不提交。

本发布记录作为后续文档提交保存，生产业务制品仍对应上面的功能 SHA。
