# 存酒商品维护与紧凑子入口联合发布

日期：2026-10-27（Asia/Singapore）。用户授权“提交部署，把酒品管理入口和存酒子入口紧凑切换栏一起上线”。

## Production Readiness

GO：本轮8项后端测试、71项前端回归、vue-tsc/Vite构建通过，数据库指针为本工作区61644端口。发布前从功能提交Git archive校验源码并干净构建，不包含原有Wallet组件、验收文档或私有资料。

范围为D:\RPB对应ubuntu@43.134.69.75的/opt/rpb及rpb-backend。新增只读商品分页查询，复用catalog.view与原商品写入权限、幂等和版本校验；存酒子入口改为紧凑单行切换。没有迁移、依赖、环境变量或权限注册变化，原接口保持兼容。Reservation、Queue、Walk-in、Seating、Cleaning模块没有业务变更。

发布前核实后端b49ddb8f、前端90d7dc35，服务active且NRestarts=0，磁盘可用约1.8GiB，现有前端约101MiB、JAR约88MiB，足够独立制品及完整备份。持有既有独占发布锁，备份旧JAR、前端、版本、环境及数据库；校验备份目录后只重启RPB服务。新后端就绪、认证只读查询、业务指纹、匿名拒绝、公开资源及邻接服务身份均须通过，否则自动恢复旧应用。

## 风险与回退

商品名称与全局上下架为租户共享，本店停用影响该商品本店全部渠道，界面明确提示。无数据库结构变更，回退仅恢复本次备份的旧JAR、index和前后端标记，保留新旧哈希资源和所有业务事实，不用旧数据库覆盖新交易。发布不执行真实顾客存取酒或商品写入，认证读取采用既有专用QA租户临时会话并在finally撤销。319px界面夹具检查不等同真机验收。

## 结果

功能提交8ca25c8bb67d01009d9b4cae0dc908356b732d26已推送并同时部署前后端。不可变制品目录为/opt/rpb/releases/20261027-storage-product-management-8ca25c8b；完整备份目录为/opt/rpb/backups/20261027-storage-product-management-8ca25c8b-before，切换边界数据库备份19,973,250字节，pg_restore目录检查通过；本次没有迁移或数据库恢复。

干净Git archive逐一核对2,857个blob，npm ci、71项前端回归、vue-tsc/Vite及Maven package全部成功。与上一版线上JAR相比仅5个catalog类文件改变，依赖、其他模块及历史迁移保持一致。当前工作区8个Wallet组件、3份验收文档及私有资料共12项无关内容SHA256均保持不变，未纳入提交。

线上QA认证只读检查38项通过，包含4项寄存酒商品分页/搜索与状态数据核对、非法参数拒绝、顾客身份及跨店拒绝；存酒清单与3种Excel导出通过。未通过生产接口写入商品或真实顾客存取酒，临时QA会话在finally撤销。

四个生产域名共64个页面入口返回新index，33项公开静态资源字节一致，72项匿名拒绝和4项匿名空目录通过。267个前端制品文件与正式目录一致，7,202个旧资源保留且字节不变。152项业务/平台配置指纹切换前后及最终复核相同，环境文件及邻接Nginx/PostgreSQL/Redis/Docker服务身份不变；该口径排除QA租户、会话、限流和审计等易变数据，不代表整库字节一致。

后端最终active，PID2255987、InvocationID083d8880fda3494386ed5c36c8260dd0、NRestarts0；就绪响应401符合认证契约，观察及最终复核无新增ERROR。Flyway仍084|84|0，前后端SHA均为功能提交。GO，未触发回退；后续发布记录提交不改变线上功能SHA。

制品SHA256：JAR c49be3de7807f0e37fa8206700387bc404dbbf9e8a57dc95c8079f16440d1c2f；前端归档3358c43ef0b31d95ce4ca0b50cf6ec8cf0d1d593185c3cd0fd1e1ab696371188；index 2cc6dd32963f41b0b8524d46fa462d673c9f289f8523e9944cd94a6c097efcca。

本地证据目录target/storage-product-management-release-20261027/，服务器证据/home/ubuntu/storage-product-management-release-20261027/。未执行真实手机或微信实机验收。
