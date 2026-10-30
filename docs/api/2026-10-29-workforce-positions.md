# 岗位目录契约

根路径 /api/v1/stores/{storeId}/workforce，沿用workforce.view、AppGate和当前门店访问。

- GET /positions：员工管理员（现有employeeManage）可读租户未删除目录，含停用项。返回数组{id,name,enabled,version}；非员工管理员403。
- PUT /positions/{id}：总部管理员（既有workforce.admin.manage及tenant.admin.manage，非门店经理）维护；请求{expectedVersion,name,enabled}。expectedVersion=0创建，成功200；同值创建重放返回原项；更新也允许上一版本的相同值重放。名称去首尾空格、1..80字符，同租户未删除名称不分大小写唯一。变更审计原子提交。
- PUT /positions/{id}/deletion：请求{expectedVersion}，总部管理员执行未引用岗位软删除，返回{id,deleted:true,version}。相同版本重放可返回原结果；已删除不能复用ID。被任何员工引用409 WORKFORCE_POSITION_IN_USE，须先调岗或停用。
- 员工读写新增可空positionId；jobTitle继续返回当前名称。显式positionId只能引用本租户未删除岗位；停用项只允许原员工保持相同ID。传入的jobTitle不作为ID模式的权威值。旧客户端未传ID时，空文字表示未设置；非空必须匹配目录名称，或保持原员工旧岗位文字。任意未维护值400 WORKFORCE_POSITION_UNAVAILABLE。
- 统一错误：未授权403、跨租户/不存在岗位404、格式400 WORKFORCE_INVALID、旧版本/重名409既有冲突码、停用或未知选择400 WORKFORCE_POSITION_UNAVAILABLE。响应沿用no-store及既有X-RPB-Workforce保护。

V092：新增租户岗位表（复合id/tenant唯一、有效名称唯一索引、版本/时间戳/软删除、启用约束），员工增加同租户复合FK position_id。旧非空岗位按租户及去空白/大小写合并，回填ID但不修改旧job_title、员工版本及任何冻结工资/付款。当前名在查询中join，历史快照保持。空岗位允许null。回退保留表与列；旧程序仍可读取job_title文字，改名不会重写历史。
