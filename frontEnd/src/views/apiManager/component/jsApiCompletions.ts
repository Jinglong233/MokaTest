/**
 * JavaScript 脚本编辑器自定义补全项定义
 *
 * 覆盖 context.* 平台内置 API、JS 内置函数、常用方法与关键字。
 * 供 Monaco Editor 的 completion provider 使用。
 */

export interface JsCompletionItem {
  label: string
  /** 原 CodeMirror 类型，映射到 Monaco CompletionItemKind */
  type: 'method' | 'property' | 'function' | 'class' | 'keyword' | 'constant'
  detail: string
  info: string
}

export const jsApiCompletions: JsCompletionItem[] = [
  // context 变量操作
  { label: 'context.getVariable', type: 'method', detail: '(name: string)', info: '获取变量值，如 context.getVariable("token")' },
  { label: 'context.setVariable', type: 'method', detail: '(name, value)', info: '设置变量值，如 context.setVariable("sign", "abc")' },
  { label: 'context.setVariables', type: 'method', detail: '(vars: object)', info: '批量设置变量' },

  // context 日志
  { label: 'context.log', type: 'method', detail: '(message)', info: '输出信息日志到控制台' },
  { label: 'context.error', type: 'method', detail: '(message)', info: '输出错误日志到控制台' },

  // context 断言
  { label: 'context.assertCondition', type: 'method', detail: '(condition, message)', info: '自定义断言，如 context.assertCondition(code === 200, "状态码应为200")' },

  // context 请求信息（前置脚本可用）
  { label: 'context.request', type: 'property', detail: '.url | .method | .headers | .body', info: '请求信息对象（前置脚本可修改 headers/body）' },

  // context 响应信息（后置脚本可用）
  { label: 'context.response', type: 'property', detail: '.statusCode | .body | .headers | .responseTimeMs', info: '响应信息对象（后置脚本只读）' },

  // context 工具函数
  { label: 'context.utils', type: 'property', detail: '工具函数集合', info: '内置工具函数' },
  { label: 'context.utils.md5', type: 'method', detail: '(input: string)', info: 'MD5 加密' },
  { label: 'context.utils.sha1', type: 'method', detail: '(input: string)', info: 'SHA1 加密' },
  { label: 'context.utils.sha256', type: 'method', detail: '(input: string)', info: 'SHA256 加密' },
  { label: 'context.utils.base64Encode', type: 'method', detail: '(input: string)', info: 'Base64 编码' },
  { label: 'context.utils.base64Decode', type: 'method', detail: '(input: string)', info: 'Base64 解码' },
  { label: 'context.utils.urlEncode', type: 'method', detail: '(input: string)', info: 'URL 编码' },
  { label: 'context.utils.urlDecode', type: 'method', detail: '(input: string)', info: 'URL 解码' },
  { label: 'context.utils.uuid', type: 'method', detail: '()', info: '生成 UUID' },
  { label: 'context.utils.randomString', type: 'method', detail: '(length: number)', info: '生成随机字符串' },
  { label: 'context.utils.timestamp', type: 'method', detail: '()', info: '获取当前时间戳（毫秒）' },
  { label: 'context.utils.now', type: 'method', detail: '(pattern?: string)', info: '获取当前日期时间，如 context.utils.now("yyyy-MM-dd HH:mm:ss")' },
  { label: 'context.utils.parseJson', type: 'method', detail: '(json: string)', info: 'JSON 字符串转对象（也可用 JSON.parse）' },
  { label: 'context.utils.toJson', type: 'method', detail: '(obj)', info: '对象转 JSON 字符串（也可用 JSON.stringify）' },
  { label: 'context.utils.mock', type: 'method', detail: '(type, ...args)', info: '生成 Mock 数据，如 context.utils.mock("phone")、mock("int", 1, 100)' },
  { label: 'context.utils.template', type: 'method', detail: '(templateId)', info: '按数据模板生成单条数据（JSON 字符串）' },
  { label: 'context.utils.templateBatch', type: 'method', detail: '(templateId, count)', info: '按数据模板批量生成数据（JSON 字符串）' },
  { label: 'context.utils.custom', type: 'method', detail: '(functionId, ...args)', info: '调用自定义公共函数，如 context.utils.custom(123, "a")' },

  // 自定义函数（按名调用，文本字段与脚本统一语法 fn.名称(...)）
  { label: 'fn', type: 'property', detail: '.函数名(...args)', info: '调用当前项目的自定义函数，如 fn.请求签名("a", "b")' },

  // JS 内置函数（常用）
  { label: 'JSON.parse', type: 'method', detail: '(text)', info: 'JSON 字符串解析为对象' },
  { label: 'JSON.stringify', type: 'method', detail: '(obj, replacer?, space?)', info: '对象转为 JSON 字符串' },

  // console
  { label: 'console.log', type: 'method', detail: '(...args)', info: '输出信息到控制台' },
  { label: 'console.error', type: 'method', detail: '(...args)', info: '输出错误到控制台' },
  { label: 'console.warn', type: 'method', detail: '(...args)', info: '输出警告到控制台' },
  { label: 'console.info', type: 'method', detail: '(...args)', info: '输出信息到控制台' },

  // 定时器（后端脚本引擎默认不提供，避免误导，先移除）
  // { label: 'setTimeout', type: 'function', detail: '(fn, delay)', info: '延迟执行函数（毫秒）' },
  // { label: 'setInterval', type: 'function', detail: '(fn, delay)', info: '定时重复执行函数（毫秒）' },
  // { label: 'clearTimeout', type: 'function', detail: '(id)', info: '取消 setTimeout' },
  // { label: 'clearInterval', type: 'function', detail: '(id)', info: '取消 setInterval' },

  // Promise / 异步
  { label: 'Promise', type: 'class', detail: '(executor)', info: 'Promise 构造函数' },
  { label: 'Promise.resolve', type: 'method', detail: '(value)', info: '返回已 resolve 的 Promise' },
  { label: 'Promise.reject', type: 'method', detail: '(reason)', info: '返回已 reject 的 Promise' },
  { label: 'Promise.all', type: 'method', detail: '(promises)', info: '等待所有 Promise 完成' },
  { label: 'Promise.race', type: 'method', detail: '(promises)', info: '返回最先完成的 Promise' },

  // 全局函数
  { label: 'parseInt', type: 'function', detail: '(string, radix)', info: '字符串转整数' },
  { label: 'parseFloat', type: 'function', detail: '(string)', info: '字符串转浮点数' },
  { label: 'isNaN', type: 'function', detail: '(value)', info: '判断是否为 NaN' },
  { label: 'encodeURIComponent', type: 'function', detail: '(string)', info: 'URI 编码' },
  { label: 'decodeURIComponent', type: 'function', detail: '(string)', info: 'URI 解码' },
  { label: 'btoa', type: 'function', detail: '(string)', info: 'Base64 编码（二进制转 ASCII）' },
  { label: 'atob', type: 'function', detail: '(string)', info: 'Base64 解码（ASCII 转二进制）' },

  // Array 常用方法
  { label: 'Array.isArray', type: 'method', detail: '(obj)', info: '判断是否为数组' },
  { label: 'Array.from', type: 'method', detail: '(iterable)', info: '从类数组创建数组' },
  { label: 'push', type: 'method', detail: '(...items)', info: '数组末尾添加元素' },
  { label: 'pop', type: 'method', detail: '()', info: '移除数组末尾元素' },
  { label: 'shift', type: 'method', detail: '()', info: '移除数组开头元素' },
  { label: 'unshift', type: 'method', detail: '(...items)', info: '数组开头添加元素' },
  { label: 'splice', type: 'method', detail: '(start, deleteCount, ...items)', info: '增删数组元素' },
  { label: 'slice', type: 'method', detail: '(start?, end?)', info: '截取数组片段' },
  { label: 'concat', type: 'method', detail: '(...arrays)', info: '合并数组' },
  { label: 'join', type: 'method', detail: '(separator)', info: '数组转字符串' },
  { label: 'reverse', type: 'method', detail: '()', info: '反转数组' },
  { label: 'sort', type: 'method', detail: '(compareFn?)', info: '数组排序' },
  { label: 'indexOf', type: 'method', detail: '(searchElement)', info: '查找元素索引' },
  { label: 'lastIndexOf', type: 'method', detail: '(searchElement)', info: '从后查找元素索引' },
  { label: 'includes', type: 'method', detail: '(value)', info: '判断是否包含元素' },
  { label: 'find', type: 'method', detail: '(predicate)', info: '查找第一个匹配元素' },
  { label: 'findIndex', type: 'method', detail: '(predicate)', info: '查找第一个匹配元素索引' },
  { label: 'filter', type: 'method', detail: '(predicate)', info: '过滤数组元素' },
  { label: 'map', type: 'method', detail: '(callback)', info: '映射转换数组' },
  { label: 'reduce', type: 'method', detail: '(callback, initialValue?)', info: '数组累归约' },
  { label: 'forEach', type: 'method', detail: '(callback)', info: '遍历数组' },
  { label: 'some', type: 'method', detail: '(predicate)', info: '是否有元素满足条件' },
  { label: 'every', type: 'method', detail: '(predicate)', info: '是否所有元素满足条件' },
  { label: 'flat', type: 'method', detail: '(depth?)', info: '数组扁平化' },

  // String 常用方法
  { label: 'length', type: 'property', detail: '', info: '字符串/数组长度' },
  { label: 'charAt', type: 'method', detail: '(index)', info: '获取指定位置字符' },
  { label: 'charCodeAt', type: 'method', detail: '(index)', info: '获取字符 Unicode' },
  { label: 'substring', type: 'method', detail: '(start, end?)', info: '截取子字符串' },
  { label: 'substr', type: 'method', detail: '(start, length?)', info: '截取子字符串' },
  { label: 'indexOf', type: 'method', detail: '(searchString)', info: '查找子串位置' },
  { label: 'lastIndexOf', type: 'method', detail: '(searchString)', info: '从后查找子串位置' },
  { label: 'startsWith', type: 'method', detail: '(searchString)', info: '是否以指定字符串开头' },
  { label: 'endsWith', type: 'method', detail: '(searchString)', info: '是否以指定字符串结尾' },
  { label: 'replace', type: 'method', detail: '(searchValue, replaceValue)', info: '替换字符串' },
  { label: 'replaceAll', type: 'method', detail: '(searchValue, replaceValue)', info: '替换所有匹配' },
  { label: 'toLowerCase', type: 'method', detail: '()', info: '转小写' },
  { label: 'toUpperCase', type: 'method', detail: '()', info: '转大写' },
  { label: 'trim', type: 'method', detail: '()', info: '去除首尾空白' },
  { label: 'split', type: 'method', detail: '(separator)', info: '字符串分割为数组' },
  { label: 'match', type: 'method', detail: '(regexp)', info: '正则匹配' },
  { label: 'matchAll', type: 'method', detail: '(regexp)', info: '正则全局匹配' },
  { label: 'test', type: 'method', detail: '(string)', info: '正则测试（RegExp 方法）' },
  { label: 'padStart', type: 'method', detail: '(targetLength, padString)', info: '左侧填充' },
  { label: 'padEnd', type: 'method', detail: '(targetLength, padString)', info: '右侧填充' },

  // Object 常用方法
  { label: 'Object.keys', type: 'method', detail: '(obj)', info: '获取对象所有键' },
  { label: 'Object.values', type: 'method', detail: '(obj)', info: '获取对象所有值' },
  { label: 'Object.entries', type: 'method', detail: '(obj)', info: '获取对象键值对数组' },
  { label: 'Object.assign', type: 'method', detail: '(target, ...sources)', info: '对象合并/复制' },
  { label: 'Object.hasOwn', type: 'method', detail: '(obj, key)', info: '判断对象自身是否包含属性' },
  { label: 'hasOwnProperty', type: 'method', detail: '(key)', info: '判断对象自身是否包含属性' },

  // Math 常用方法
  { label: 'Math.abs', type: 'method', detail: '(x)', info: '绝对值' },
  { label: 'Math.ceil', type: 'method', detail: '(x)', info: '向上取整' },
  { label: 'Math.floor', type: 'method', detail: '(x)', info: '向下取整' },
  { label: 'Math.round', type: 'method', detail: '(x)', info: '四舍五入' },
  { label: 'Math.max', type: 'method', detail: '(...values)', info: '取最大值' },
  { label: 'Math.min', type: 'method', detail: '(...values)', info: '取最小值' },
  { label: 'Math.random', type: 'method', detail: '()', info: '生成 0-1 随机数' },
  { label: 'Math.pow', type: 'method', detail: '(base, exponent)', info: '幂运算' },
  { label: 'Math.sqrt', type: 'method', detail: '(x)', info: '平方根' },

  // Date 常用方法
  { label: 'Date.now', type: 'method', detail: '()', info: '获取当前时间戳（毫秒）' },
  { label: 'new Date', type: 'function', detail: '(value?)', info: '创建日期对象' },
  { label: 'getTime', type: 'method', detail: '()', info: '获取时间戳' },
  { label: 'getFullYear', type: 'method', detail: '()', info: '获取年份' },
  { label: 'getMonth', type: 'method', detail: '()', info: '获取月份（0-11）' },
  { label: 'getDate', type: 'method', detail: '()', info: '获取日期（1-31）' },
  { label: 'getHours', type: 'method', detail: '()', info: '获取小时' },
  { label: 'getMinutes', type: 'method', detail: '()', info: '获取分钟' },
  { label: 'getSeconds', type: 'method', detail: '()', info: '获取秒' },
  { label: 'toISOString', type: 'method', detail: '()', info: '转为 ISO 格式字符串' },

  // Number 常用方法
  { label: 'Number.isNaN', type: 'method', detail: '(value)', info: '判断是否为 NaN' },
  { label: 'Number.isInteger', type: 'method', detail: '(value)', info: '判断是否为整数' },
  { label: 'Number.parseFloat', type: 'method', detail: '(string)', info: '字符串转浮点数' },
  { label: 'Number.parseInt', type: 'method', detail: '(string, radix)', info: '字符串转整数' },
  { label: 'toFixed', type: 'method', detail: '(digits)', info: '保留指定小数位' },

  // 其他常用
  { label: 'typeof', type: 'keyword', detail: 'operand', info: '获取数据类型' },
  { label: 'instanceof', type: 'keyword', detail: 'constructor', info: '判断实例类型' },
  { label: 'undefined', type: 'constant', detail: '', info: '未定义' },
  { label: 'null', type: 'constant', detail: '', info: '空值' },
  { label: 'true', type: 'constant', detail: '', info: '真' },
  { label: 'false', type: 'constant', detail: '', info: '假' },

  // 控制流关键字（方便输入时提示）
  { label: 'function', type: 'keyword', detail: ' name() {}', info: '声明函数' },
  { label: 'return', type: 'keyword', detail: ' value', info: '返回值' },
  { label: 'if', type: 'keyword', detail: '(condition) {}', info: '条件判断' },
  { label: 'else', type: 'keyword', detail: ' {}', info: '否则' },
  { label: 'else if', type: 'keyword', detail: '(condition) {}', info: '否则如果' },
  { label: 'for', type: 'keyword', detail: '(init; condition; step) {}', info: 'for 循环' },
  { label: 'while', type: 'keyword', detail: '(condition) {}', info: 'while 循环' },
  { label: 'do', type: 'keyword', detail: '...while(condition)', info: 'do...while 循环' },
  { label: 'break', type: 'keyword', detail: '', info: '跳出循环' },
  { label: 'continue', type: 'keyword', detail: '', info: '跳过本次循环' },
  { label: 'switch', type: 'keyword', detail: '(expression) {}', info: 'switch 分支' },
  { label: 'case', type: 'keyword', detail: ' value:', info: 'case 分支' },
  { label: 'default', type: 'keyword', detail: ':', info: '默认分支' },
  { label: 'try', type: 'keyword', detail: '{} catch(e) {}', info: '异常捕获' },
  { label: 'catch', type: 'keyword', detail: '(err) {}', info: '捕获异常' },
  { label: 'finally', type: 'keyword', detail: '{}', info: '最终执行' },
  { label: 'throw', type: 'keyword', detail: ' new Error()', info: '抛出异常' },
  { label: 'var', type: 'keyword', detail: ' name', info: '声明变量（函数作用域）' },
  { label: 'let', type: 'keyword', detail: ' name', info: '声明变量（块作用域）' },
  { label: 'const', type: 'keyword', detail: ' name', info: '声明常量（块作用域）' },
  { label: 'this', type: 'keyword', detail: '', info: '当前上下文' },
  { label: 'new', type: 'keyword', detail: ' Constructor()', info: '创建实例' },
  { label: 'delete', type: 'keyword', detail: ' property', info: '删除属性' },
  { label: 'in', type: 'keyword', detail: ' property in obj', info: '判断属性是否在对象中' },
  { label: 'void', type: 'keyword', detail: ' expression', info: '返回 undefined' },

  // ES6+ 常用
  { label: 'class', type: 'keyword', detail: ' Name {}', info: '声明类' },
  { label: 'extends', type: 'keyword', detail: ' Parent', info: '类继承' },
  { label: 'constructor', type: 'keyword', detail: '() {}', info: '类构造函数' },
  { label: 'super', type: 'keyword', detail: '()', info: '调用父类' },
  { label: 'import', type: 'keyword', detail: ' { ... } from "..."', info: '导入模块' },
  { label: 'export', type: 'keyword', detail: ' default ...', info: '导出模块' },
  { label: 'async', type: 'keyword', detail: ' function', info: '声明异步函数' },
  { label: 'await', type: 'keyword', detail: ' promise', info: '等待 Promise 完成' },
  { label: 'yield', type: 'keyword', detail: ' value', info: '生成器产出值' },
  { label: 'Symbol', type: 'class', detail: '(description?)', info: '创建唯一标识符' },
  { label: 'Map', type: 'class', detail: '(iterable?)', info: '键值对集合' },
  { label: 'Set', type: 'class', detail: '(iterable?)', info: '唯一值集合' },
  { label: 'WeakMap', type: 'class', detail: '()', info: '弱引用键值对' },
  { label: 'WeakSet', type: 'class', detail: '()', info: '弱引用集合' },
]
