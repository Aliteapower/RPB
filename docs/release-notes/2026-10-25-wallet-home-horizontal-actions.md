# 钱包首页统一横排入口

2026-10-25；D:\RPB；用户授权验证后提交部署。

移除钱包首页按是否存在资金账户添加wallet-identity-only的条件，以及对应的强制单列CSS。出示个人钱包码、扫一扫付款统一使用既有两列布局。余额、账户开通、扫码授权、产品可用性、租户/门店资料及路由不变。

验证：先复现无账户状态额外添加单列样式；修复后116项组件、工作区和导航回归通过。新增测试同时覆盖有/无账户下入口一致、跳转地址一致、仅GET读取且不创建账户。

无API、数据库迁移、权限、依赖或运行配置变更。仅前端发布，后端无需重启。回退时恢复备份index和前端版本标记，旧assets保留，不恢复数据库。

代码和UI审查：改动限于首页条件与过期样式，复用原两列规则；产品停用时原入口隐藏规则保留。真机视觉验收未执行。

## 生产发布

- 功能提交 `daa574360763fc8bd40d66fa979690122289e7bd` 已推送并发布前端；仅4个相关文件进入提交，原有8个组件换行状态和未跟踪材料保留。
- 干净Git归档2485文件核对通过，1989个源码文件与验证工作区一致；独立安装依赖后116项回归、类型检查及生产构建再次通过。
- 发布目录 `/opt/rpb/releases/20261025-wallet-horizontal-daa57436`；备份 `/opt/rpb/backups/20261025-wallet-horizontal-daa57436-before`，含原前端、版本标记及可读的1083618字节数据库dump。持既有发布锁，先复制资源再原子替换index，旧assets保留，未触发回退。
- 归档SHA256 `a41c445f364b072290cf93fec1b70070195fe7c33d610510f26d49f82e6da353`；index SHA256 `4af699b771b89b8be89d14440c24badab1ddd7e6397c5eb3fbf38cac3200c2b3`。4个域名12个页面入口匹配新index；224个公网资源逐字节匹配制品，均无wallet-identity-only遗留样式。
- 后端版本保持 `05bf684e72089a145924edf7b7207fbd607bdca5`，JAR哈希保持 `26cfda83d3209d05e7efe762ba8b5ae4bba2dc72f370b6ccf517d81e66ac53ec`，active、PID1436636、InvocationID `5b25b68536674301a5bd4e7708d2f25d`、NRestarts0均保持。nginx、PostgreSQL、Redis、Docker身份均不变，无新增ERROR或Exception，Flyway保持070/70/0。
- 证据：target/wallet-horizontal-source-proof.json、wallet-horizontal-clean-tests.log、wallet-horizontal-clean-build.log、wallet-horizontal-artifacts.json、wallet-horizontal-deploy.log、wallet-horizontal-public-proof.json、wallet-horizontal-final.log。
