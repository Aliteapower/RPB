# 钱包 PayNow 凭证复用实施计划

**Goal:** 复用快速收款的上传、预览、OCR及模板，钱包凭证只辅助核对并随原收款事务留档。

**Architecture:** payment 提供不查付款单、不确认收款的公共图片检查服务及结构化字段解析；快速收款继续使用原匹配确认策略。wallet/proof 独立管理单据上下文、凭证、核对及绑定，继续使用原 CollectionService 与记账事务。公共前端上传组件供两产品使用。

**Tech Stack:** Java21/Spring、PostgreSQL/Flyway、Vue3/TypeScript，复用 Tesseract 与银行模板，不增加依赖。

**Spec:** 本会话已确认的“自动带参考号→可选凭证→识别校对→商户核实到账→确认入账”；API合约 docs/api/2026-10-24-wallet-paynow-proof-contract.md。

## 约束与审查重点

- 不提交、不发布；保留已有8个组件及未跟踪资料；不读取私密文件。
- 凭证不调用快速收款 scanAndMatch；OCR不得单独触发资金／积分写入。
- 所有操作限制tenant/store/source；顾客无凭证图片入口；沿用wallet.collection.confirm与wallet.view权限，历史读取不依赖商业开通。
- 新增附加凭证表，不重写账本；图片最多5MiB且核验真实图片类型；私有响应不缓存；原图不返回给顾客。
- 明确不匹配不能普通确认；字段缺失可人工补录并确认到账，人工值与识别值均保留。金额或渠道改变、切换单据必须清除旧结果，迟到响应丢弃。
- 并发、重复上传、超范围附件、复用已绑定附件、收款账户变更、无登录和跨站均有验证。

## 执行任务（inline，按 executing-plans 执行）

- [x] 1. 先写解析／只读提取测试；抽取 PaymentProofImageInspector、ReceiptIdentifiers 及 PaymentProofUpload；快速收款保持原行为并回归。
- [x] 2. 写PostgreSQL/API安全及集成测试与合约；新增V069钱包凭证表、作用域仓储、WalletCollectionProofService、精确API白名单。验证扫描无业务写入、来源／金额／账号校对、幂等和租户隔离。
- [x] 3. CollectionConfirm增加可选proofId并保留旧构造器；原收款事务内校验及绑定凭证，重复Ref沿用现有唯一约束。失败整笔回滚；历史接口返回识别和人工确认信息。
- [x] 4. 钱包收款表显示系统Ref／应收账号、可选上传、核对结果和银行Ref；详情可查看已确认凭证。测试错误、缺字段、重试、过时响应和移除凭证。
- [x] 5. 指针PostgreSQL定向与相关回归、前端行为测试、构建和可行的本地渲染；完成代码/API/数据库审查与发布说明，报告证据及限制。

Ruling: 钱包凭证限定PNG/JPEG，快速收款保留原WebP能力 — 当前JDK无WebP完整解码器，避免用容器头检查冒充图像验证且不引入新依赖；需要WebP时后续独立提供受控解码能力。

Ruling: OCR不可用降级为保留合法原图及missing检查，明确提示人工核实 — OCR只是辅助且所有入账仍须员工填写银行Ref并核实到账；避免识别故障导致凭证无法留档。快速收款错误语义不改变。

Final: 独立审查发现的旧OCR Ref残留和图片头校验不足已通过失败用例复现并修复；末次前端115项、后端定向62项和独立原钱包62项回归通过。跨单据／门店用例末次单项通过，详见验收记录。浏览器及真实OCR限制明确保留，不代表真机或生产验收。
