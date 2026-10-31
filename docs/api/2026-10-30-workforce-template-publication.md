# 工资模板发布与门店采用（阶段1，2026-10-30）

> 阶段演进说明：本文保留阶段1发布/采用契约及当时边界，不能单独视为当前完整流程。未来采用的currentAt/effective_from规则见[阶段2](2026-10-30-workforce-pay-changes.md)，经济写入口见[阶段3](2026-10-30-workforce-effective-pay-governance.md)，有本店修改时的显式升级及冲突见[阶段4](2026-10-30-workforce-bulk-upgrade.md)；阶段1的“后续尚无审批/当前取最大storeVersion”描述已由这些增量契约取代。

基础路径 `/api/v1/stores/{storeId}/workforce`。所有写操作 PUT，要求 `X-RPB-Workforce: 1` 和同源；响应 private,no-store。复用 workforce.view AppGate，不新增工资授权。总部接口额外要求 WorkforceAccess.headOffice；目录、预览、启用要求当前活动门店、当前活动公司、actor 门店权与 WorkforceAccess.manage。

| 方法/路径 | 请求 | 响应 |
|---|---|---|
| GET /pay-template-masters | 无 | Draft[]，总部草稿 |
| GET /pay-template-publication-directory | 无 | PublicationDirectory(companies,stores)，总部发布专用合法活动目录，含无门店公司；不扩大PayrollAccess |
| PUT /pay-template-masters/{id} | DraftWrite | Draft |
| PUT /pay-template-masters/{id}/publish | Publish(commandId,expectedDraftVersion) | Release，不可变 |
| GET /pay-template-library | 无 | LibraryEntry[]，仅当前门店合法可见发布 |
| GET /pay-template-library/{id}/preview?releaseVersion=N | 固定母版版本 | Preview(release,adoption,updateAvailable) |
| PUT /pay-template-library/{id}/enable | Enable(commandId,releaseVersion,expectedAdoptionVersion) | Adoption |

DraftWrite: expectedVersion、name、rule（原 PayrollDtos.Rule，原校验标准化）、companyIds、storeIds、includeFutureStores。Draft 含 id/version、其余请求字段。公司及所选门店均须本租户合法活动记录，门店公司属于 companyIds；总部仍须覆盖 scope 对应门店权与 AppGate。非活动旧门店也计入发布时既有集合。

编辑或重放已有草稿前须独立授权旧scope（原选中门店及原公司当前全部门店均须actor可访问），再核验新scope的active/归属合法性。旧scope授权不要求原门店仍active或仍属原公司；真实认证会从principal过滤inactive门店，故inactive旧店可由同tenant/actor仍有效的服务器account_store_access证明原授权，活动旧门店仍要求access.enter重检actor.canAccessStore与AppGate，以便有权总部修复停用/转公司后的草稿；无权者不能靠收窄新scope夺取原母版。草稿列表按旧scope授权过滤；发布仍要求目标scope当前合法。发布目录只供合法总部：公司须租户活动且未停用，现有活动门店须actor/AppGate合法；无门店公司的合法目录不依赖工资公司列表。编辑器保留目录外已选公司/门店的失效提示与取消控件，保存不会静默删除这些选择，须显式修复后提交。

Release: masterId、version（母版独立递增）、draftVersion、name、rule、companyIds、storeIds、includeFutureStores、existingStoreIds（发布时含 inactive 的全部既有门店集合）、publishedAt（数据库 clock_timestamp）、publishedBy。未来门店须未在发布事务可见的完整 existingStoreIds 集合、当前公司匹配；created_at 与 publishedAt 保留审计，不双重时间门槛以免漏掉晚提交新店；可见不代表启用。

Adoption: id、masterId、releaseVersion、companyId、storeId、storeVersion、materializedTemplateId、name、rule、enabledBy、enabledAt。采用快照固定，materializedTemplateId 对应独立旧表模板（version=1），不写个人方案、岗位映射或 batch。一个门店母版的最新 storeVersion 是当前采用；旧采用及物化模板保留且不可改。后续阶段以 tenant/store/company/adoptionId 精确解析，不能只凭 materializedTemplateId 或共享岗位映射扩大作用域。

LibraryEntry: masterId、name、latestReleaseVersion、adoption（当前可用采用或 null）、state（available/enabled/updateAvailable）。Preview 明确完整固定发布、当前采用和 updateAvailable；新发布不覆盖旧采用。启用必须带预览时 expectedAdoptionVersion（未采用为0），母版升级需再次显式预览/启用。重复启用相同当前版本不复制，commandId 绑定 tenant/actor/store/action/规范化 payload，精确重放返回原响应，改 actor/payload/动作冲突409；重放前重新核验当前权限及范围。

错误：401 WORKFORCE_UNAUTHENTICATED；403 WORKFORCE_FORBIDDEN 或原 AppGate deny code；404 WORKFORCE_NOT_FOUND；400 WORKFORCE_INVALID；409 WORKFORCE_CONFLICT（过期版本/命令冲突）、WORKFORCE_MANAGED_TEMPLATE_IMMUTABLE（旧入口改受管物化模板）。不增加宽泛 permitAll。旧 salary-templates 仅返回 legacy 公司共享模板和本店当前有效采用的物化模板；其他店派生ID/旧采用ID用于新个人选择、岗位预览/保存明确拒绝。既有同模板个人方案的本店历史来源保留维护和精确重放，不强制升级；不能借此换成别店来源。岗位 legacy 映射仍共享。

SalaryTemplate 追加可选 governanceSource(adoptionId,storeId,masterId,releaseVersion)，仅新受管物化记录带该字段，原六参Java构造与旧JSON可读；旧模板页面据此展示固定版本只读。门店转公司后旧采用不再是当前合法采用：Preview.adoption 为 null、expectedAdoptionVersion 用0，新显式启用在原storeVersion之后递增，产生新公司物化模板，不复用旧公司id。精确历史 adoption 查找始终要求原tenant/store/company/id。没有门店的合法活动公司也能列入未来范围，总部仍在实际读取/启用时重检新增门店权。

阶段边界：尚无派生申请、金额审批或统一经济写守卫；个人保存保持既有行为，仅增加受管模板隔离。V108 仅新增发布/采用/命令证据，不改历史 JSON、工资、付款、法定计算。后续阶段必须使用服务器固定 release/adoption 快照，不能将母版草稿或客户端 approvalId 当证明。
