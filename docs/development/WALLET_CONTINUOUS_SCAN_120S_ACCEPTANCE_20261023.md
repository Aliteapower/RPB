# 连续办理、120 秒个人码及限额入口验收

2026-10-23，D:\RPB；基线 9f6639ee（运行代码 bf01194d）。仅本地开发测试，未提交、推送或部署，保留所有原工作区改动。

## 实现

- 充值／积分顾客识别栏改为“扫下一位顾客”，重置时清空顾客、资金账户、身份、上次错误和当前办理选择，重新进入扫码；提交期间禁止切换。
- 新增单一职责 WalletNextCustomer 组件，充值已入账／取消结果及消费已结清／关闭结果可直接进入下一位办理；充值进入空白扫码页，消费进入空白金额页，再用新授权扫码付款。未决交易仍按原恢复和幂等流程处理。
- 统一个人码及旧身份码签发共用 120 秒常量；刷新立即失效旧码，状态轮询保留过期／付款后更新。
- 顾客“出示个人钱包码”页面将“支付上限设置”移到二维码上方，可展开查看、输入、修改及关闭，一次授权持续有效的规则不变。不设置仍可充值、办理积分。
- 本租户、钱包计划内过期／刷新失效码，身份识别返回 409 WALLET_PERSONAL_CODE_EXPIRED，明确提示重新出示当前码；未知、跨租户及跨计划仍保持 404。不保存明文码，不绕过有效期，不放开一次付款约束。

## 相册报错诊断

图片识别成功后会请求身份解析；原查询把 expires_at 条件放在 WHERE 内，过期码直接变成通用“记录不存在或无权访问”，无法指导用户。有效期内身份读取本来可重复，文件选择器也会清空 value 允许再次选择同图。保存的图片不会随顾客刷新而更新，不能保证第二次使用仍有效。

本轮证明上述过期／刷新路径可复现，但未取得截图当次请求日志，不能把具体那次失败断言为过期，也未将相册第二次操作视为天然非法。跨租户／钱包计划仍可能按原规则被拒绝。

## 测试证据

- 先失败：个人码寿命测试显示预期 2M、实际 1M（target/wallet-code-120-red.log）；前端连续顾客测试在缺少新入口时失败（target/wallet-next-scan-red.log）。
- 后端 32 项通过：WalletPersonalPaymentPostgresTest 20、WalletPersonalPaymentApiTest 4、WalletCustomerIdentityApiTest 4、WalletLocalRuntimeSecurityTest 4。主日志 target/wallet-code-120-backend.log；新增身份冲突 API 验证在 target/wallet-code-120-api-final.log。
- PostgreSQL 使用 target/local-postgres-current.txt 指向的 61644 运行环境和随机测试 schema。覆盖 119 秒重复读取、120 秒边界、刷新失效、跨租户不可见、同码只能付一笔、重试、超限及过期并发锁后重验。
- 钱包前端 116/116（target/wallet-code-120-front-regression-final.log）。首轮高负载下旧充值测试用固定 30ms 等待造成断言过早，改为等待真实异步点击处理完成，未修改业务逻辑；重跑全部通过。
- npm run build 和 Maven package 通过（target/wallet-code-120-build.log、target/wallet-code-120-package.log）；git diff --check 通过。
- 本地运行 API 4 项通过（target/wallet-code120-e2e-20261023/proof.json）：120 秒、重复身份读取、刷新旧码 409、新码识别成功，未执行资金命令。
- 浏览器隔离环境验证顾客二维码上方的限额设置、当前限额及修改／关闭按钮；商户已入账充值结果的“扫下一位顾客”进入 /wallet/topup?scan=1，未携带旧顾客或金额。截图同目录 customer-limit-entry.png、merchant-next-customer.png。

## 审查、兼容与风险

按 troubleshooting、TDD review、API review、code review 和 release-note 检查。职责仍为身份识别、付款授权及业务操作独立；下一位组件仅清理本地状态和导航，不创建交易。自审无阻塞问题。

无数据库迁移、新权限或依赖变更；仅新签发码改为 120 秒，已经签发的码不会延长。详情入口、旧 API 兼容保留；身份过期错误由 404 细化为已有 409 个人码错误。其他产品线无业务变更。可按本轮文件差异回退，不回滚此前交易分类、营销或其他已有工作，无历史数据回写。

真实手机相机、iOS 相册重复上传操作尚未重测；API 重复识别及图片失效提示不等同于真机图片识别通过。本轮原线上服务及配置没有变更。临时验证服务使用独立 8087／5187，完成后关闭，只保留原有本地服务。
