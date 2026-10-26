# 取酒流程精简（2026-10-26，本地实现）

## 范围与设计

- 商户确认顾客后，在对应酒品卡右侧调整瓶数、直接生成顾客扫码确认码；顾客扫码授权后，商户仍需确认实物交付。
- 顾客端保留逐卡“出码取酒”，移除两端重复的“选择领取”、额外瓶数字段、核对清单和“下一步”流程。顾客扫描商户码的入口保留。
- 复用独立 `StorageQuantityStepper`：减号、数字、加号；最少 1、最多剩余存数，开瓶固定 1。手工输入归一为有效整数，库存变化时同步限制。
- 商户确认码按当前顾客、单个酒品和瓶数创建，显示本码数量。出码期间锁住其他出码操作；自动查询顾客授权状态，支持手动刷新、取消、失败恢复。
- 已有批量单的接口和恢复路径不变，仍可显示旧单二维码；只移除新建批量单的重复 UI。

## TDD Review

先增加“两端没有旧步骤且有上下限加减控件”的页面测试；实现前因旧 checkbox 仍存在失败，实现后通过。更新原有流程测试以匹配直接出码，保留原有鉴权、取消、恢复和库存规则断言。

最终命令：

```powershell
node --experimental-transform-types --test src/test/frontend/wine-storage.test.mjs src/test/frontend/wine-storage-page.test.mjs src/test/frontend/storage-merchant-scope.test.mjs src/test/frontend/storage-customer-selection.test.mjs src/test/frontend/cards-scan.test.mjs src/test/frontend/wallet-camera.test.mjs
npm run build
git diff --check
```

63 项测试全部通过，其中页面测试 36 项；vue-tsc 与 Vite 构建通过。覆盖两端上下限、输入归一、已开瓶、库存刷新、顾客归属、重复点击只提交一次、授权前不可交付、自动授权状态更新、取消、旧批量单原幂等键恢复、失败恢复及原扫码回归。

## 浏览器验证

使用 127.0.0.1:5206 的内存模拟接口预览最终构建，不连接后端或数据库。桌面、约 321px 与 393px CSS 窄屏检查通过；393px 顾客端 document scrollWidth 与视口同为 393，无横向溢出。浏览器操作确认上限禁用加号、商户点击后右侧显示绑定 2 瓶的确认码。

截图及日志：`target/storage-pickup-simplification-20261026/`。浏览器测试不代替手机相机、微信或真实后端扫码验收。

## Code Review

- P0/P1：无已知未解决问题。
- 仅调整存酒取酒组件、双语文案、样式与对应前端测试；后端接口、数据库、权限、App Gate 与交付扣减逻辑不变。
- 沿用现有 API 的持有人/租户/门店边界、有效期、单次码和幂等保护；没有自动执行实物交付。
- 原有产品开关及历史访问等工作区改动按 SHA256 保留。共享页面测试中的原有新增用例保留；只更新与此次流程变化直接相关的旧断言并增加回归。
- 结论：APPROVE_WITH_COMMENTS。本地实现完成，未提交、推送或发布；实机验收尚未执行。
