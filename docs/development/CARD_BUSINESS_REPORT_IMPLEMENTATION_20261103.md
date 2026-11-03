# 卡业务报表实现（2026-11-03）

## 页面与口径

商户/员工端将“卡业务记录”和“卡业务统计”合并为“报表”，包含业务明细、汇总统计和额度到期。日期使用公共DateInput；可按顾客姓名/手机号、卡方案、业务类型查询，服务端25条/页，显示全量记录数。支持按卡方案或顾客、分别按单位汇总；核销与冲正、集次与撤回分列，并给出期间净发生量，不冒充当前余额。

业务明细按门店时区的实际createdAt筛选及显示，业务归属日期另列；到期区按businessDay筛选，包含有限总次数卡的真实daily_expire流水和不限总次数周期卡的按日计算，不计入消费。实际日结与按日计算标明不同状态；派生行没有独立办理时间和冲正动作。原始ID、source、原单及原因放入详情/审计信息。

购卡发放原始数量可能代表整张卡内的权益数量，报告将非奖励卡grant事件规范成1张卡；原始数量和权益前后值完整保留。奖励数量按商品单位、集次按集次单位，跨单位不相加。售卡实收/退款按实际收退款时间、顾客和方案筛选，仅全部业务汇总时展示，避免把单一核销类别冒充资金筛选。

保留原冲正/撤回命令、原因、权限和幂等流程。报表从数据库全局判断原单是否已被撤回，不依赖当前页或筛选范围；按钮为基础可用性，集次倒序、奖励使用、并发等最终规则仍由原命令服务判定。卡到期不擅自屏蔽原服务允许的历史核销纠错。顾客端原历史接口保留，办理时间按已加载卡片时区格式化（缺失卡片时沿用Asia/Singapore默认），与不同时的业务日期分开标注；新商户报表始终独立使用服务端门店时区。

## 架构 / API / 数据

接口合同见 `docs/api/CARD_BUSINESS_REPORT.md`。新增GET `/api/v1/stores/{storeId}/wallet/cards/reports`，原entries/summary兼容，旧summary增加独立stampReversals字段。报表集成读模型位于wallet/cards/report，拥有参数校验、顾客与卡快照关联及报表投影，不将查询SQL塞入Controller或改写核销服务。

复用CardAccess的wallet.cards.view及现有租户、门店、App Gate/历史权限；完整参数绑定、关键词通配符转义。日期范围最多366天、页大小不超过100，按稳定字段排序，单次响应的分页和汇总在同一repeatable-read事务内。报告通过卡领域CardReportService.prepare门面复用授权与到期结算，冻结一次clock时间；wallet读模型不直接调用卡结算Repository，不能误改为readOnly事务。没有数据库迁移、依赖或权限扩张，既有历史数据不删除、不回写。

现有tenant/store索引用于范围隔离；大规模生产数据的执行计划和压力尚未验收，不把503条样本当成高容量性能证明。

## 验证

- 44项卡业务前端回归通过，覆盖报表筛选、分页、总计、按顾客汇总、历史只读、错误与门店切换、无效草稿切换时清除旧分区结果、原冲正命令及顾客端兼容。
- 45项后端卡业务数据库/服务回归通过，涵盖原核销、集次、冲正、每日额度、并发和新报表；最终报表投影、退卡计量和领域门面另以10项专项复核全部通过。使用target/local-postgres-current.txt指定61644端口、动态独立测试schema，未使用生产业务数据。
- 钱包/积分、卡券、酒存相邻回归421项通过。类型检查、完整前端构建通过；最后文案调整复用类型检查并重新执行Vite构建。真实IAB使用实际CardsStaffPage及报表组件与只读合成API，核验桌面明细、按顾客汇总、到期分区、关键词筛选和第二页；390px中文client/scroll均375、320px英文均305，表格区域独立收缩，不产生页面横向溢出。汇总明确说明净发生量只对当前筛选计算，核对完整期间须选全部业务。

证据：`target/card-business-report-frontend.log`、`target/card-business-report-backend-final.log`、`target/card-business-report-backend-verified.log`、`target/card-business-report-build-final.log`、`target/card-business-report-vite-final.log`、`target/card-business-report-preview/`。实体手机、生产私有账号、真实HTTP鉴权全链路及大数据压力未验收。

## 状态

本次按“修改”请求在本地完成实现与验证，尚未提交、推送或部署。发布须同时包含新后端API与前端；只更新前端会导致reports接口404。回退必须成对恢复前后端版本；无数据库迁移。原有钱包组件、EmployeeStores及未跟踪资料保留。
