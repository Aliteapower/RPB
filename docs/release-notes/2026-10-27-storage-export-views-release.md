# Excel 导出及存酒视图合并发布（2026-10-27）

功能版本 b49ddb8fb1402c231c00695faa47864cd347347e，已提交、推送并部署前后端。当前寄存可导出全部筛选结果和人数/剩余瓶数统计；直接登记作为默认流程，顾客待审核单独点击进入，选客后直接显示登记表单。

## Production Readiness / GO

沿用原 wine_storage.view / receive、租户/门店历史边界、存取酒模式及确认规则。没有新增权限、依赖、环境变量、迁移、库存或财务业务写入。先前同一实现的 18 项后端检查证据有效；干净提交再次通过构建、67 项前端回归和打包。2,847 个源码文件与 Git blob 一致，23 个功能文件单独提交；JAR 比对只有 Excel 导出、共用过滤和本地 GET 路由相关的 6 个类变化，其余类/资源一致。原 12 项无关文件 SHA256 不变。

## 生产验收

- 真实二进制 xlsx 下载验证 3 份：中文全部 3 条、英文按酒名过滤 2 条、中文过期空结果 0 条。两表名称、人数/瓶数/开瓶/过期统计、明细字段及门店时区日期均与同筛选接口相符；ZIP 校验、MIME、attachment、private/no-store 和无公式单元格检查通过。
- 导出匿名、顾客、跨店拒绝及两种非法参数共 5 项通过。临时验收会话已撤销，验收只读业务；没有新增收存、取酒、财务或模式设置记录。
- 32 项登录态读回通过，含 3 条在店库存（验收租户 1 位顾客、4 瓶）的字段/汇总/搜索/分页；原 42 条操作者姓名和钱包/次数卡读回不变。
- 四域名 64 页面、33 个关键资源、68 匿名拒绝及 4 匿名应用发现通过，包括 task=inbox、inventory/export。
- 前后端发布标记一致，267 个发布文件与线上逐一相同，6,963 个旧静态资源保留。152 组业务指纹与发布前一致；数据库 V084（084 / 84 / 0）、模式、环境文件及 nginx/PostgreSQL/Redis/Docker 身份不变。
- rpb-backend active，PID 2223012，InvocationID 13e27c5c3e0b4f429589e2bbeaee947e，NRestarts=0，新进程无 ERROR。

存酒视图的默认/审核互斥、注册和审核/拒绝命令保留由 67 项前端回归及本地浏览器演示页确认；生产验证静态资源/路由一致性。未另做生产浏览器实际点击下载和手机实机验收。

## 备份和回退

不可变发布 /opt/rpb/releases/20261027-storage-export-views-b49ddb8f；备份 /opt/rpb/backups/20261027-storage-export-views-b49ddb8f-before，含旧 JAR、前端、两个版本标记、环境文件及数据库。切换时数据库备份 19,972,280 字节，pg_restore -l 可解析；同 V084 完整恢复演练复用此前已验证证据。自动回退已准备，发布检查均通过无需触发。

回退时恢复备份 JAR、前端入口和两个版本标记，启动 rpb-backend 后验证旧版 53b7c094、就绪 401、原业务指纹及邻接服务；保留数据库业务数据，不回退数据库。

证据 target/storage-export-views-release-20261027/{final-check.json,export-readback.json,inventory-readback.json,live-readback.json,public-check.json,source-proof.json,jar-differences.json,unrelated-final.json,deploy.log}。生成的验收 xlsx 仅为专用 QA 租户数据，未导出真实商户业务到本地。
