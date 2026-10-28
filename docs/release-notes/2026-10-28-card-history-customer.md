# 卡业务记录顾客信息

2026-10-28，D:\RPB。本地完成，未提交、未生产部署。

## 变更与排查

员工卡业务记录现在显示真实顾客姓名及电话；只有一项时显示现有资料，都缺失时显示“顾客资料不可用”，不显示顾客 ID 作为替代。原页面仅显示交易类型、次数、来源等元数据；原分录接口只有顾客关联 ID，没有姓名或电话。

新增职责独立的 WalletCardEntryQuery，先调用原 CardHoldingService.entries 做权限、范围、日结与分页，再通过现有 CustomerDirectoryQuery.activeByIds 批量补充当前租户本页有效顾客资料，不逐条查询。资料缺失不丢弃历史记录。中文、英文沿用购卡订单的资料缺失提示。

## API 与审查

员工 GET `/api/v1/stores/{storeId}/wallet/cards/entries` 增加可空 customerName、customerPhone，保持原分录字段、customerId/before 过滤、每页 500、wallet.cards.view 权限与门店/租户范围。内部关联 ID 保留在接口用于业务关联，顾客标签不显示 ID；邮箱及认证资料不返回。

顾客端接口、冲正写接口、原业务来源/原单编号保持原样，无迁移、新权限或配置调整。原记录查询会补齐过去日额度，因此新查询层使用正常读写事务，不能声明 readOnly 而禁止原日结写入。预约、排队、Walk-in、就座及清洁无变更。

代码审查：授权发生在顾客资料查询之前，复用已有租户批量查询边界；缺失资料返回 null；JSON 保持扁平；交易记录、分页顺序和冲正逻辑不改动。

## 验证

- 先验证新增显示测试在原页面失败，再实施修改。
- 卡模块 30 项前端测试通过，涵盖姓名/电话、仅电话、仅姓名、缺失/旧响应、过期记录、中英文、顾客端不额外显示员工顾客资料，以及原冲正交互。
- 16 项后端定向测试通过，包括新投影与扁平 JSON、同租户本页批量去重、空页、权限拒绝不查询资料、员工接口顾客/游标参数绑定、匿名拒绝、既有购卡投影及架构检查。
- PostgreSQL 测试使用 `target/local-postgres-current.txt` 指定端口的隔离测试 schema，验证跨租户/归档/已删除资料隔离，以及新查询仍将过去未用额度从 30 结算到 29，只有一笔过期分录。
- `npm run build` 和 `git diff --check` 通过。原有 12 个无关改动文件 SHA256 保持一致。

证据：`target/card-history-customer-ui-red.log`、`card-history-customer-all-ui.log`、`card-history-customer-backend.log`、`card-history-customer-build.log` 及 Surefire XML。未做线上验证。

## 风险与回退

姓名及电话需发布后端新字段后才能显示；旧接口资料缺失时前端明确提示，不回退到 ID。回退应用代码即可，无数据库回退，不删除或重写历史分录。
