# 打卡许可多选与实际方式契约

沿用 `/api/v1/stores/{storeId}/workforce` 全部既有路由、App Gate、sourceWrite、本人打卡、完整员工管理范围及岗位 headOffice 权限。没有新增公开接口。

## DTO

- `Position` / `PositionWrite` 增加可选 `attendanceModes: string[]`；`attendanceMode` 仍为默认方式。
- `Assignment` / `AssignmentWrite` 增加可选 `allowedModes: string[]`；`mode` 仍为默认方式，`inheritPosition` 语义不变。
- `ClockPolicy` 增加可选 `options: ClockOption[]`，每项 `{mode,point,addressId,addressVersion,siteVersion}`。主字段保留默认方式及其定位；options 包含班次全部许可及定位快照。旧策略缺 options 只允许原 mode；空许可、重复/未知项、默认不在集合、free/legacy 与其他方式混用均 400 `WORKFORCE_INVALID`。
- `ClockWrite` 增加可选 `mode: string`；缺省采用策略默认，不能从 location 或当前页面选项推断。非法值 400；未授权选择 403 `WORKFORCE_CLOCK_MODE_FORBIDDEN`。
- `Event` 增加可选 `clockMode: string`，记录该事件实际方式。`location.description` 继续接受可选至多 200 字符的外勤地点／客户名称；单独名称不要求 GPS，也不作为定位证据。`occurredAt` 始终由服务器生成。事件 mode 可展示，location 仍按原隐私权限整体遮蔽。
- 所有新增 nullable 字段采用局部 NON_NULL，保留旧 Java 构造器，未设置的新字段不改变旧 JSON 形状。

## 写入、无变化与重试

岗位缺 attendanceModes：默认未改变时保留原多选；默认改变视为旧客户端明确改成该方式单选。新岗位缺字段仍兼容原 nullable 默认。

个人许可缺 allowedModes：默认与最后记录相同时保留原多选；默认改变视为明确单选。继承始终读取当前岗位；提供 allowedModes 时必须与岗位实际许可一致，否则 400，不能伪造继承覆盖。旧继承请求的 mode 仍忽略。

员工策略 PUT 的路径 id 是命令 ID。事务中先校验权限并锁员工；已有新回执须精确匹配 tenant/store/actor/employee/原请求（包括 expectedVersion、effectiveFrom、mode、inheritPosition、allowedModes），返回固定结果。原命令不同载荷或身份 409 `WORKFORCE_CONFLICT`。原 V104 已有命令按旧格式精确语义重放，不扩展其许可。

首次命令先验证 expectedVersion 与最后策略版本相等，再判断实质许可集合、默认、来源及 positionId。相同即返回最后 Assignment（id 可能不同于本次命令 ID），不新增策略、版本或审计，但保存不可变命令回执。因此无变化结果仍可在以后策略变化之后精确重试。仅岗位名称/version 变化不构成实质变化。无变化仍校验最早次日/入职日期；严格晚于最后策略日期仅用于实际新增记录。过期版本始终 409，不能靠无变化绕过。实质变化保持原未来日期顺序约束。

打卡命令按原 schedule/action/breakRuleId/location 加原请求 mode 精确重放；null 与显式 mode 不互换。IN 保存完整固定许可；之后选择只能来自该 Session 原始许可。实际 mode 只记事件证据，不改 Session 原 IN 策略、不新增 Session、不重复计工时。旧 Session 缺策略只允许 legacy。

## 定位、隐私与居家错误

onsite/home 继续验证新鲜定位、精度和半径；缺站点/地址不回退外勤。field 沿用可不报坐标规则。free 仍 409 `WORKFORCE_CLOCK_EXEMPT`，不改变全额工资守卫。

隐私投影清除主 home 策略及所有 options 内 home 的 point/addressId/addressVersion，涵盖 Session、核准 source 和历史。主 mode 为 onsite/field 也执行。未打卡核准来源仅含不可变许可，不读取可变几何。

- 400 `WORKFORCE_HOME_EFFECTIVE_DATE_INVALID`：新居家申请早于次日（入职等其他错误仍原 INVALID）。
- 409 `WORKFORCE_HOME_REQUEST_EXPIRED`：审批时申请原生效日期早于次日，必须由本人重新申请，不能修改原申请日期。
- 409 `WORKFORCE_HOME_EFFECTIVE_DATE_CONFLICT`：批准同一员工同一日期的第二个地址；事务内锁员工检查。
- 其他既有定位、状态、版本和权限错误保持原契约。

## 数据库

V113 仅添加 nullable 岗位/员工许可数组、事件实际方式和原请求方式、员工策略命令回执表；复合 tenant/store/employee FK、许可约束及唯一命令键。无旧记录回填、不修改 V001–V112、不改变工资/核准历史。回执在源事务内写入，审计失败时一并回滚。
