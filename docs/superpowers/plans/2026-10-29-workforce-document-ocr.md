# 员工证件批准日期、OCR及CPF引用

目标：沿现有证件模块增加持有人姓名、批准日期及明确区分的正式PR取得日期；上传后本地OCR给出候选字段，人工核对后保存；工资管理员可以明确选择正式PR证件日期作CPF依据。保留权限、数据、图片1MB限制，不部署、不提交。

## 设计与接口

采用现有本地Tesseract能力，不复用付款领域解析器、不将证件发送第三方。识别只返回候选，不写员工主档、不自动改变身份或工资。普通批准日期/IPA/护照签发日不等于PR取得日期；PR日期仅正式Entry Permit标识或用户明确核对。SDL保持工资基数计算，不按批准日期开关。

Document/Write追加holderName:string、approvedOn:LocalDate|null、prEffectiveOn:LocalDate|null，旧构造器和旧客户端兼容；V100新增列及长度/日期先后约束，旧数据不回填猜测。

PUT /api/v1/stores/{storeId}/workforce/employees/{employee}/documents/{id}/attachments/{fileId}/recognition，body={expectedVersion}，沿员工本人/管理员/覆盖全部员工门店的经理权限。返回{documentId,attachmentId,documentVersion,name,number,approvedOn,expiresOn,prDate,warnings}，候选缺失为null。查授权及文件存在后才运行OCR，完成后复查版本，旧版本409；异常WORKFORCE_DOCUMENT_OCR_UNAVAILABLE(503)、WORKFORCE_DOCUMENT_OCR_UNSUPPORTED(400)、WORKFORCE_DOCUMENT_OCR_BUSY(429)。原附件上传独立成功，OCR失败不撤销上传或阻止手工填写。保留只读历史文件访问规则及同源保护。

ProfilePage追加employeePrDocuments映射（授权工资公司内、仅提供已填PR取得日期的有效证件元数据，不提供附件）。CpfProfile追加可空prDocumentId，旧五参数构造器兼容。引用时确认是正式Entry Permit；保存薪资方案时后端验证租户/员工/证件未删除和请求prDate等于prEffectiveOn。source捕获该引用的当前日期，变化或删除产生CPF_DOCUMENT_REVIEW_REQUIRED，阻止确认，禁止员工通过改证件直接改计薪。已确认工资原快照保持，普通证件批准日期不用于PR年限。

## 实施与测试清单

- [x] 证件DTO/仓储/服务/迁移：旧客户端、批准先于有效期、日期清空/重试、审计回滚、权限和跨店隔离。
- [x] 本地OCR解析及受限执行器：中英文/多种确定日期、PR与IPA/REP区别、低置信/缺失/歧义日期不猜、超时/并发/清理、无日志泄露、真实合成图片识别。
- [x] 证件UI：批准/持有人/正式PR日期维护；上传后建议核对；选择填入而非静默覆盖；失败保留附件；版本/切店竞态；原字段/删除传递保留。
- [x] 工资引用：公司授权后的候选、仅SPR可选、明确确认、日期变化需复核、手动旧方案兼容、原快照不变。
- [x] 定向Maven/真实PostgreSQL、前端真实组件测试、构建、自有schema迁移与本地浏览器验证、原数据逐行哈希。

PDF如需要独立解码能力应使用受限本地渲染，不将文件发送在线识别服务；明确支持范围、页数限制和失败后的手工路径。接口先于实现冻结；UI、OCR执行/解析与工资引用各有独立职责，不新增独立业务首页。
