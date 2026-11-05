# 员工产品线与模块授权生产发布

2026-10-28（Asia/Singapore），用户明确授权“提交部署”。

## Production Readiness Report

### Checklist / Decision

GO，提交、推送、发布和最终验证完成。功能提交 `af97cd60fb93e4aa20f7426ec3bdda517268aaab` 已推送至 Aliteapower/RPB 的 codex/paynow-payment-product-line-staging。只提交43个本轮文件；原14个无关文件发布前后SHA256一致。

后端103项定向测试通过（含8项真实PostgreSQL测试）；干净Git archive逐一核对2,939个blob，重新执行npm ci、31项前端测试、vue-tsc/Vite及Maven package通过。后端测试复用同一源码和环境下已验证结果，制品从干净提交构建，未夹带工作区原有钱包改动。

新旧JAR仅31个条目有差异（含目录条目），全部属于本轮授权类/嵌套类和V087；依赖与运行配置保持字节一致。

### Target / Artifacts

- 项目 D:/RPB；主机 ubuntu@43.134.69.75，服务 rpb-backend，数据库 rpb_acceptance，后端127.0.0.1:8080。
- 前后端功能SHA均为 `af97cd60fb93e4aa20f7426ec3bdda517268aaab`。
- 不可变制品 `/opt/rpb/releases/20261028-staff-permissions-af97cd60`。
- 实际JAR `/opt/rpb/app/reservation-platform.jar`，前端 `/opt/rpb/frontend`。
- JAR SHA256 `da505c75f5b36357b28fc32791293e2542c49dc3297aadf831e53e8a4927379c`。
- 前端归档 SHA256 `5b6d32b7e3f02a1f3c2fdae072975e16ec858a666d4fbe43883392d1921fcfd6`。
- index SHA256 `e784e5e20c1121dfbe3943b1516b38187d93677baf370e847990cfec29b694b5`。

### Backup / Migration

备份 `/opt/rpb/backups/20261028-staff-permissions-af97cd60-before`，包含完整旧前端、旧JAR、前后端版本标记、环境及停服切换时数据库dump，pg_restore目录校验通过。备份目录权限0700，未传输数据库或环境秘密到本地。

独占现有发布锁；先对真实生产schema事务执行V087再ROLLBACK验证约束兼容，随后由新后端Flyway完成正式迁移，最终 `087|87|0`。新增权限表3个外键均存在。原账号权限未回填，没有自动扩大任何真实员工权限。发布后非QA租户新权限配置行数为0。

旧后端 `f3f35ca9`、旧前端 `a40e5e5f` 均已保护备份；旧前端8,607个哈希资源通过inode/大小核对保持，279个新制品文件逐字节与正式前端一致。无旧资产或备份清理。

### Production Acceptance

- 四个生产域名的32个页面入口返回新版index；8个相关公开静态资源逐字节一致；新权限API的8项匿名GET/PUT均401。
- 既有独立QA租户内创建临时普通员工，通过真实管理员API配置卡券查看权限，服务端自动补齐wallet.view；同一员工会话可以读取卡券设置/钱包上下文，没有存酒权限时被拒绝。
- 明确撤销全部所列权限后，同一会话卡券/钱包读取立即403，产品及历史目录为空；auth/me的账号基线保持，门店覆盖反映真实撤权。
- 27项带身份/拒绝检查通过：包括普通员工禁止管理权限、跨店拒绝、未知管理员权限拒绝、请求头保护、管理员自身不可编辑、陈旧版本409、相同请求重放不重复写审计。仅生成2条QA授权审计；未执行任何金融交易。
- QA员工已禁用，相关员工会话与管理员测试会话已撤销；保留QA空权限集合version2及审计证据。没有修改真实员工权限、密码、业务记录或产品配置。
- 153项业务/配置指纹在切换前、发布后及最终复核保持一致。口径排除专用QA租户及相关角色/权限、会话、限流和审计、新权限表及Flyway；不是全库字节一致声明。
- 最终 rpb-backend active，PID2474578，InvocationID `559c961f582d419ea1251ec3f9dffb7a`；观察期及最终复核进程身份稳定，无新ERROR。Nginx/PostgreSQL/Redis/Docker身份和环境文件保持一致。

### Blocking / Non-blocking Issues

无阻塞项。首次最终校验因保护备份目录0700而以普通用户读取失败，随后仅对只读校验脚本使用sudo，校验通过；没有修改目录权限或触发发布回退。

未做真实手机/微信浏览器交互验收；API和静态入口检查不代表实机验收。管理员可在“租户管理 → 员工 → 编辑 → 产品线与模块授权”配置权限，员工原有授权不会自动扩大。

### Rollback Plan

禁止恢复旧数据库覆盖新业务及审计。保留V087表。直接回滚旧后端会忽略门店覆盖，因此发布脚本回退前先停服务并检查非QA显式配置：仅在真实配置为0时允许恢复旧后端；已有真实配置时保留新安全后端，避免恢复被撤销权限，另行修复前端或受影响功能。本次发布未触发回退。日后回退须先隔离受影响员工或保留兼容的新鉴权逻辑。

证据：本地 `target/staff-permissions-release-20261028/`；服务器 `/home/ubuntu/staff-permissions-release-20261028/`，包括final-check.json、public-check.json、live-check.json、业务指纹、迁移演练和服务日志。后续发布记录提交不改变线上功能SHA。
