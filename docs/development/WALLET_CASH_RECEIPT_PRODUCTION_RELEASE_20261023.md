# 现金充值收据选填生产发布

项目 D:\RPB，2026-10-23（Asia/Singapore）。用户明确授权“提交部署”。

## Production Readiness Report

### Checklist
- 本轮仅普通现金充值收据选填，服务端按充值单生成稳定内部编号，保留资金幂等、权限、审计及实际收款确认；PayNow、信用卡、补付、异常实收仍必填凭证。
- 同码验证：57 项后端通过（含原生 PostgreSQL 48 项资金回归）、5 项前端交互／重试通过、Vue 类型检查和 Vite 构建通过。无需迁移或配置／依赖／权限变更。
- 生产目标 ubuntu@43.134.69.75，rpb-backend、/opt/rpb、8080、rpb_acceptance；旧运行代码 1d61725a57cabc989ab48064184cc93484399d89，jar SHA256 8fe72244db445941aafa853deb1468ff3c166c05fe7f1b96ee3e3517f9326b72。
- 服务 active/running、MainPID 763695、InvocationID e2f9479fb6cf4c9c89a27cdd78bde4bc、NRestarts 0；V058 / 58 成功 / 0 失败，约 18GB 可用空间。
- 只提交本轮文件，保留 rpb信息.txt 和另一份未跟踪验收文档；从提交的干净归档构建，备份原 jar、完整前端、版本标记及数据库并验证 dump 可读，再切换并只读验证。

### Blocking Issues
无实现阻塞；制品、备份及发布后验证完成前不宣称成功。

### Non-blocking Issues
无纸质现金凭证时通过充值单及系统内部编号追溯，仍要求员工确认实际收款。生产不进行真实充值／退款测试。

### Rollback Plan
就绪失败或进程异常时恢复本次备份 jar、前端 index 和 RELEASE_SHA，重启 RPB；保留新旧资源及业务数据库，禁止覆盖已成功交易。无需逆向迁移。

### Decision
GO，按授权执行提交、推送、干净构建、备份和部署，完成后补记结果。
