# 酒品简化表单与紧凑设置联合发布

日期：2026-10-27（Asia/Singapore）。用户明确授权将两项变化一起提交部署。

## Production Readiness

GO。77项前端回归、vue-tsc/Vite和diff检查通过。桌面及319px中英文组件夹具验证无横向溢出；新增默认瓶、编辑已有单位不覆盖、折叠保存及验证失败自动展开有回归覆盖。只提交本次表单/设置与说明文件，原12项无关工作区内容保留；提交后从Git archive逐项核对blob，重新安装依赖、回归和构建。

仅更新D:\RPB对应ubuntu@43.134.69.75的/opt/rpb/frontend及FRONTEND_RELEASE_SHA，后端保持8ca25c8b，不重启。无API、数据库迁移、权限、依赖或环境变化。复用原商品保存、版本与幂等逻辑，租户/门店范围及其他产品业务保持原契约。

持有现有独占发布锁，备份完整前端和版本标记，添加新哈希资源后原子替换index，保留旧资源。失败恢复备份index和前端标记，不恢复数据库覆盖新业务。验证公网入口、资源、匿名拒绝、QA商品/清单/Excel导出读取、服务身份、环境和业务指纹；临时QA会话finally撤销，无真实商品或存取酒业务写入。未做真实手机/微信验收。

## 发布结果

功能提交f1cd0b3622499f645a8c1a238a37d0b44bc21be5已推送并部署前端，后端保持8ca25c8bb67d01009d9b4cae0dc908356b732d26且未重启。不可变制品/opt/rpb/releases/20261027-storage-simple-entry-settings-f1cd0b36，完整备份/opt/rpb/backups/20261027-storage-simple-entry-settings-f1cd0b36-before。备份与原前端逐项一致，本轮无数据库备份、迁移或恢复。

干净Git archive核对2,865个blob；npm ci、77项前端回归、vue-tsc/Vite构建成功。12项无关工作区文件SHA256保持不变，未带入提交或制品。前端归档SHA256为6c319dc7f2c6cd331192bff469c9621473bf1e63a73e741071c6b3a7dd96c177，index为abfcb44bf553079ed0493f845af2a69fb01add5dc2b11289d150d66f842b5b24。

四个生产域名共64个页面入口返回新index，33项公开资源逐字节一致；72项匿名拒绝及4项匿名空目录验证通过。38项QA认证只读检查通过，包括商品分页/搜索及访问拒绝、寄存清单和3种Excel导出。临时会话finally撤销，没有通过生产接口新增酒品或办理真实存取酒。

268个前端制品文件与正式目录一致，7,472个旧资源字节保持不变。152项业务及配置指纹在发布前、发布后及最终复核一致，口径排除QA租户、会话、限流及审计等易变数据，不代表整库字节相等。环境文件和后端/Nginx/PostgreSQL/Redis/Docker服务身份不变，Flyway仍084|84|0。后端active，PID2255987、InvocationID083d8880fda3494386ed5c36c8260dd0、NRestarts0；观察及最终复核无新增ERROR。

GO，发布完成且未触发回退。后续发布记录提交不改变线上功能SHA。证据target/storage-simple-entry-settings-release-20261027/；服务器证据/home/ubuntu/storage-simple-entry-settings-release-20261027/。真实手机/微信验收未执行。
