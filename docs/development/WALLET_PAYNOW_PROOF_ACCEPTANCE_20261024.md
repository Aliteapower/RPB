# 钱包 PayNow 凭证实施、审查与验收

日期：2026-10-24。项目 D:\RPB。实现范围为本会话确认的共享上传／识别、钱包独立校对与原事务确认；未提交、推送或部署。

## 模块与 API 审查

- payment/application/PaymentProofImageInspector 只负责临时图片OCR和同租户银行模板增强，不依赖付款意图仓储，不匹配订单、不确认收款。PaymentReceiptIdentifiers 区分系统参考号、银行交易Ref及收款代理号；不从银行交易ID推断系统号。
- 快速收款原服务继续自行匹配意图及自动确认，仅调用提取出来的公共检查服务；原识别选择、模板策略与业务权限不改。修复公共金额规则对未分组1000.00截断的问题，有失败／通过证据。
- wallet/proof 对源单据、身份与门店负责，控制器不访问仓储。钱包不依赖payment产品开通；复用原wallet权限与既有订单恢复规则，不新增员工、租户或账本模型。
- wallet.confirm 的可选proofId在原账户／源单据锁及资金事务内校验、绑定；绑定方法要求MANDATORY事务，不能独立发起入账。同源、同金额、同渠道、同收款账户且无明确不匹配才能确认；银行Ref不可替换成系统号。
- 省略proofId的旧JSON只省略这个新字段，其他旧null字段保持原序列化语义，避免改变原命令幂等摘要。
- 精确开放本地GET context/list/images与POST multipart scan；钱包原同源标记、跨站拒绝和会话身份继续适用。没有顾客或匿名凭证读取入口。

## 数据库审查

V069仅附加凭证表及所需收款复合唯一键。tenant_id、store_id及来源／收款复合FK约束禁止跨租户、门店引用；只能属于充值或消费之一。图片MIME限PNG/JPEG，最多5242880字节，金额为正；上传幂等键、收款绑定和同收款账户已绑定图片摘要都有唯一约束。原图、上传人、created_at、updated_at及最终人工确认JSON保留。查询有tenant/store/source索引，动态SQL仅在已验证的topup/bill两种固定字段间选择。

查询图片设置private/no-store、nosniff；仅员工有权读取原图。图片先核验类型、尺寸、3000万像素上限并完整解码，再交OCR；不支持的WebP明确拒绝，原快速收款格式不变。无账本或余额迁移，无外部图片服务、依赖或环境变量变化。回退保留附加表及资金历史，不恢复旧数据库覆盖真实交易。

## 测试与证据

本地数据库使用本项目 target/local-postgres-current.txt 指向的127.0.0.1:61644/postgres、postgres空密码，各测试创建独立随机schema。未更改共享业务数据或其他项目进程。

| 场景 | 结果／证据 |
| --- | --- |
| 共享提取、银行模板及系统号／银行Ref区分 | 初次解析缺类失败；实现后公共测试通过；target/wallet-proof-parser-red.log、wallet-proof-shared-tests.log |
| 不带千位分隔符的1000.00金额 | 新测试先失败后通过；wallet-proof-final-unit-red.log、wallet-proof-backend-delivery.log |
| 真PostgreSQL上传无资金写入、幂等重试、金额不符、原图留档、绑定、重复图片回滚、人工补录及OCR不可用 | WalletTransactionsPostgresTest的实际业务链，wallet-proof-backend-delivery.log |
| 跨单据凭证不可绑定、跨门店凭证不可读取 | 同一真实PG用例末次加强验证，wallet-proof-scope-delivery.log |
| 未登录、缺标记、跨站、错误HTTP方法和顾客路径 | WalletLocalRuntimeSecurityTest；图片内容及私有响应头、超大上传、旧JSON兼容由WalletCollectionProofApiTest验证 |
| 图片有效性、截断／伪头／超大文件及WebP拒绝 | 原测试先2项失败；修复后通过；wallet-proof-images-red.log、wallet-proof-backend-delivery.log |
| 前端上传、校对、未自动确认、匹配／不匹配、迟到响应、上传重试、金额变更、换图缺Ref、OCR不可用补录、上下文请求重试 | 前端相关五组115项通过；wallet-proof-front-delivery.log |
| Vue类型检查与Vite构建 | 通过；wallet-proof-build-delivery.log |
| 原钱包充值、消费、退款等既有回归 | WalletPostgresTest全部62项通过；wallet-proof-backend-verified.log；此前完整119项轮次也通过 |

最终后端定向8组62项通过，覆盖最新凭证实现、接口、权限、解析及模板；复用独立原钱包62项回归的通过结果，合计124项不同测试。末次仅为新增跨单据／门店断言复跑同一个真实PG用例，不重复计数。

## 审查发现与修复

按executing-plans进行了独立只读审查，发现两个P1，均已处理：

1. 旧图自动填入的银行Ref在换图／改金额后残留。组件用例先证明旧BANK-123仍在，修复后按自动填入来源清理；新图缺Ref和金额改变均不能沿用旧值。明确人工输入可保留并记录。证据 wallet-proof-ref-red.log、wallet-proof-ref-green.log、wallet-proof-ref-final.log及末次前端回归。
2. 仅凭WebP头不足以验证真实图像或像素上限。钱包改为仅接受可完整解码的PNG/JPEG，并验证解码结果与像素上限；原快速收款WebP不变。伪WebP与截断图先失败后通过。

P0：未发现。上述P1已修复。最终代码/API/数据库自审：APPROVE_WITH_COMMENTS，评论为下述验收限制；没有再次无依据全量审查或把可选样式修改当作交付前提。

## 限制

- 未运行真实Tesseract银行截图识别：银行模板和字段解析执行真实代码，OCR输出使用受控替身；这不等于银行接口验证到账。
- 本地浏览器夹具服务曾启动，应用内浏览器及Chrome通道连接超时，未得到可确认截图；已停止本轮夹具服务。未做真机相机和生产已登录业务验收。
- 钱包暂仅PNG/JPEG，不支持WebP／HEIC／PDF。OCR不完整或不可用时展示人工核查提示并保存合法原图，入账仍要求银行Ref及员工到账确认。
- 未跑全项目所有测试，不把之前现金充值测试环境超时计为通过；本轮没有修改该测试。
- 未提交或部署。本轮V069与前后端须配套发布，业务回退不可覆盖后续资金数据。
