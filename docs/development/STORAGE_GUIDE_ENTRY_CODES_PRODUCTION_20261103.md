# 酒存指南本店网址与二维码发布

2026-11-03，D:/RPB，分支codex/paynow-payment-product-line-staging。沿用本聊天提交、推送及发布授权。功能提交及生产前端 `7cc372fd835dc137f2e708d4bd8017ea7de7e0c7`，后端仍5c0efc9b。

## 新行为

酒存→设置→操作指南显示本店员工酒存入口、顾客存酒入口的完整URL、门店名称与带名称二维码，可打开网址及下载PNG。员工入口为统一分享域名/login?entry=staff&tenantCode=...&redirect=/stores/.../wine-storage；顾客入口为统一域名/wallet/.../wine-storage。复用产品distributionEntries、buildTenantEntries及publicEntryOrigin，不单独拼租户子域或申请证书。

身份由已认证/api/v1/me/stores取得，商业及岗位访问由/api/me/apps?includeAccess=true的productAccess判定，不调用管理员专用常用入口接口。关闭、无权限、外租户/缺失门店和加载失败时不提供业务入口；复用30秒可见刷新，恢复资格后恢复展示。切店/身份变化清理旧入口，迟到响应不能覆盖。

设置与指南中的“下载本店指南PDF（含入口码）”先重查身份和资格，再本地生成三页PDF：原两页指南加中文本店入口页，含两组二维码、完整URL及可点击链接，页脚1/3至3/3。二维码通过共享带名称渲染器的像素解码校验。文件名含安全处理后的门店名称；生成期间切店、退出或资格失效不下载旧文件。生成后保留“点击下载/保存”链接作移动端兜底，范围变化/卸载释放Blob。通用文字PDF继续保留并明确标注“不含入口”，不混入任何固定租户资料。

新增pdf-lib1.17.1与锁定依赖，PDF包单独lazy chunk，仅导出时调用；无外部二维码/PDF服务、字体请求或数据上传，无API/后端/数据库/证书更改。

## 验证

- 154项关联前端测试全部通过，并在干净归档上再次通过。覆盖角色URL/统一域名/新租户、外租户及停用拒绝、无需管理员目录、迟到身份、关闭/失败清空与恢复、只读GET、导出前重查、切店取消迟到PDF、可手动保存及安全文件名。含原租户入口/host及酒存/取酒车/报表回归。
- 原tenant-entry-links测试直接导入.ts，需要Node --experimental-strip-types；首次不带flag失败于.ts加载，使用正确运行器后通过，没有变更已有断言。
- 真实pdf-lib合并测试验证三页及两个链接URI。使用实际共享名称QR渲染器在本地canvas绘图，中文门店名及角色标题可见；将实际PDF第三页栅格化后分别解码员工/顾客QR，均与原完整URL严格一致。中文附页目视检查无遮挡，原两页页脚更新。此为软件PDF/二维码验证，不等同真实设备、微信或打印扫码。
- 干净归档4185个Git blob核对一致，fresh npm ci --ignore-scripts、类型检查和完整构建通过，npm audit零漏洞。原通用PDF及新入口模板与源文件字节一致；第三方PDFchunk不被HTML预加载。
- 生产450个文件逐字节匹配、5域名85次HTML、5次版本/TLS、9项匿名401、两种PDF在5域名共10次文件核验通过。后端MainPID501640、InvocationID4d411c88031e41929c5d239da78a212f、active及NRestarts0保持，jar SHA256仍0a98c8f15c329442645a49b85e918f799903a85d522a5503c4420e8dd80e1a10。
- Work设置禁用浏览器自动化，网页实际渲染、手机点击下载和真机/打印扫码未验收。没有伪造生产顾客/员工登录或变更真实订阅；无关工作区改动保留。此轮无后端变更，未重复数据库测试。

## 发布与回退

仅发布前端，无业务数据或权限写入，无后端重启。发布后可用空间437747712字节，未清理历史备份。备份/opt/rpb/backups/20261103-storage-guide-entries-7cc372fd-before；恢复index.html、frontend-version.json、FRONTEND_RELEASE_SHA回退7d8fad91，旧静态资产保留，不恢复数据库。

证据target/storage-guide-entries-release-20261103含source-verification、npm-ci、tests、build、artifacts、review-readiness、deploy-result、public-result、三页合成QA PDF、附页PNG及qr-pdf-result。QA文档为合成门店，不作为真实门店成品分发。代码审查/生产就绪为APPROVE/GO，明确保留网页及实体设备验收缺口。
