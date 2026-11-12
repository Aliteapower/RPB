# 第四阶段薪资接口契约

沿用/api/v1/stores/{storeId}/workforce，原登录、同源写头、private/no-store、AppGate及完整公司薪资授权。

## 计算

原actions/{commandId}新增action=calculate_full，原calculate改为安全增量；confirm仍全量校验。成功回执及旧DTO/历史JSON不变。全量回退正常返回，不报错；来源/版本/权限/繁忙错误沿用409/403/503。失败无部分工资写入。

## GET /payroll-batches/{id}/changes

ChangeReport={batchId,batchVersion,status,sourceChanged,lastCalculation,previousMonth,pending,recalculated,reused}。
Comparison={available,baselineMonth,baselineVersion,totalGrossDelta,totalNetDelta,totalEmployerCostDelta,lines,reasonBasis?}。
LineChange={employeeId,employeeName,kind,reasons,basicPayDelta,grossPayDelta,netPayDelta,employeeCpfDelta,employerCostDelta}；kind=added/removed/changed，reasons=employee/profile/input/approval/attendance/leave/issues/policy/source/added/removed/legacy_comparison。差值用十进制字符串，未具备对比双方时Comparison.available=false且不伪造差值。
比较理由只表示有保留证据支持的事实：input仅比较双方持久化输入version；policy比较双方完整政策或可核实UUID/vN身份，相同身份只补齐政策对象不算变更；approval比较核准version或双方证据可知的有效性；attendance比较双方SourceEvidence中的actualSeconds/startAt/endAt；leave仅比较双方current有效核准的请假秒数。自动生成单位、stale零占位、旧来源证据补全不能当人工输入/实际出勤事件，无法定位用source。

新比较reasonBasis=snapshot_evidence_v1，保存的lastCalculation可识别其原因准则；紧邻上月比较现场使用相同准则。旧已存比较没有完整before证据且无该标记时，读取投影reasonBasis=legacy_unverified，各行reasons仅legacy_comparison，UI显示“历史比较结果（缺少完整前态，原因未验证）”；保留kind和全部原差额。不把旧结果维度当政策、人工输入、出勤事件，不回写旧比较/工资JSON。不新增写接口或迁移；历史权限仍先校验再投影。reasonBasis是可选新增字段，旧JSON缺失可读；未来旧服务若不识别优化缓存，可按既有不兼容缓存全量回退，不能重写冻结工资。

PendingChange={employeeId,employeeName,reason}；reason=source/global/added/removed/baseline_unavailable。pending表示当前来源需要重算，不能把上次工资当实时工资。recalculated/reused为最近一次计算统计。

本月lastCalculation比较最近两次计算；previousMonth只取同公司紧邻上月已确认/已发布批次。已冻结月份只做投影，不写缓存或工资。比较和回执涉及的历史来源门店必须再次授权，否则403，不降级返回敏感历史。

## PUT /payroll-batch-operations/{commandId}

BulkWrite={action,note,entries:[{batchId,expectedVersion}]}。action=calculate/calculate_full/confirm，note必填1..500，entries 1..20且批次不重复，expectedVersion>=1。
BulkResult={commandId,action,results:[{batchId,status,code,batch}]}；status=success/failed，success.code为空，success.batch为原BatchSummary，失败batch=null且code为既有业务错误。部分失败HTTP200，非法整体请求400，命令换actor/store/payload409。未知服务异常中断HTTP500，原命令可恢复；已完成子动作通过确定性子commandId重放。批量完成结果持久化，同命令重试返回原结果，重新鉴权成功行；不以新命令重复提交未知结果。

每批独立提交，不宣称跨批事务。确认继承月底、无异常、来源一致和本人不可确认；不新增工资权限，不包含发布/付款/作废。

并发恢复：同一命令使用专用非池化PostgreSQL会话锁，只保护该命令，不持有来源或工资批次锁。每实例最多两个在途批量操作，忙时503、保留原命令恢复。V105新增两张证据表并扩充原命令表动作约束以允许calculate_full；不重写任何旧工资或回执。未来部署需迁移，当前仅隔离测试schema验证。
