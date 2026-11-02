# 酒水寄存不限时间：验证与审查

发布补记（2026-11-02）：已随功能提交`26947810`推送并发布生产，V126。见[实际发布记录](WINE_STORAGE_PRODUCTION_RELEASE_20261102.md)；以下保留实现阶段的验证说明。

日期：2026-11-02；项目 D:/RPB；本地工作区实现，未提交、未发布生产。

## 测试清单与结果

| 场景 | 证据 | 结果 |
|---|---|---|
| 0月份无到期日、有限月份月末计算、到期边界、数量/开瓶限制 | StorageRulesTest | 3项通过 |
| 显式0整数及缺失/null/小数/字符串 | StorageJsonTest | 8项通过 |
| 有限→不限时间→有限版本，历史期限/快照、远期库存及取酒、幂等、跨租户 | StoragePostgresTest.unlimitedRuleRetainsHistoryAndSupportsInventoryAndDelivery | 通过 |
| 权限、App Gate、门店及顾客归属、重试、并发扣减、授权超时回归 | Storage*Test | 后端合计112项通过，0失败/错误/跳过 |
| 中英文不限时间 Excel 到期列及原导出行为 | StorageInventoryWorkbookTest | 3项通过 |
| 双语规则提交、月数草稿切换、不限时间可取酒筛选、数量和顾客范围、顾客与库存页面 | wine-storage*.test.mjs | 前端合计91项通过 |
| Vue/TypeScript及生产资源构建 | npm run build | 通过 |
| 本次文件空白检查 | git diff --check（限定本次路径） | 通过 |

测试先补期限和数据库场景，再实现链路，其余测试伴随实现补充。测试日志位于 target/wine-unlimited-{backend,frontend,build}.log。

## 本地环境处理

数据库用例首次因127.0.0.1:61644拒绝连接失败；读取 target/local-postgres-current.txt 及 LOCAL_RUNTIME_QUICK_RESTART_GUIDE.md 后确认目标为 D:/RPB/target/local-postgres-20260623203420。使用原数据目录启动 PostgreSQL，日志完成自动恢复且 ready to accept connections 后重跑。未使用系统5432服务，未创建替代集群。测试在独立随机schema执行所有迁移并自行清理，未改写实际业务规则/库存。新增导出测试初次编译发生 Row 类型重名，限定 StorageInventoryDtos.Row 后重跑通过。

## API Review

沿用原 /api/v1/.../wine-storage/rules、权限、StoreScope及持久幂等命令。months=0为显式不限时间，1–120兼容；Integer请求字段及严格整数反序列化防止缺失字段变为不限时间。物品及库存expiresAt可空，前端同步处理；授权码到期字段和5分钟时效不变。契约已更新 docs/api/WINE_STORAGE_API.md。

## Database Review

V126仅将月份检查扩展为0–120、放宽物品expires_at NOT NULL。月份仍非空，原版本唯一约束、租户/门店复合外键及索引保留。无历史数据UPDATE。新物品冻结所选最新版本，空到期日参与stored库存筛选、不计入expired。不使用远期假日期。完整迁移链在上述112项测试中通过。已有空到期日后不能直接回退旧后端/恢复NOT NULL。

## Code Review

本次变更限于酒水寄存模块、测试、API契约、迁移及说明。控制器/权限/事务/模块边界不变；没有改动钱包或员工模块已有工作。检查了规则快照、到期判断、原子扣减、库存统计和导出消费者；未发现阻塞问题。结论：APPROVE_WITH_COMMENTS（以下视觉范围未验证）。

## UI与交付限制

参照 MerchantStorageModeSettings.vue，复用 storage-panel、storage-customer-fields 和原设置网格及窄屏规则；没有新增页面宽度或浮动保存栏。双语组件交互已验证，但没有登录后的桌面/390px/320px视口截图和相邻页面实测，没有实体手机验收。生产发布未授权，因此没有提交、推送、生产迁移或部署；发布时需要兼容前后端与V126同步。
