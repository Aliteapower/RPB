# 存酒主栏目紧凑切换栏（2026-10-27，本地实现）

按用户钱包收银台参考，将商户酒水寄存页面的四个大按钮改为白色圆角底板、浅绿选中项的单行切换栏。显示短标签“存酒、取酒、当前寄存、记录”，英文 Deposit / Collect / Stock / Records；aria-label 保留“帮顾客存酒、帮顾客取酒、当前寄存、存取酒记录”等完整名称。

样式仅限 storage-merchant-page，不改变顾客页面或实际业务按钮。grid-auto-flow:column 与等宽自动列适配正常四项及关闭新业务后的三项；移除手机两行规则，最小触控高度 44px，保留焦点轮廓、禁用、aria-pressed、原任务路由及待审核归属存酒主栏目的行为。

67 项前端回归通过，覆盖存酒登记、审核、取酒、记录、Excel、权限与任务切换；只更新主导航可见文字的断言，不改测试场景。npm run build、git diff --check 通过。纯视图/样式变更，无接口、后端、迁移或业务状态变化，不重复执行未受影响的后端测试。

Chrome 静态演示页几何检查：实际 390px 中文、319px 中文及 319px 英文均为单行，各标签无截断，页面无横向溢出，导航总高约 56px。预览使用演示数据，不连接业务库；不作为手机实机或生产验收。已恢复浏览器尺寸并关闭测试页。

原 12 项钱包及其他无关文件 SHA256 保持不变。证据 target/storage-compact-tabs-20261027/{verification.json,frontend.log,build.log,browser-check.json,unrelated-before.json}。UI/Code Review 无未解决 P0/P1。未提交、推送或部署，回退本次前端文案与样式即可，无数据回退。
