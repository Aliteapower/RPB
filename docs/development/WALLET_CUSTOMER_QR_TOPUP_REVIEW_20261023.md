# 钱包扫码充值实施审查

## 范围与 API 审查

接口契约见同目录 `WALLET_CUSTOMER_QR_TOPUP_CONTRACT_20261023.md`。复用钱包应用服务、授权仓储、扫码组件和原收款确认；没有跨产品线调用支付内部对象，没有控制器直连仓储。没有新增迁移或 URL 白名单。

识别的可选 `purpose=topup` 只选择既有充值权限，不允许凭充值识别直接扣款。其他用途返回 400；省略用途保留原扣款识别语义。租户与门店来自已有会话，二维码只以请求体传输，响应沿用既有 no-store 策略。发行主体边界由钱包计划和门店关系约束。

## TDD 与验证

新增测试先于实现：后端首先因缺少用途重载编译失败，随后完成实现及测试编译修正。测试使用本工作区指针 PostgreSQL 61644 / postgres，独立临时 schema，执行结束删除测试 schema。

| 验证范围 | 结果 |
| --- | --- |
| WalletPostgresTest：扫码识别无资金副作用、重复现金确认幂等、付款码仍可扣款、WP 无直接扣款授权、租户/门店/发行主体/冻结/过期/门禁 | 50 通过 |
| WalletApiIdentityTest：Cookie/员工会话、用途传递和租户身份 | 5 通过 |
| WalletRequestSecurityTest / WalletCommercialGateTest / WalletLocalRuntimeSecurityTest / WalletCashTopupReceiptTest | 11 通过 |
| wallet-workspace.test.mjs：模式、门店、授权清除、迟到响应、充值识别码不轮询扣款结果等 | 11 通过 |
| wallet-topup-qr.test.mjs：实际顾客码页与商户充值页组件（摄像头和 QR 显示器使用边界替身） | 2 通过 |
| wallet-camera / wallet-cash-topup / wallet-navigation / wallet-scan-charge | 25 通过 |
| npm run build：Vue 类型检查与 Vite 生产构建 | 通过 |

已有测试的 Vite WebSocket 24678 端口提示未影响 25 项回归测试结果；新测试禁用 HMR。Mockito 动态代理提示为工具链提示。

## 代码审查结论

未发现阻断问题。识别路径无资金写入；实际充值继续在原事务中验证实际收款，使用既有幂等键与账务记录。刷新/切换码用途或门店会清除旧码，迟到响应不得恢复旧授权；识别用于充值后不会把 WC 留到扣款页。中英文文案齐全。

手机实机摄像头仍需后续验收；本轮已验证组件交互、摄像头生命周期及二维码类型检查，没有声称完成实机或生产验收。保留原有两个无关未跟踪文件，未提交、推送或部署。
