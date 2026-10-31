# Release Notes

## Version / Date
2026-10-25 staff-storage-registration；已按用户授权提交、推送并完成前后端生产部署，版本及验证见文末。

## New
商户在“帮顾客存酒”选定顾客后，可直接登记，无需顾客预先申请。支持选择目录酒品或登记新酒品、瓶数、整瓶/开瓶、来源、当前保存规则、收存照片、位置、标签和备注。商户必须确认已说明保存规则、获顾客同意并收到实物。

## Changed
新增 POST /api/v1/stores/{storeId}/wine-storage/registrations，契约见 docs/api/2026-10-25-storage-staff-registration.md。独立 StorageRegistrationService 编排登记，复用原 StorageApplicationService.receive 校验与入库；同事务失败回滚。原顾客申请及商户核实申请收存仍可使用。

## Fixed
代登记不冒用顾客身份，不伪造顾客申请照片或顾客自助同意。保存 staff.registered 审计、当前操作人、商户确认和真实收存照片；原申请 rulesAccepted=false，收存后直接 stored。

## Migration
无迁移。复用原租户/门店范围表、复合外键、快照、照片唯一关联和幂等命令。

## Permission
沿用 wine_storage.receive、新业务 App Gate 和有效顾客校验。无新增权限授权。新接口不在 public 路径，顾客身份不能代办。

## Risk
前后端需配套发布。没有修改取酒授权、实际交付、有效期计算、钱包金额/积分或预约/排队/Walk-in/座位/清洁流程。仅本地数据库和模拟页面验证，未触及生产记录；真实手机拍照上传未验收。

## Rollback Notes
本轮已发布。需要时可回滚本轮前后端代码，已完成代登记作为原有 stored 申请和收存物品继续查询/取酒，审计事件保留。无需数据删除或数据库回滚，不整体恢复工作区。

## Review / Validation
- 接口先行定义，测试先添加并确认因 Registration/StorageRegistrationService 未实现而失败，再补实现。
- 已核查事务传播 REQUIRED、外层幂等回放、照片原子关联、操作者与顾客边界；不构造 CustomerAuthPrincipal。
- 后台真实 PostgreSQL 测试使用 target/local-postgres-current.txt 指定 61644 端口，在自动生成的隔离 catalog_cards_* 测试 schema 内执行并清理，不修改业务 schema。
- 最终验证结果以 target/storage-registration-backend-final.log、storage-registration-frontend-final.log、storage-registration-wallet.log、storage-registration-build-final.log 为准。

- 最终后台 31 项通过（含 20 项 PostgreSQL 测试），存酒前端 17 项通过，构建通过。桌面中文及 390px 手机页面检查了直接入口、目录选酒和自动匹配规则、必填照片及商户确认；没有调用浏览器业务写接口或真实相机。
- 钱包全量测试 97 项通过、4 项既有失败：顾客退出登录 2 项、顾客钱包首页 2 项；失败详情见 target/storage-registration-wallet.log，本轮未修改对应钱包实现。
- 原有 12 项非本轮改动经 SHA256 比较保持不变。

## 钱包测试修复与发布准备

已按用户要求修复此前 4 项失败：4 个用例缺少 wine-storage/home 的专用响应，错误复用了积分余额对象，导致存酒摘要渲染和组件卸载失败。补充真实形状的 Home 夹具，保留原有退出、晚响应和积分断言，额外检查顾客存酒入口。不更改生产钱包实现。修复后该文件 101/101，钱包前端全量 216/216 通过（node --experimental-transform-types --test --test-concurrency=1 src/test/frontend/wallet-*.test.mjs），日志 target/wallet-all-release-tests.log。此前 4 项失败记录为修复前证据，不是当前未解决项。

## 生产发布结果

- 功能提交 `590b54c1f7f428f186037291c75a1841da67cdc0` 已推送 origin/codex/paynow-payment-product-line-staging，线上前后端均为该版本。
- 由干净 git archive 副本构建；归档内文件内容逐一校验，不包含工作区其他已有改动。npm ci、钱包与存酒前端组合测试 233/233、前端生产构建及 Maven 打包均通过。后台 31 项测试的代码状态未变，复用已核实的 PostgreSQL 证据。
- 服务器 43.134.69.75，发布目录 `/opt/rpb/releases/20261025-storage-workflows-590b54c1`，备份 `/opt/rpb/backups/20261025-storage-workflows-590b54c1-before`，备份含旧 JAR、前端、版本标记及经 pg_restore 列表验证的数据库 dump（1,215,600 bytes）。未增加数据库迁移，Flyway 保持 077|77|0。
- 仅重启 rpb-backend；nginx、PostgreSQL、Redis、Docker 身份未变。新 PID 1625144，InvocationID c4b1fc1c24f7489288aa635bdad248d0，多次检查稳定，当前进程日志无 ERROR。
- 9 项已有隔离验收租户的已登录回读通过：产品、卡余额、钱包余额、顾客与商户存酒历史、顾客身份/搜索，以及商业功能关闭时新代登记被拒绝。临时会话仅按本轮 session_hash 撤销；未改变其他会话。无真实存取酒、交付、金额或积分业务写入。
- 4 域名共 32 条公开页面路径与发布首页字节一致；11 项相关静态资源与制品一致；12 项匿名顾客/商户存酒及顾客查询接口返回 401。145 项业务表快照发布前后相同。
- 线上浏览器实际验收：从钱包收银台点击酒水寄存管理、顾客查找空结果与匹配结果、明确选择顾客、打开直接代登记、选择目录酒品自动匹配规则、未上传照片/未商户确认时提交禁用；商户取酒/记录导航、顾客存酒/取酒/记录分区及空记录均正常。没有提交表单，没有上传真实照片或测试摄像头硬件。
- 本地保留清单中的原有 12 个文件逐一 SHA256 相同。
- 最初包含临时网络隔离规则的发布脚本被自动审批审查拦截，未执行；随后采用不改变网络规则的备份、服务重启、就绪回读及前端切换流程完成发布，失败回退逻辑保留旧制品且不恢复业务数据库。

发布证据：本地 `target/storage-workflows-release-20261025/`；服务器 `/home/ubuntu/storage-workflows-release-20261025/`。包含 scope.json、artifacts.json、frontend-tests.log、frontend-build.log、maven-package.log、deploy.log、final-check.json、live-readback.json。

制品 SHA256：JAR `9c1afbd8958d782131eba619d5350438d214ce6274525dafb36758d58218b562`；前端归档 `835083b8fcae06e8ec82fbc65b74786017d73f1c824102bde1e5e7caf45e395d`；首页 `ca60caa407a8144eec3b27175d947e017f7e90dc39995643a89cb96c51562b0e`。
