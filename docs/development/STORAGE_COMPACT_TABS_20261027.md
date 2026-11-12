# 存酒主栏目紧凑切换栏（2026-10-27，已发布）

按用户钱包收银台参考，将商户酒水寄存页面的四个大按钮改为白色圆角底板、浅绿选中项的单行切换栏。显示短标签“存酒、取酒、当前寄存、记录”，英文 Deposit / Collect / Stock / Records；aria-label 保留“帮顾客存酒、帮顾客取酒、当前寄存、存取酒记录”等完整名称。

样式仅限 storage-merchant-page，不改变顾客页面或实际业务按钮。grid-auto-flow:column 与等宽自动列适配正常四项及关闭新业务后的三项；移除手机两行规则，最小触控高度 44px，保留焦点轮廓、禁用、aria-pressed、原任务路由及待审核归属存酒主栏目的行为。

67 项前端回归通过，覆盖存酒登记、审核、取酒、记录、Excel、权限与任务切换；只更新主导航可见文字的断言，不改测试场景。npm run build、git diff --check 通过。纯视图/样式变更，无接口、后端、迁移或业务状态变化，不重复执行未受影响的后端测试。

Chrome 静态演示页几何检查：实际 390px 中文、319px 中文及 319px 英文均为单行，各标签无截断，页面无横向溢出，导航总高约 56px。预览使用演示数据，不连接业务库；不作为手机实机验收，生产检查见下。已恢复浏览器尺寸并关闭测试页。

原 12 项钱包及其他无关文件 SHA256 保持不变。证据 target/storage-compact-tabs-20261027/{verification.json,frontend.log,build.log,browser-check.json,unrelated-before.json}。UI/Code Review 无未解决 P0/P1。功能 90d7dc35 已提交、推送并部署，回退本次前端文案与样式即可，无数据回退。

## 生产发布 / GO

前端版本 90d7dc3550b57488642ff561756bb26848e2d409，后端继续为 b49ddb8fb1402c231c00695faa47864cd347347e。本次仅前端发布，没有重启后端、数据库迁移、环境或权限变更。2,850 个干净源码文件核对 Git blob 一致，后端源码与当前生产版本一致；干净构建与 67 项前端回归再次通过。原 12 项无关文件 SHA256 不变。

线上 64 页面、33 关键资源、68 匿名拒绝及 4 匿名应用发现通过；32 项登录态读回、3 份中英文/筛选/空结果 xlsx 生成与内容检查通过，另 5 项导出身份/参数拒绝通过。267 个发布文件一致，7,083 个旧资源保留。152 组业务指纹、存取酒模式、V084（084 / 84 / 0）、环境及所有邻接服务身份不变。后端 PID 2223012 / InvocationID 13e27c5c3e0b4f429589e2bbeaee947e 保持原样，观察窗口无新增 ERROR。临时验收会话已撤销，没有新增存取酒业务。

发布目录 /opt/rpb/releases/20261027-storage-compact-tabs-90d7dc35；备份 /opt/rpb/backups/20261027-storage-compact-tabs-90d7dc35-before，前端备份逐文件比较通过。失败自动恢复旧 index.html 和 FRONTEND_RELEASE_SHA；手工回退同样恢复该备份入口/标记并核验旧前端 b49ddb8f，不操作后端和业务数据库。

证据 target/storage-compact-tabs-release-20261027/{final-check.json,public-check.json,live-readback.json,export-readback.json,source-proof.json,unrelated-final.json,deploy.log}。手机实机及生产浏览器交互未另行验收；窄屏单行/文字宽度证据为本地演示页检查。
