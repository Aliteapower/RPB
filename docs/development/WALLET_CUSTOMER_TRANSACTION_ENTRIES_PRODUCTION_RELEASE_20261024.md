# 顾客钱包交易入口分离：生产发布记录

2026-10-24，新加坡时区。用户明确授权“提交部署”。项目 D:\RPB；分支 codex/paynow-payment-product-line-staging；远端 Aliteapower/RPB。

## 发布结果

功能提交 `8605889c8bb85cbab0ef487d1fbbee17928447c9` 已推送，线上前后端版本标记均为该完整SHA。本记录后续单独提交，不改变运行代码版本。

- 个人钱包卡右上交易入口进入 money，积分卡入口进入 points，下方及底部交易入口进入 all。
- 顾客交易页可切换全部／钱包／积分；金额分类在后端统一过滤后再分页、计数及汇总。显式all不再转换成auto。
- 无数据库迁移、依赖、环境变量、权限、账本或记账流程变更；商户原页签不变。
- 工作区原有8个组件换行状态、3份未跟踪验收材料和私密文件保留，未纳入提交或制品；未读取私密文件。

## Production Readiness / GO

- 只提交本轮13个文件，未使用全量暂存。
- 从Git归档生成干净发布源码，2415个文件逐个核对Git blob，1945个源码／配置文件与验证工作区内容一致。归档受本机换行设置影响的2414个文件仅在独立构建目录恢复为已核实的Git原字节；主工作区未改写。
- 干净构建中前端110项、后端27项全部通过，Vue类型检查、Vite构建和Maven package成功。后端测试读取所属本地运行环境61644端口，使用测试独立schema。
- 原现金充值测试在Vite初始化阶段超时，单独重试仍未完成；不属于本轮变更，没有声称全部前端测试通过。新增入口、查询、分页、权限、安全与重试回归均有当前通过证据。
- npm ci仍提示锁文件已有7项high漏洞，本轮未调整依赖。
- GO：确认目标为ubuntu@43.134.69.75、/opt/rpb、rpb-backend、rpb_acceptance；无影响本轮功能、身份隔离、数据兼容或回退的未解决阻塞。

## 备份、切换与数据核验

发布前确认前后端均为78bea244、旧jar及index哈希与上次发布相同，Flyway为068|68|0。全程持既有/home/ubuntu/rpb-ledger-release.lock。

- 发布目录：`/opt/rpb/releases/20261024-wallet-entry-8605889c`。
- 回退备份：`/opt/rpb/backups/20261024-wallet-entry-8605889c-before`，含旧jar、完整前端及版本标记。
- 临时仅阻断nginx到RPB 8080的请求，停止RPB后备份数据库；dump为953373字节，pg_restore目录验证可读。
- 新jar原子安装，服务就绪、数据与进程核验后发布前端入口并解除临时阻断；旧assets保留。无其他项目服务重启。
- 无新增迁移，最终仍068|68|0。停机边界／切换后／公网验证后的115张业务及配置表数量和指纹一致，包含钱包科目标签及名称快照；会话、审计、限流及动态码等运行态表按既有规则排除。
- jar SHA256：`99539a064c3fa4a76a4c6c9df308576aabde44e8f899c52440eb71d4b3826466`。
- 前端包 SHA256：`c3e856733115ac53601aa09dce10db552b2149ff66e052109788498aa41300ff`。
- index SHA256：`2b61c09a6f886c7ecbf2c9c265d39287709bd7b4eb1929fcec63879841115442`。

## 线上验收

- booking、platform、20000000、sx四个域名入口及12条相关页面返回本次index；212个静态资源与干净构建逐字节相同。
- 六个原有公开／匿名API的状态和响应内容与发布前相同。
- 员工及顾客三种分类查询未登录均401；既有客户、充值和积分写入口的匿名、缺标记及跨站拒绝检查保持正常，共15项保护检查通过。
- rpb-backend active，PID1121653，InvocationID `1132a017ce284e66894608c428964905`，NRestarts0；切换观察和独立最终验证均稳定，新进程日志无ERROR。
- nginx、PostgreSQL、Redis、Docker的状态、PID、InvocationID和重启计数与发布前相同；临时阻断规则已移除。
- 未使用生产账号查询私人交易或执行真实资金／积分操作；公网资源和保护检查不等同于真机或已登录生产视觉验收。

## 回退

未触发回退。必要时持同一发布锁，隔离RPB入口，停止rpb-backend，从上述备份恢复旧jar、前端及两份版本标记，恢复rpb属主并启动，确认就绪和进程稳定后解除隔离。发布脚本包含失败自动恢复旧应用的路径；无法就绪时保留隔离并报错。

本轮无迁移，无须恢复数据库；不得用旧dump覆盖发布后的真实业务数据。仅回退本次功能，不回滚工作区其他修改。

## 证据

target/wallet-entry-release-source-proof.json、wallet-entry-clean-package.log、wallet-entry-clean-tests.log、wallet-entry-clean-build.log、wallet-entry-release-artifacts.json、wallet-entry-release-preflight.log、wallet-entry-production-deploy.log、wallet-entry-assets-proof.json、wallet-entry-api-proof.json、wallet-entry-production-final.log。
