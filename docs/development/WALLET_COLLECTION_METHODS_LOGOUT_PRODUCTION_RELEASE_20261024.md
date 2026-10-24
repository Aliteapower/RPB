# 收款科目与顾客退出登录：生产发布记录

2026-10-24，新加坡时区。用户明确授权“提交部署”。项目 D:\RPB，分支 `codex/paynow-payment-product-line-staging`，远端 Aliteapower/RPB。

## 发布范围与版本

功能提交及线上前后端版本：`78bea2441088aebf8815c1fb2b16fac0c677bc0d`。

- 商户「更多 → 钱包设置 → 收款科目」支持管理员新增、改名、排序和移除，顾客与商户充值同步使用配置，历史名称保留快照。
- 个人钱包「修改登录密码」右侧增加「退出登录」，复用顾客会话撤销，不影响员工会话。
- 保留原有无内容差异的组件换行状态、三份未跟踪复验文档和私密文件；没有读取、提交或打包私密文件。

## 发布前验证 / GO

- 从 Git 提交生成独立干净源码目录 `target/wallet-methods-release-build-78bea244`；逐项核对 2413 个 Git 文件内容，372 个前端源码／测试文件与已通过测试的验证目录一致。
- 前端相关测试 168/168 通过，包含退出成功、失败重试、重复请求及延迟响应；发布目录执行 `npm run build`，类型检查及 Vite 构建通过。
- 发布目录 Maven package 通过：10 个相关测试类，99 项测试，失败／错误／跳过均为 0，含 WalletPostgresTest 全部 62 项。
- 本地后端测试使用所属运行环境指针的 61644 端口，各自隔离 schema；未改用其他 PostgreSQL。
- 生产独立副本 `rpb_wallet_methods_rehearsal_20261024` 演练 V068；115 张业务表原字段的数量与指纹相同，新字段仅默认空对象／空值。首次执行遇到 postgres 无权读取 ubuntu 文件，改用标准输入继续，未重复迁移或重建副本。
- 未新增权限码、环境变量或依赖。npm ci 报告锁文件已有 7 项 high 漏洞提示，本轮没有修改依赖或执行自动升级。

## 生产切换与保护

发布前确认旧版本 `2a746808`、前后端哈希、Flyway `067|67|0`，发布锁可用；执行全程独占既有发布锁。

- 仅阻断 nginx 到 RPB 8080 的转发后停止 RPB 服务；在停止边界备份数据库、原 jar、前端和版本标记。
- 备份：`/opt/rpb/backups/20261024-wallet-methods-78bea244-before`，数据库 dump 951459 字节，pg_restore 目录验证通过。
- 发布目录：`/opt/rpb/releases/20261024-wallet-methods-78bea244`。
- 原子切换 jar；启动成功且迁移、数据指纹及进程稳定性检查通过后，发布前端入口并开放流量。
- 自动回退仅适用于尚无自定义渠道业务数据的封闭切换阶段；检测到自定义渠道数据时保留阻断，不能恢复不兼容旧程序。

## 线上验收

- 前后端 RELEASE_SHA 均为完整功能提交 SHA，jar SHA256 `e185b9b28e4f5e8e73b691069ed37bfe8530418d07a24eefc1a35a92a4c6062f`，index SHA256 `b4e4dd2efc475eef6377fa909c6023d89b2799779b786697945e0ed1ccc8d2ca`。
- Flyway `068|68|0`；115 张业务表原字段发布前／后／最终指纹一致，不比较 V068 新增的默认元数据字段；会话及审计等运行态表按既有规则排除。
- rpb-backend active，PID `1113874`，InvocationID `bc7a7ac4a5074399ba80b9c3c9fe09f5`，NRestarts 0，观察及最终复验均稳定，无新增 ERROR。
- nginx、PostgreSQL、Redis、Docker 的状态、PID、InvocationID 和重启计数与发布前一致；临时流量阻断规则已移除。
- 四个域名的入口、九条相关页面及 212 个线上静态文件与发布产物逐字节相同。
- 六个既有公开／匿名 API 响应与发布前一致；原充值／积分写接口的未登录、标记及跨站拒绝行为正常。
- 新增科目接口匿名 GET/PUT 为 401，缺少钱包标记及跨站 PUT 为 403；顾客钱包未登录为 401。
- 退出接口无登录时保持既有幂等成功语义 `{success:true,principal:null}`。初次线上脚本错误假设它也适用钱包路径的跨站过滤，已根据未修改的顾客认证控制器纠正该断言；无登录调用没有撤销真实用户会话，也未写生产业务数据。该检查不等同于已登录跨站验证。

证据：`target/wallet-methods-release-artifacts.json`、`wallet-methods-release-package.log`、`wallet-methods-rehearsal-resume.log`、`wallet-methods-production-deploy.log`、`wallet-methods-production-final.log`、`wallet-methods-assets-proof.json`、`wallet-methods-api-proof.json`、`wallet-methods-new-api-proof.json`。退出实际移动尺寸浏览器及会话撤销证据沿用本地隔离验收；未用生产账号办理交易或退出真实会话。

## 兼容与剩余风险

V068 扩展钱包科目标识、名称快照和唯一约束，原账本不重写。前后端已配套发布，旧页面需刷新以获取新选项。后续一旦有自定义渠道交易，保留支持该格式的程序，采用兼容修复，不删除新字段、标签及历史记录，也不恢复旧数据库覆盖新业务。无需新增短信或支付供应商配置。

未声称全量项目测试或真实手机相机验收通过。本轮线上验证以发布一致性、接口保护、数据完整性和运行稳定性为主；管理员修改科目、真实顾客退出以及交易行为已在本地隔离环境验证。
