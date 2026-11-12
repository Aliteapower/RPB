# 扫码设备兼容生产发布记录

日期：2026-11-04。用户明确授权“提交部署”。

## 版本与范围

- 功能提交：`85d86bd81deadbb82ec893d7e947ff7d14a60052`，已推送 `origin/codex/paynow-payment-product-line-staging`。
- 目标：RPB `ubuntu@43.134.69.75`、`/opt/rpb/frontend`、`/opt/rpb/app/FRONTEND_RELEASE_SHA`。
- 生产前端：`3abca5b1` → `85d86bd8`；后端仍为 `3abca5b1431ec800e573f9b214f9c84afdd87a35`。
- 本次29个文件，仅前端功能、关联测试和方案/验证文档；39个范围外未提交文件逐个指纹核对不变，包括原钱包展示、EmployeeStores及私有验证材料。
- 没有后端、API、权限、订阅、数据库迁移、依赖、环境、Nginx或证书变化；不重启后端或相邻服务。

功能包括统一设备偏好、顾客码与商品条码输入适配、连续成功核销、明确已付款后自动接收下一位、商户存酒草稿扫码加瓶。默认仍为相机，需在本机设置中选择扫码枪；钱包设置、存酒设置和统一扫码页折叠设置共用。

## Production Readiness

结论：**GO（本次前端软件发布）**。

| 检查 | 证据 |
| --- | --- |
| 干净候选 | 从功能提交创建 detached worktree，npm ci 后关联169项全通过，vue-tsc及Vite生产构建通过，构建后工作树仍干净 |
| 基线失败 | 开发期完整回归27项失败在去掉本次改动的副本中逐项复现，未将其声称为全绿；本次关联测试在干净候选重新全绿 |
| 隔离与权限 | 共用原领域API/服务端授权及幂等；未知结果保持恢复锁定；未执行真实顾客扣款、核销、存取酒或订阅变更 |
| 恢复点与预算 | 既有受管发布有效，retention只读dry-run无blocked且capacity=ok；发布预算保留额外2GiB；创建本次专属完整回退快照 |
| 运维一致性 | 生产制品管理helper与提交源码哈希一致；应用切换及成功登记共持原发布锁，未嵌套调用加锁CLI |
| 已知限制 | Work设置仍禁用本地浏览器，Chrome/Edge登录后的视觉与焦点未实测；当前无扫码枪，HID实机未验收。该限制已告知，用户随后明确授权发布 |

GO不表示完成Chrome、Edge实机或扫码枪设备验收。设备兼容问题可先将本机设置改回相机；不扩大为真实交易测试。

## 制品与回退

- 受管上传：`/opt/rpb/staging/20261104-scan-device-85d86bd8-upload/frontend.tgz`。
- 不可变版本：`/opt/rpb/releases/20261104-scan-device-85d86bd8`。
- 回退快照：`/opt/rpb/backups/20261104-scan-device-85d86bd8-before`，包含原前端、原版本标记、原后端制品及经pg_restore目录校验的`database.dump`；数据库备份为只读操作。
- 全部新前端文件校验后切换index、版本文件和前端标记；保留旧哈希资源，不清理任何历史版本或备份。
- 原子切换失败自动从本次快照恢复原前端及标记。若后续需回退，在共享`/home/ubuntu/rpb-ledger-release.lock`下恢复该快照中的前端文件及`FRONTEND_RELEASE_SHA`，再验证页面、资源、鉴权和原服务身份。仅前端回退，不恢复数据库覆盖后续业务数据，不重启后端。
- 公开和本机校验通过后才调用`register_release`并绑定上述rollback，再登记上传副本。没有将未知、失败或未完成材料登记为成功。

## 发布验证

- 459个当前版本前端文件：生产本机及公开根域逐字节一致。
- 五域名各10个页面、各1个版本标记：共50个页面、5项版本检查通过，HTTPS证书校验正常。
- 域名：`booking.yumstone.sg`、`platform.booking.yumstone.sg`、`20000000.booking.yumstone.sg`、`sx.booking.yumstone.sg`、`mw.booking.yumstone.sg`。
- 页面覆盖扫码、钱包设置/充值、存酒、商品维护、顾客钱包/卡/存酒及公开订位。
- 8项匿名GET均401，覆盖钱包、卡、酒品条码查询、存酒报表、商品目录及顾客私有入口。
- 后端JAR、环境和Nginx配置哈希不变；后端及nginx/PostgreSQL/Redis/Docker的PID/InvocationID/ActiveState不变。
- Flyway仍为`128|128|0`；发布窗口后端新增ERROR为0，受管版本校验有效。
- 上述为HTTP、资源、源码和组件测试证据，不等同于登录后浏览器视觉、实体相机、扫码枪或真实交易验收。

## 本地证据

`target/scan-device-release-20261104/`：`files.json`、`preserved.json`、`candidate-sha.txt`、`npm-ci.log`、`frontend-tests.log`、`frontend-build.log`、`artifacts.json`、`budget.json`、`incoming.json`、`production-before.json`、`deploy-result.json`、`deploy-stderr.log`及发布/复核脚本。主工作区原有改动继续保留。

原方案与开发验证：[开发方案](../superpowers/plans/2026-11-04-scan-device-compatibility.md)、[实现验证](SCAN_DEVICE_COMPATIBILITY_20261104.md)。
