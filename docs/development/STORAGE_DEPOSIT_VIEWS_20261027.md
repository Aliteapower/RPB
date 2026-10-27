# 直接登记与顾客待审核分离（2026-10-27，本地实现）

默认 task=deposit 直接显示顾客查找/新增，不再折叠“直接帮顾客登记存酒”。选定顾客后立即显示 StorageReceiveForm 的 direct 登记表单，删除子组件中多余的启动登记按钮及混入的该顾客待审核列表；保存仍使用原 registrations 命令和原确认规则，完成后清空表单以便继续登记。

保留四个主栏目，在帮顾客存酒内提供两个独立视图：直接登记、顾客提交·待审核（数量）。点击待审核才进入 task=inbox 并加载 pending-applications，既有收存、拒绝、证据查看和返回申请列表保留。两视图内容互斥，不要求先选顾客来审核。Home 已有 pending 数据用于入口计数，无新 API；inbox 深链接、设置返回和切店均保留任务。原收存权限及新业务开启要求沿用，越权/关闭状态不加载待审核列表。

待审核内容由 MerchantStorageInbox 承担，直接登记由 MerchantStorageDeposit 承担；父页面负责入口及路由，去掉旧 reviewing 状态对子流程可见性的耦合。手机窄屏办理方式入口改为单列，四主栏目仍使用既有布局。中英文提示与默认登记流程同步。

## TDD / UI / Code Review

先补互斥/默认可用/懒加载/路由/权限回归，原实现两个新增用例失败；改造后发现直接登记子组件仍包含第二次启动点击和旧审核列表，进一步收敛为直接表单。更新原审核及商品状态测试，使其从 task=inbox 进入；保留注册、照片、同意、审核实际数量、拒绝原因与未提交 Excel 导出回归。

67 项前端回归全部通过：node --experimental-transform-types --test src/test/frontend/wine-storage-page.test.mjs src/test/frontend/wine-storage.test.mjs src/test/frontend/storage-merchant-scope.test.mjs。npm run build、git diff --check 通过。纯前端路由/视图变化，无新增接口、迁移或权限；不重复执行未受影响的后端检查。

Chrome 静态演示页已核对默认登记、切换待审核及返回，DOM 确认两块内容不同时存在且桌面无横向溢出。预览只用演示数据，不连接业务库；开发预览模块加载慢，改用独立静态构建。截图接口超时，未获得可用截图，不作为截图验收。未做手机实机或生产验收。

原有 Excel 导出后端和其他无关文件内容以 SHA256 核对不变；共享 merchant/text.ts 与页面测试在现有导出改动上追加，导出三项回归保留且通过。证据 target/storage-deposit-split-20261027/{verification.json,frontend.log,build.log,browser-check.json,preserved-before.json}。

无未解决 P0/P1。本轮未提交、推送或部署；原未提交的 Excel 导出仍保留。回退本次视图、路由和文案即可，无数据库操作。
