# 公共日期输入规范

所有新页面使用 src/components/common/DateInput.vue。支持 date、month、datetime-local，显示分别为 年-月-日、年-月、年-月-日 时:分（英文 YYYY-MM-DD、YYYY-MM、YYYY-MM-DD HH:mm）。原生日期选择器保留，业务模型仍为 ISO 日期、月份或不带时区的本地日期时间；控件不进行 UTC 或门店时区换算。

接口兼容 input 的 v-model、:value、required、disabled、readonly、min、max、step、id/name、class/style、aria 属性，以及 input/change 事件。只有规范日期值或空字符串进入业务模型，未完成或无效手输草稿留在控件内；表单有效性与必填/范围/步长遵循原生约束。事件 target 为原生日期字段，供原有 change/input 回调读取规范值。type=date/month/datetime-local 可在三者间切换；普通数字、文本、密码输入不经过本组件。

父 label 可按原页面布局保留，组件继承现有模块输入外观；模块自己拥有页面宽度与日期筛选排列，公共组件只拥有输入及日历入口。独立字段应提供 label/aria-label。原生输入仅允许存在于公共组件；构建前检查禁止新增裸日期、月份、本地日期时间输入。

不修改服务端、订阅、权限、日期含义、结算口径、员工资料或历史记录。采用只读合成数据验证各产品及窄屏；业务日期/月份/本地时间不能用 Date.toISOString() 互相替换。

手工重置筛选时，如规范模型已为空但控件仍有未完成输入，递增 resetKey 以显式清空本地草稿。不要将无效草稿发送给 API，或靠重载整个页面来清空输入。
