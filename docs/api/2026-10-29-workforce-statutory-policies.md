# CPF/SDL政策配置

基址/api/v1/stores/{storeId}/workforce，沿用AppGate、当前店访问、headOffice总部管理权限及租户全部门店访问／AppGate检查、同源写保护、private/no-store。

GET /statutory-policies→{items:[Policy],headOffice}；GET /statutory-policies/{id}/versions→Policy[]；GET /statutory-policy-templates/{year}支持2026/2027官方模板，其他404。Policy={id,name,year,currency,effectiveFrom,effectiveTo,enabled,version,config}。PUT /statutory-policies/{id} body={expectedVersion,name,year,currency,effectiveFrom,effectiveTo,enabled,config}，同内容重试返回原版，变更附加不可变revision并更新header版本；禁止启用政策区间重叠(409 WORKFORCE_STATUTORY_OVERLAP)。无硬删。日期为同年完整月份边界，2000..2100。

config={schemaVersion:1,owCeiling:string,ageBands:[{code,name,upperAge:number|null}],wageBands:[{code,name,upperWage:string|null,method:none/employer_only/ramp/normal,rampMultiplier:string}],tables:[{code:T1..T5,name,rates:[{ageCode,employerRate:string,employeeRate:string}]}],sdl:{rate,minimum,maximum},totalCpfRounding,employeeCpfRounding,companySdlRounding,cpfSource,sdlSource,ageTiming:next_month,prTiming:next_month,notes:string}。舍入HALF_UP/FLOOR/CEILING；金额比率均十进制字符串，0.17=17%。年龄/工资档按上界递增、末档null，5表完整覆盖全部年龄code。工资四种公式为内置安全方法，ramp从前档上界开始按rate×multiplier增长，不接受脚本表达式。

本轮工资账本仍仅SGD和OW，拒绝非SGD，不以保存字段暗示支持多币；月中取得SPR、AW/AWS及跨期补缴继续明确不支持。未来常规费率、档次、上限与SDL调整可保存为新修订/生效月份，不改代码或重写历史。

工资capture按月读取当前租户唯一enabled政策；无配置生成STATUTORY_POLICY_MISSING阻断，不能回退硬编码。PayrollSnapshot追加可空statutoryPolicy完整引用快照(id/version/name/year/currency/effectiveFrom/To/config)，旧构造器/JSON兼容。来源指纹始终包含政策完整引用，新配置建立或修订后待确认工资需重算；已确认/发布单据金额和引用快照保留。

ProfilePage追加employeeBirthDates map，仅原工资公司授权范围；员工档案生日优先匹配月份年龄，生日当月沿原档、次月生效。没有档案DOB时保留旧个人CPF资料兼容，修改员工DOB会使后续待计算来源变化，不改已锁定快照。
