# 第二阶段考勤方式、定位与地址审批契约

全部位于/api/v1/stores/{storeId}/workforce，沿用WorkforceEndpointSupport的登录、private/no-store和X-RPB-Workforce同源写限制。管理使用原employeeManage/managedStores，岗位写仍headOffice；工资授权不授予地址审批。

## 类型

- mode：legacy（旧兼容）、onsite、field、home、free。
- Point：label:string,latitude:number,longitude:number,radiusMeters:number；半径10..1000。
- Location：latitude,longitude,accuracyMeters,capturedAt:ISO instant,description:string；外勤/legacy可省略位置，也可只提交description（其他字段为null），现场/居家必须有完整位置。
- Assignment：id,employeeId,effectiveFrom,mode,origin（position/personal）,positionId,positionVersion,version。
- Site：point:Point|null,version:number（0表示未设置）。
- HomeAddress：id,employeeId,point,effectiveFrom,status（pending/approved/rejected）,version,applicantId,reviewerId,reviewNote,createdAt。
- ClockPolicy：mode,assignmentId,assignmentVersion,point:Point|null,addressId:UUID|null,addressVersion:number,siteVersion:number|null（现场站点版本）；legacy可为null（旧数据兼容）。
- WorkModePage：employeeId,employeeName,canManage,canReview,serverDate,assignments:Assignment[],addresses:HomeAddress[],policy:ClockPolicy,reviewerNames:Record<UUID,string>（可选、只读）。姓名只查询当前租户该员工地址记录的审批账号，停用账号仍可显示；缺失姓名由前端显示“姓名暂不可用”，不回退显示 ID。不改变历史记录和审批命令响应。

## 路由

- GET /work-mode：本人页面；GET /employees/{employeeId}/work-mode：有完整员工管理范围的管理员页面。
- PUT /employees/{employeeId}/work-modes/{id}：{expectedVersion,effectiveFrom,mode,inheritPosition:boolean}。expectedVersion为员工当前策略历史最大版本，首条0；个人变更最早次日，effectiveFrom严格晚于已有最后一条。相同id同内容重放返回原记录，异内容409。inheritPosition时复制当前岗位默认，不动态联动。
- GET /clock-site：员工可读取当前门店站点；PUT /clock-site：管理员{expectedVersion,point}，同内容重试返回旧版本；写入审计。不能以读取站点获得他人居家地址。
- PUT /home-addresses/{id}：本人提交{point,effectiveFrom}（最早次日）。不可修改已有申请，重复同内容重放，异内容409。
- PUT /home-addresses/{id}/decision：管理员{expectedVersion,status:approved|rejected,note}。禁止本人审批，必须具备员工全部门店管理范围；审批和申请不可修改坐标；批准日期不能早于次日，过期申请需重新提交。审计失败回滚，审批幂等。
- 原positions的Position和PositionWrite增加attendanceMode可选属性。旧客户端遗漏时保留原值，明确legacy用于保留旧方式；岗位改名不修改已有员工策略。
- 原GET /clock响应增加policy及每个可打卡排班的策略映射policies；原ClockWrite增加可选location。Session增加可选clockPolicy（IN时固定）；事件增加可选location。旧字段null时不序列化，旧指纹保持兼容。

## 规则及错误

现场及居家所有动作必须上报新鲜且精度合格位置，在服务器计算的范围内。居家没有生效批准地址返回WORKFORCE_HOME_ADDRESS_REQUIRED(409)；现场未设置地点WORKFORCE_CLOCK_SITE_REQUIRED(409)；自由打卡WORKFORCE_CLOCK_EXEMPT(409)；缺位置WORKFORCE_LOCATION_REQUIRED(400)、过期WORKFORCE_LOCATION_STALE(409)、精度WORKFORCE_LOCATION_ACCURACY(409)、范围外WORKFORCE_LOCATION_OUTSIDE(409)。非法坐标/日期/状态400 WORKFORCE_INVALID，版本/载荷冲突409 WORKFORCE_CONFLICT，越权403，missing404沿用原规则。

打卡command重放须匹配location及原action/schedule/breakRuleId，不重新检查已经成功的时间/位置；未知结果重试原载荷，不能换坐标。个人例外/站点/地址均与原sourceWrite锁兼容。自由不伪造工时或人工核准，薪资配置冲突以WORK_MODE_PAYROLL_MISMATCH阻断确认，跨自由类型月份以WORK_MODE_MONTH_SPLIT阻断。

## 数据库V104

岗位追加attendance_mode nullable；新增employee策略历史、门店站点、居家申请、打卡位置证据表，含tenant/employee/store复合FK、日期/版本/枚举约束、时间戳、查询索引。Session追加clock_policy nullable JSON。无旧资料回填、不改旧事实/快照/冻结工资。新增地址数据属于敏感个人资料，接口仅本人或有完整员工管理范围的管理员可见。

## 响应隐私与历史核准

Attendance及ApprovalRow增加可选exempt:true表示免打卡/免核准；不新增伪造批准。看板类别exempt可筛选。考勤列表/详情/事件及核准列表/详情/历史/看板/成功写入与重放响应均采用WorkModePrivacy投影：非本人且不具备员工全部门店管理范围时，隐藏home point/addressId与位置证据，保留原指纹。该投影不改变持久化数据。

无Session的核准来源只保存不可变mode和assignment版本，不包含可变站点/地址几何；有Session时仍使用当次完整快照。工资方式一致性检查限定在发薪公司实际雇佣区间。
