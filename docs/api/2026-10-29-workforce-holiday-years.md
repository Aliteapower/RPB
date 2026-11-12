# 年度公共假期及月度店休

公共假期按租户/年度维护一次，所有门店/员工读取共享设置；保留原门店自定义日历记录。管理员写入，经理/员工在原有门店权限内读取。店休仍按门店隔离维护。

GET `/public-holidays/{year}` → `{year,version,items}`（未配置version=0，空列表），年份2000..2100。items包含id,date,name,englishName,observedOn（可空）、observedKind（none/official/agreed）、enabled,tentative。
GET `/public-holiday-templates/{year}`：2026、2027 MOM官方模板及source链接，其他年份404。只读模板不保存、不伪造农历日期。
PUT `/public-holidays/{year}`：`{expectedVersion,items}`，最多100个唯一UUID，原日期须在选定年，名称必填<=100，补休日严格晚于原日期且不超过一年，需明确official/agreed；没有补休时kind=none。整年原子保存、乐观版本与同内容重试；移除行软删。未改行保留自身版本，避免无关历史核准失效。复制前一年仅前端草稿，须确认农历/宗教日期后保存。

GET/PUT `/store-rest-months/{YYYY-MM}`：读取`{month,dates,fingerprint}`；写`{fingerprint,dates}`，最多该月31个日期。按月多选日期，新增店休为“店休”，取消选择仅停用既有店休，保留原名称/历史；不改公共假期或其他门店。日期已符合所请求集合时重试成功，否则过期指纹409。使用现有CalendarService写入及审计。

现有GET `/calendar`及排班board增加年度假期和补休投影：kind=public_holiday，scope=tenant，originDate、observed、year、englishName、tentative元数据。原门店事件新增字段为空并JSON省略，保持旧核准源指纹兼容。周表/月表/排班列表及员工日历显示节日名和补休关联。补假/约定补休均标明，不把周六自动当成全国周一假日。

年度写入取得既有payroll.source共享锁、租户年度锁及所有门店日历锁，和原工时核准及计薪来源读取协调；已核准快照不重写。相关日期变动走原源指纹复核。月度店休只取本店日历锁。无新增角色，不自动重算工资或统一套用2倍；既有特殊日期标记继续供后续计薪核对。

来源：MOM 2026、2027公告和Public holidays entitlement and pay。星期日休息日与星期六非工作日规则不同，实际员工休息日及约定仍需计薪时核实。模板宗教待确认标志保留可编辑。

验证：模板11原假期及关联补假、跨年补休、日期/版本/重复提交、未改行版本、全店可见/门店隔离/角色及AppGate、原JSON兼容、核准源变化、月度多选/取消/过期冲突/回滚、排班三视图及员工日历显示、UI草稿不自动保存。
