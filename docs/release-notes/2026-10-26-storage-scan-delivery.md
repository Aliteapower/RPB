# Release Notes

## Version / Date
2026-10-26 storage-scan-delivery；已按用户授权提交、推送并完成前后端生产部署。

## New
- 商户扫描顾客自己生成并授权的取酒码，直接核销码内酒品及数量，不再二次点击“确认实物已交付”。扫码前明确提示即刻核销，成功显示酒品/数量、完成时间及码已失效，可继续办理下一位。
- 新增独立 StorageScanDeliveryService 和 POST /api/v1/stores/{storeId}/wine-storage/scan-deliver，契约见 docs/api/2026-10-26-storage-scan-delivery.md。仅接受 customer 来源的码，复用原 deliver 的意图锁、库存锁、期限、数量及整批事务校验。
- 新增 scan.delivered 审计，记录操作人及明细，使用原交付完成时间；不把明文二维码写入审计。

## Changed
- 完成后服务端立即关闭码；不同键/收银员再次扫已完成码会失败，不增加扣减。响应丢失时保留原请求及幂等键，恢复返回原成功结果，不重新扣减；即使之后码过期，原键成功响应仍可重放。
- 顾客展示取酒码时每2.5秒及页面重新可见时刷新状态，已完成后隐藏二维码/取消按钮，提示码已失效；隐藏页面暂停请求，卸载清理计时器和事件。服务端失效不依赖客户端刷新或二维码截图是否仍可见。
- 系统提示区分顾客出码与商户出码。顾客生成码前会提示商户扫码立即扣减。商户出码、顾客扫描授权后再确认交付的原流程保留，旧 /scan 保持只读以兼容旧调用。
- 扫码完成结果独立展示，不依赖额外顾客查询成功；下一次扫码前清除上一笔提示，避免旧成功结果误导。

## Fixed
- 防止重复识别回调、并发和恢复请求引发重复核销；识别后关闭扫描器，忙碌时阻止新命令。
- 原顾客码提示“仍需最终确认”的文案改为符合扫码即核销；完成状态即使意外携带code也不渲染二维码。

## Migration
无数据库迁移或新依赖，生产仍可保持 V078。复用原意图、库存、命令响应和审计表。

## Permission
沿用 wine_storage.deliver、商户身份、租户与门店范围。仍允许商业功能关闭时受控领取有效库存；不放开顾客身份的商户写入。请求需原同源标记与CSRF约束。顾客仍需先确认具体酒品/数量并生成码，不自动处理未授权商户码。

## Validation
- 后台42项通过，含真实指针 PostgreSQL 隔离schema测试：扫码即扣、原键成功响应重放、不同键重复拒绝、同键/不同键并发、商户来源码拒绝、取消/超时/跨店/跨租户拒绝、整批库存冲突回滚、权限拒绝及原寄存回归。
- 钱包/存酒组合前端240项通过；最后仅调整下次扫码先清除旧状态，相关存酒24项再次通过。钱包代码未变，可复用其中216项结果。最终TypeScript/Vite构建通过。
- 新增前端验证：同次解码两回调只发1笔核销；无需顾客查询和额外交付POST；核销成功不显示二维码/确认按钮；顾客返回页面后读取完成状态自动隐藏二维码；断网用原code与原幂等键恢复。
- 本地环回模拟API与实际构建页面验证：粘贴合成取酒码走真实解码回调，直接显示核销成功，继续扫码同一码明确提示失效。模拟仅内存数据，不连接数据库/生产；其他写路由拒绝。没有测试真实摄像头、微信/iPhone硬件或线上真实取酒。
- 后台使用 D:\RPB\target\local-postgres-current.txt 指定61644端口的PostgreSQL，测试自动创建并清理隔离schema；未改实际业务schema。
- 证据：target/storage-scan-delivery-{backend,frontend}-final.log、storage-scan-delivery-frontend-latest.log、storage-scan-delivery-build-verified.log。

## Review / Risk
API、状态转换、事务传播、幂等、权限和UI自审完成。扫码编排、成功结果、顾客刷新分别独立，复用原库存规则；未修改钱包金额/积分、产品目录、卡、预约、排队、座位及清洁流程。
前后端需配套发布。顾客页面离线时可能暂时显示旧二维码，服务端已完成的码仍不可再用。没有改变商户出码的交付流程。

## Rollback Notes
本轮已发布。回退前端可以恢复原两步操作；后端新接口可暂保留，旧接口行为不变。已完成的核销和审计必须保留，不能恢复数据库来撤销真实交付，不整体回滚其他已有改动。

## 生产发布结果

- 功能提交 `b4a3bf0d4f525130bab71e419eb77fd2fdee0785` 已推送 origin/codex/paynow-payment-product-line-staging，线上前后端均为该版本。
- 从干净 git archive 源码构建，npm ci、全部240项钱包/存酒前端测试、TypeScript/Vite构建及Maven打包通过。42项后台测试与本次提交的后台代码一致，复用本轮已经核实的PostgreSQL隔离测试证据。
- 没有数据库迁移、环境变量或依赖变更；Flyway发布前后均为 `078|78|0`。
- 服务器 43.134.69.75；发布目录 `/opt/rpb/releases/20261026-storage-scan-b4a3bf0d`；备份 `/opt/rpb/backups/20261026-storage-scan-b4a3bf0d-before`，含旧JAR、前端、标记和经pg_restore列表验证的数据库dump（12,451,875 bytes）。回退只恢复制品，保留所有已完成业务数据。
- 新rpb-backend PID 1711100，InvocationID `d156249e7f8e4fdfa5af6cbc9f8671ba`，发布和最终观察后稳定，日志无ERROR。nginx、PostgreSQL、Redis、Docker身份均未改变。
- 16项已登录QA接口回读通过，含目录、卡、钱包、寄存/人员/待办、新扫码接口。新接口使用先经SQL证明不存在的随机合成码验证404、错误格式400、顾客身份写商户接口401，不消费任何真实码。成功扣减、并发和重复码场景由本轮真实PostgreSQL隔离schema测试覆盖，不将生产拒绝性探测描述为真实核销验收。
- 4域名32个公开路径响应与新首页相同，11个相关静态资源字节与制品一致；24项匿名接口检查均401，包含新扫码写接口。
- 业务快照本轮扩大到147项，涵盖人员/审计、完整平台应用及全部权限；部署前、回读后和浏览器检查后均相同。临时QA会话按本轮hash撤销，没有修改其他会话。
- 生产浏览器跳转登录页，当前商户会话已过期，因此本轮未完成已登录页面点击验收；未代填生产密码、处理滑块或扫描真实取酒码。前文成功/失效页面和自动隐藏验证属于本地模拟UI及组件测试，不能作为生产实机扫描证明。
- 原有12个无关本地文件的SHA256保持一致，未混入功能或证据提交。

制品SHA256：

- JAR `57a74edb2af22398405e7492e3d62a6ef40f9ce477fb05066ebe08f5b51092ba`
- 前端归档 `d8096e5aae317e9ba977f3d4d2e8a27721e253bc8a4423757df3f8ad26affafc`
- 首页 `7b2adcb4e80ec49ebb3943eeeb19f4804f087911541930a1d98b74af049a7be1`

证据：本地 `target/storage-scan-release-20261026/`，服务器 `/home/ubuntu/storage-scan-release-20261026/`，含scope.json、artifacts.json、frontend-tests.log、frontend-build.log、maven-package.log、deploy.log、live-readback.json、final-check.json及after-browser.log。发布制品、临时脚本和合成码不提交。
