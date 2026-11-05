# 存酒单行切换栏（已发布，2026-10-27）

酒水寄存管理顶部四个主栏目改为紧凑单行切换栏，短标签为“存酒、取酒、当前寄存、记录”，浅色选中状态保留商户端配色，为下方操作腾出空间。完整名称保留在无障碍标签中；任务、权限、待审核、取酒及 Excel 操作不变。

67 项前端回归、构建及差异检查通过。Chrome 演示页在 390px 中文、319px 中英文宽度验证单行、无文字截断及横向溢出，总高约 56px。原 12 项无关改动保留；未另做手机实机验收，生产验证通过，功能 90d7dc35 已提交部署。无后端、迁移或权限变更，回退前端文案及样式即可。

详情 docs/development/STORAGE_COMPACT_TABS_20261027.md，证据 target/storage-compact-tabs-20261027/verification.json。

## 生产发布 / GO

前端版本 90d7dc3550b57488642ff561756bb26848e2d409，后端继续为 b49ddb8fb1402c231c00695faa47864cd347347e。本次仅前端发布，没有重启后端、数据库迁移、环境或权限变更。2,850 个干净源码文件核对 Git blob 一致，后端源码与当前生产版本一致；干净构建与 67 项前端回归再次通过。原 12 项无关文件 SHA256 不变。

线上 64 页面、33 关键资源、68 匿名拒绝及 4 匿名应用发现通过；32 项登录态读回、3 份中英文/筛选/空结果 xlsx 生成与内容检查通过，另 5 项导出身份/参数拒绝通过。267 个发布文件一致，7,083 个旧资源保留。152 组业务指纹、存取酒模式、V084（084 / 84 / 0）、环境及所有邻接服务身份不变。后端 PID 2223012 / InvocationID 13e27c5c3e0b4f429589e2bbeaee947e 保持原样，观察窗口无新增 ERROR。临时验收会话已撤销，没有新增存取酒业务。

发布目录 /opt/rpb/releases/20261027-storage-compact-tabs-90d7dc35；备份 /opt/rpb/backups/20261027-storage-compact-tabs-90d7dc35-before，前端备份逐文件比较通过。失败自动恢复旧 index.html 和 FRONTEND_RELEASE_SHA；手工回退同样恢复该备份入口/标记并核验旧前端 b49ddb8f，不操作后端和业务数据库。

证据 target/storage-compact-tabs-release-20261027/{final-check.json,public-check.json,live-readback.json,export-readback.json,source-proof.json,unrelated-final.json,deploy.log}。手机实机及生产浏览器交互未另行验收；窄屏单行/文字宽度证据为本地演示页检查。
