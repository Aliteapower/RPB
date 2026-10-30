# 员工证件日期与OCR契约

沿用 /api/v1/stores/{storeId}/workforce、员工本人/管理员/覆盖员工全部门店的经理权限及AppGate、private/no-store、同源写标记。审批资料识别不增加账号权限，不对外发送文件。

Document和Write增加holderName（最多100字）、approvedOn（批准日期，可空）、prEffectiveOn（正式PR取得日期，可空）。原到期日expiresOn不变；批准日及PR取得日期如都有到期日不能晚于到期日。旧客户端完全省略新增元数据时保留原值；新版保存/删除携带holderName字符串及两个日期（可空），允许明确清空。姓名只存证件，不改员工主档。

PUT /employees/{employee}/documents/{id}/attachments/{fileId}/recognition body={expectedVersion:long}，沿当前证件版本判断；返回{documentId,attachmentId,documentVersion,name,number,approvedOn,expiresOn,prDate,warnings}。未识别字段null，不返回原始OCR全文；候选未确认不写入证件。503 WORKFORCE_DOCUMENT_OCR_UNAVAILABLE、400 WORKFORCE_DOCUMENT_OCR_UNSUPPORTED、429 WORKFORCE_DOCUMENT_OCR_BUSY、409 WORKFORCE_CONFLICT。识别前后检查权限、版本及文件是否仍有效。上传与识别独立，OCR失败仍保留上传文件，可手工填写或重识别。

JPG/PNG/WebP本地OCR，PDF本地渲染前2页，warnings含PDF_FIRST_TWO_PAGES_ONLY。用户日期标签不明确、日期歧义或互相冲突时不能猜测。普通DATE OF ISSUE不是批准日期，IPA或REP不是首次取得PR日期；只有正式Entry Permit上下文明确日期才给prDate候选。

ProfilePage新增employeePrDocuments:Record<employeeId,PrDocument[]>，仅原发薪公司授权范围、未删除且已填写prEffectiveOn的证件元数据；PrDocument={id,typeName,number,approvedOn,prEffectiveOn,holderName}。number仅返回脱敏尾号，不得包含完整证件号、附件或OCR原文。

CpfProfile增加可空prDocumentId。仅SPR可以引用，保存时证件须属于同租户同员工、未删除，prDate须等于prEffectiveOn；UI要求确认正式Entry Permit。批准日期不用于PR年限，SDL规则不受日期驱动。来源证件日期修改或删除后，计薪产生CPF_DOCUMENT_REVIEW_REQUIRED并阻止最终确认，不自动用员工修改后的日期改工资；工资管理员核对并维护薪资方案。旧手动PR日期可继续用，已确认工资保留快照。

## 附件对照展示（2026-10-29）

预览复用原GET /employees/{employee}/documents/{id}/attachments/{fileId}，不改Content-Disposition、鉴权和缓存响应。前端以same-origin/no-store获取Blob，只允许JPEG/PNG/WebP/PDF并在切换/关闭时回收对象URL，不创建公开图片地址。新增表单上传使用原PUT证件空命令（类型、同一UUID）后上传附件，个人字段不提前写入；取消编辑保留上传文件。OCR修正值明确选择填入后仍须原版本化PUT保存。
