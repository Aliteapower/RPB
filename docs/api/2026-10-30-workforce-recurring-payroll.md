# 第一阶段长期薪资与自动生成契约

沿用原store-scoped salary-templates、pay-profiles、payroll-batches及inputs/actions的GET/PUT路径、AppGate和公司薪资权限，未新增路由或本地白名单。

Rule新增可选generation={mode,workingWeekdays}：mode为manual、attendance或fixed；缺省manual保持旧行为。workingWeekdays用ISO星期1..7，不能重复；attendance月薪必须显式选至少一天。fixed仅monthly且overtimeEnabled=false。个人方案按effectiveFrom跨月持续生效，无需每月新建。

Item新增可选proration=full或payable；缺省为旧规则（固定月薪收入按计薪比例，其他全额）。proration只适用于长期收入项目，月度调整始终全额，不能用它改变扣缴基数分类。

LineInput响应增加可选generation（fixed或attendance），标记后端计算的单位/说明；请求不能伪造此字段。自动模式输入仅保留月度items、note、specialDaysReviewed及cdacReview；不接受手工单位覆盖生成规则。生成时携带原输入version。

创建批次：存在自动方案时捕获一致来源并立即保存calculated预览；缺资料以issues返回，不自动confirm/publish。全旧方案保持draft。相同批次ID创建重试返回现有状态，不再生成。旧动作版本、commandId重放、来源指纹和月底确认规则不变。

自由模式忽略考勤核准和请假折算，全额基本工资，无自动加班；仅当该公司该月雇佣区间非空才纳入。来源变化校验仍覆盖员工、方案、公司和政策；不修改原出勤。普通自动模式缺排班/特殊日/缺核准/来源过期均不能绕过。

新增issues：AUTO_SCHEDULE_MISSING、AUTO_UNEXPECTED_DAY、AUTO_UNITS_INVALID，均阻断确认；前端提供中文/英文说明。非法规则400使用原WORKFORCE_INVALID；版本/来源冲突409、越权403沿用原错误。旧JSON新属性为null时不序列化，不回填历史。新规则保存使用原审计事务；无数据库迁移。

## 第三阶段计算事务与重试

原路径、DTO、权限、状态和金额口径不变。创建自动工资预览及calculate/confirm先捕获一致只读快照，事务外纯计算，再短写事务重验权限、批次版本及完整来源。期间的补卡、审批、调薪或月度输入变化不能被旧结果覆盖：来源变化使用既有WORKFORCE_PAYROLL_SOURCE_CHANGED/409，批次版本变化使用WORKFORCE_CONFLICT/409，调用方需刷新和重新计算。失败不留下新工资、审计或成功命令回执。

计算池容量不足返回WORKFORCE_PAYROLL_BUSY/503，前端提示稍后重试或恢复本次操作，并保留原命令ID。此时无写入；成功命令重试仍返回原响应，不因当前批次版本变化而重复计算或记薪。输入payload冲突仍拒绝。确认使用复算校验问题但保存原工资快照（含calculatedAt），发布/付款历史不受影响。无新API路由、权限、白名单或迁移。
