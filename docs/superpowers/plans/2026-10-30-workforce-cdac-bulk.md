# CDAC批量生效方案实施

1. 复用PayrollAccess、PayrollConfigurationRepository与薪资方案快照；独立CdacBulkService编排预览与原子保存，CdacBulkRepository仅保存命令及读取冻结边界，不把批量逻辑塞进单人维护服务。
2. V103添加可重试命令存储，保留历史方案及工资，使用已有源锁协调确认。
3. 新增PostgreSQL行为测试覆盖合同清单，先红再实现；Vue列表复用现有弹窗样式增加筛选多选、月份、依据和影响预览，保存刷新原列表。
4. 跑相关后端、前端、构建，本地独立schema API/浏览器验证；记录审查和证据，不发布。
