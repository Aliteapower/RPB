# 前端依赖安全修复（2026-10-31，本地）

## 结果与范围

修复当前RPB工作树的8项npm安全审计提示（高危5、中危3）。全量 `npm audit --json` 与 `npm audit --omit=dev --json` 均exit0、漏洞0；不代表未公开或审计数据库未覆盖的问题也不存在。本轮未提交、推送或部署，已上线的薪资发布不在此处静默替换。

- vue-i18n及@intlify同版本链：11.4.6 → 11.4.16，保留11.x主版本。
- vue-tsc：实际安装2.2.12 → 3.3.14（声明从^2.2.0调整为^3.3.14）；language-core升级为3.3.14，新官方依赖链不再引用minimatch，原minimatch9.0.9/brace-expansion/balanced-match被移除。
- package.json补充Node >=22，实际验证Node22.16.0/npm10.9.2；Vue、Vite、TypeScript、路由、Pinia等其他直接依赖声明未改。
- 未使用audit fix --force、忽略公告或不兼容的minimatch overrides。锁文件变化仅上述两条依赖链及其工具链传递依赖，共23个包路径增删/更新，无业务实现、后端、数据库/API/权限变更。

## 安装与隔离

原工作树node_modules是指向D:/RPB/node_modules的junction，因此先在target/frontend-security-20261031/candidate内独立安装、审计和验证。验证后只对本工作树的链接目录项作同卷原子改名，将旧junction保存在target/frontend-security-20261031/shared-node-modules-link；当前node_modules为本树独立的实体目录。没有删除、遍历移动或升级D盘依赖目录。

D盘原项目package.json/package-lock.json及已安装vue-i18n/vue-tsc包元数据哈希前后相同。当前根目录npm ls确认新版本，审计、实际类型检查、Vite构建和安全测试已在根目录再次通过，不仅修改锁文件声明。

## 验证

1. 基线审计复现高危5/中危3。干净隔离安装及接入工作树后的全量/生产依赖审计均为0。
2. 新增frontend-dependency-security.test.mjs：使用维护者公告的无害iframe/srcdoc结构检验属性是否被中和，并验证普通命名插值及复数翻译。旧11.4.6确实因仍保留srcdoc属性而失败，新11.4.16两项通过；没有执行攻击脚本或向真实页面注入内容。
3. 隔离候选与当前工作树的 `npm run build` 均通过（vue-tsc3类型检查＋Vite构建），未抑制类型错误或修改业务代码来迁就升级。
4. 完整前端回归同源A/B：升级前1103项，1085通过/18失败；升级后1105项，1087通过/相同18失败，新增2项安全回归通过。失败用例名称集合完全相同，未发现新增失败；不宣称全仓回归全绿。
5. 18项既有失败来自旧工时列表/员工薪资资料测试流程及钱包测试环境等；详细对应在target下regression-comparison.json，本轮没有扩大修改无关业务或删除断言。首次隔离运行还遇到测试target目录未创建的两项环境错误，补齐目录后已重新跑完整A/B，最终统计采用重跑结果。
6. git diff --check及根目录已安装版本核对通过。私有安装、审计、构建和对照日志在target/frontend-security-20261031/。

## 公告与兼容依据

- [Vue I18n iframe/srcdoc安全公告](https://github.com/intlify/vue-i18n/security/advisories/GHSA-83rm-6c8r-g728)：受影响版本范围截止11.4.14之前。
- [Vue I18n命名参数转义安全公告](https://github.com/intlify/vue-i18n/security/advisories/GHSA-8rc2-428q-7ww3)。
- [minimatch修复版本发布](https://github.com/isaacs/minimatch/releases/tag/v11.0.1)。本项目通过更新上游language-core移除该依赖，不强制将旧9.x调用方覆盖成11.x。
- [Vue语言工具3.3.14发布](https://github.com/vuejs/language-tools/releases/tag/v3.3.14)。同时核实npm官方元数据的TypeScript >=5及vue-i18n Node >=22要求。

## 剩余边界

本轮为本地依赖修复，尚未提交部署；生产需要后续明确发布授权才能应用新前端产物。原18项基线失败保留、未处理。未进行本轮真实浏览器/真机视觉或生产用户流程验收。若撤回本地改动会重新引入已确认漏洞，不能把恢复旧依赖作为安全修复。
