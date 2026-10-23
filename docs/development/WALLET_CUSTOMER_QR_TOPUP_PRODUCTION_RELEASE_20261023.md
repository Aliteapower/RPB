# 钱包顾客二维码充值生产发布

2026-10-23，D:\RPB。用户明确授权“提交部署”。

## Production Readiness Report

### Checklist
- 本次仅发布顾客充值码、充值扫码选顾客、用途权限及相关中英文交互。无迁移、配置、依赖、新权限项或其他产品业务变更。
- 复用同代码已核实的 66 项后端、38 项前端测试和构建结果；真实 PostgreSQL 测试使用本工作区 runtime 指针的独立临时 schema。
- 目标 ubuntu@43.134.69.75，/opt/rpb，rpb-backend，8080，数据库 rpb_acceptance。当前运行 ce595f9e911a25c0a9c7fd0ad29f770aa95e64a3，jar SHA256 0b650f50fba939612ed00e91daa21c235ca26c3f3c9b542e822cdca5d7e4b189。
- 当前服务 active，PID 769319，InvocationID 420c18c271754cb789786da4e86f051c，NRestarts 0；可用空间约 18GB。nginx、PostgreSQL、Redis、Docker 为邻接边界，仅记录和核对，不重启。
- 只提交指定文件，保留原有无关验收文档和 rpb信息.txt。从干净 Git 归档核对源码 blob，独立构建，再上传校验制品。
- 切换前备份旧 jar、前端、RELEASE_SHA、数据库，验证 dump 可读；发布后核对业务指纹、Flyway、服务进程与公网资源。

### Blocking Issues
无。实际发布完成及验证前不宣称部署成功。

### Non-blocking Issues
手机摄像头实机验收尚未执行。生产不执行真实充值、扣款、退款或修改顾客密码；使用匿名身份边界、制品一致性和只读核对验证发布。

### Rollback Plan
就绪失败或新进程异常时恢复本次备份的 jar、前端 index 和 RELEASE_SHA，只重启 rpb-backend；保留新旧资源和业务数据库，不覆盖已完成交易。无需逆向迁移。

### Decision
GO，执行提交、推送、备份、部署和验证。实际结果完成后追加。
