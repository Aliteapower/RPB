# 员工资料及私有证件

本地实现，保留现有授权、数据及已结算工资；不部署，不更改CPF计算规则。

## 员工资料
原GET/PUT `/api/v1/stores/{storeId}/workforce/employees/{id}`增加birthDate（可空）、englishName（选填，100字符）、enabled（默认true）。旧请求未携带扩展字段时保留现值。禁用仅限制新增排班及上班打卡，不修改全局账号、历史出勤、工资或其他产品权限；已开始打卡可正常结束。管理员可修正入职日期，经理沿用既有只读规则；不得晚于已有业务、薪资方案及公司生效日期。无业务的首次公司生效日期可随之修正，初始门店起始日期保持一致，修改审计，不重算工资。

## 证件类型
GET `/document-types`：经AppGate可读租户类型；默认种子身份证、护照、批文，不强制填写员工证件。PUT `/document-types/{id}`：租户管理员维护`{expectedVersion,name,enabled,deleted?}`，名称1..80字符且租户内不区分大小写唯一。无引用可软删；有引用只允许禁用。新增租户由管理员维护类型，不在GET中隐式写数据。

## 员工证件
GET `/employees/{employeeId}/documents`：`{types,items}`；证件含id,typeId,typeName,number,expiresOn,notes,version,attachments。员工仅本人；经理和管理员沿用对该员工全部工作门店的管理权限。所有读取和写入校验tenant、当前store及工作门店关系。禁用员工仍可维护本人证件。

PUT `/employees/{employeeId}/documents/{id}`：`{expectedVersion,typeId,number?,expiresOn?,notes?,deleted?}`；类型必选，其余选填。号码100字符、备注2000字符；有效期为可空截止日期，允许已过期证件。停用类型可保留原值，不能新增选择。版本冲突409，删除保留历史。

PUT `/employees/{employeeId}/documents/{id}/attachments/{fileId}`：multipart `file`及`expectedVersion`，证件版本加1；客户端UUID，重试相同文件返回原附件，内容不同409。每证件最多5附件；图片JPG/PNG/WebP不超过1MiB，前端先压缩；PDF不超过5MiB。后端检查签名及安全文件名，私有bytea存储，不返回公共URL。

GET 同附件路径：先授权，私有下载、no-store、nosniff、Content-Disposition attachment。PUT 同附件路径`/deletion`：`{expectedVersion}`，软删并更新证件版本。

写入沿用X-RPB-Workforce、同源检查、源资料锁、员工锁、版本及审计。审计不记录号码、备注或文件内容，只记录ID、类型、版本与动作。跨租户404，无权限403；文件不合法400 WORKFORCE_DOCUMENT_FILE_INVALID，过大400 WORKFORCE_DOCUMENT_FILE_TOO_LARGE，冲突409。

## 验证清单
字段校验/旧请求/入职修正及历史边界、启禁用排班与打卡、种子幂等、版本/重试、管理员/经理/本人/他人/跨店/跨租户/AppGate、文件签名/1MiB/上限/下载保护、压缩比例及失败提示、前端空加载错误刷新。迁移只在pointer指定的本地验收schema运行。
