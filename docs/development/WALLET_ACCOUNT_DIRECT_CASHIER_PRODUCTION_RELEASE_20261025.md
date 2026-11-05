# 账户弹窗与直接收银台合并生产发布

2026-10-25，新加坡时区；D:\RPB。用户明确授权两轮一起提交部署。

## 发布与范围

功能提交 `608dfa5458d8e8333153098dc5e9c5c7b59456f9` 已推送 `origin/codex/paynow-payment-product-line-staging`，前后端版本均为该提交。仅28个相关文件纳入功能提交，原有8个组件换行状态、未跟踪验收材料及私密文件保留，未进入制品。发布证据文档后续单独提交。

- 商户收银台账户弹窗支持当前账号改密/退出，两端弹窗带出真实会话身份，商户与顾客认证接口隔离。
- 默认收银台直接进入消费页面，充值/积分直接进入原识别办理流程，下一位保留类型并清空上一位信息。
- 无迁移、依赖、环境变量或AppGate权限变更；原收款、报价、扣款授权和核实规则保留。

## Production Readiness / GO

- 干净Git归档2500文件核对Git blob通过，2001个源码文件与验证工作区一致；独立安装锁定依赖，183项前端回归、类型检查、Vite生产构建和Maven package通过。
- 15项后端认证/顾客密码/真实PostgreSQL测试结果在源码一致性确认后复用，包含旧密码、账号及租户隔离、当前会话保留/其他会话撤销、失败限制、并发、事务回滚和来源保护；未无依据重跑相同状态。测试使用本地指针61644端口及随机隔离schema。
- 新旧JAR逐项比较：新增5个预期改密类条目，仅AuthApiErrorCode.class变化，其他类、资源、迁移和依赖逐字节一致。
- GO：明确目标ubuntu@43.134.69.75、/opt/rpb、rpb-backend:8080、rpb_acceptance；原版本、备份、发布锁、自动回退、业务数据及相邻服务边界均核对。
- 未使用生产登录会话修改真实密码、资金或积分。浏览器/真机视觉及相机验收未执行，组件与相机生命周期测试不等同于真机证明。

## 备份与切换

- 发布前后端05bf684e、前端daa57436；原JAR及index哈希与此前发布记录一致。
- 发布目录 `/opt/rpb/releases/20261025-wallet-combined-608dfa54`；备份 `/opt/rpb/backups/20261025-wallet-combined-608dfa54-before`，包含旧JAR、完整前端、两份版本标记及1084338字节数据库dump，pg_restore目录验证可读。
- 持 `/home/ubuntu/rpb-ledger-release.lock`，暂时隔离nginx到RPB请求，停止应用后备份。原子切换JAR，应用就绪且116张原业务/配置表指纹一致后复制资源、原子切换index及版本标记，确认稳定后恢复入口。旧assets保留，没有触发回退。
- JAR SHA256 `272d340596e0f8b37bbbef7f4ce27d31f1b729af356c7c3c0d825685c075b6a1`。
- 前端归档SHA256 `7e2bdf15dabbe85c704a84f07f47e415419078b00e6fb929f4b0b614fea5bb50`。
- index SHA256 `003d013e407c8342cd3f09b35ae2c2b458402681637a3baf572d5bb3a370fe12`。

## 线上验证

- 4个域名、20个页面入口匹配新index；20000000域名全部226个公网资源逐字节匹配制品。
- 6项保护检查通过：员工me及顾客me匿名401；新改密接口缺标记403、无会话401、跨站403、账号覆盖400。均未携带真实账号密码或有效会话，不改变认证或业务数据。
- 后端active，PID1453356、InvocationID `f70cbac4092643c0bc3b8d00ec1e907f`、NRestarts0，独立复验身份稳定，无新ERROR或Exception。Flyway保持070/70/0。
- nginx、PostgreSQL、Redis、Docker状态及进程身份保持，临时RPB流量隔离规则已撤销。

## 回退

持同一发布锁，隔离RPB入口并停止RPB，从上述备份恢复旧JAR、index及版本标记，启动并确认就绪后恢复入口。旧assets仍在，不恢复数据库覆盖后续真实交易或用户已修改的密码。

证据：target/wallet-combined-source-proof.json、wallet-combined-clean-tests.log、wallet-combined-clean-build.log、wallet-combined-clean-package.log、wallet-combined-artifacts.json、wallet-combined-deploy.log、wallet-combined-public-proof.json、wallet-combined-final.log。
