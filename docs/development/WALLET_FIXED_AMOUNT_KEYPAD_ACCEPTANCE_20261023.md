# 钱包收款固定金额键盘验收

2026-10-23，D:\RPB；基线 2ebc7ed1，当前线上运行代码 cb400bf7。仅本地开发和测试，未提交、推送或部署，保留已有无关改动。

## 实现

- WalletStaffCollect 以独立 WalletAmountKeypad 替代原 number 输入框。页面内常驻金额显示、7/8/9 排列数字键、删除、C 清空、00、加宽 0 和小数点，金额区为 output，不弹系统金额键盘或金额确认对话框。
- 参考 src/pages/PaymentQuickPayPage.vue 的布局，复用现有 paymentPresentBridge 的按门店快捷金额读取、解析和保存能力。默认 5、10、20、50、100、200；编辑在页面内展开，同一设备当前门店与 payments 页面共用已有配置，无新增配置服务或账本。
- 金额最多两位小数，限制到钱包既有最大范围，清空和零金额不可进入扫码。选择快捷金额是替换当前金额。扫码中、提交中、结果未确认期间所有金额键和快捷金额均禁用，组件处理函数也校验 disabled。
- 说明填写、个人码扫码授权、扣款请求、幂等键和原交易恢复保持原样；键盘按钮均 type=button，不会触发提前收款。没有后端、API、迁移、权限或依赖变更，也未重构 payments 页面。

## 验证与审查

- 新行为测试先失败（target/wallet-keypad-red.log），实现后最终钱包前端 118/118（target/wallet-keypad-front-regression.log），其中实际 Vue 营销／积分／收款组件 47/47（target/wallet-keypad-front.log）。覆盖输入两位小数、忽略第三位、退格、清空、00、最大位数、快捷替换、编辑与本机持久化、金额锁定、无提前资金请求、付款只用新扫码授权、未知结果恢复及员工／门店变化。
- npm run build（Vue 类型检查、Vite）通过，target/wallet-keypad-build.log。git diff --check 通过。
- 本地浏览器使用 target/local-postgres-current.txt 所指 61644 运行环境及本线程已有隔离 schema，独立服务 8087／5187；未使用或修改公共验证 schema、线上服务和真实顾客交易。仅操作金额键盘，未扫码扣款。
- 浏览器核对默认 0.00 时扫码按钮禁用；点快捷 20 显示 S$20.00、按钮启用，固定数字键盘和快捷金额布局符合参考。截图：target/wallet-keypad-e2e-20261023/fixed-keypad-top.png、fixed-amount-keypad.png。
- 按 TDD review、code review 与 release-note 检查；金额组件只负责输入与本机快捷设置，收款编排保留在原页面，审查无阻塞问题。

## 风险及回退

真实手机触控、iOS 软件键盘和相机尚未重测，桌面浏览器及组件验收不视为真机通过。快捷金额沿用本机门店设置，不跨设备同步。恢复本轮金额组件、收款页、文案及测试差异即可回退，无数据库恢复操作；不得回滚原有其他改动。
