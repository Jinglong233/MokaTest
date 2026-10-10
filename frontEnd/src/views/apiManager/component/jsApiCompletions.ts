/**
 * JavaScript 脚本编辑器自定义补全项定义
 *
 * 覆盖 context.* 平台内置 API、JS 内置函数、常用方法与关键字。
 * 供 Monaco Editor 的 completion provider 使用。
 */

import i18n from '@/locale';

export interface JsCompletionItem {
  label: string
  /** 原 CodeMirror 类型，映射到 Monaco CompletionItemKind */
  type: 'method' | 'property' | 'function' | 'class' | 'keyword' | 'constant'
  detail: string
  info: string
}

const t = (key: string) => i18n.global.t(key);

export const getJsApiCompletions = (): JsCompletionItem[] => [
  // context 变量操作
  { label: 'context.getVariable', type: 'method', detail: '(name: string)', info: t('api.jsComp.contextGetVariable') },
  { label: 'context.setVariable', type: 'method', detail: '(name, value)', info: t('api.jsComp.contextSetVariable') },
  { label: 'context.setVariables', type: 'method', detail: '(vars: object)', info: t('api.jsComp.contextSetVariables') },

  // context 日志
  { label: 'context.log', type: 'method', detail: '(message)', info: t('api.jsComp.contextLog') },
  { label: 'context.error', type: 'method', detail: '(message)', info: t('api.jsComp.contextError') },

  // context 断言
  { label: 'context.assertCondition', type: 'method', detail: '(condition, message)', info: t('api.jsComp.contextAssertCondition') },

  // context 请求信息（前置脚本可用）
  { label: 'context.request', type: 'property', detail: '.url | .method | .headers | .body', info: t('api.jsComp.contextRequest') },

  // context 响应信息（后置脚本可用）
  { label: 'context.response', type: 'property', detail: '.statusCode | .body | .headers | .responseTimeMs', info: t('api.jsComp.contextResponse') },

  // context 工具函数
  { label: 'context.utils', type: 'property', detail: t('api.jsComp.contextUtilsDetail'), info: t('api.jsComp.contextUtils') },
  { label: 'context.utils.md5', type: 'method', detail: '(input: string)', info: t('api.jsComp.contextUtilsMd5') },
  { label: 'context.utils.sha1', type: 'method', detail: '(input: string)', info: t('api.jsComp.contextUtilsSha1') },
  { label: 'context.utils.sha256', type: 'method', detail: '(input: string)', info: t('api.jsComp.contextUtilsSha256') },
  { label: 'context.utils.base64Encode', type: 'method', detail: '(input: string)', info: t('api.jsComp.contextUtilsBase64Encode') },
  { label: 'context.utils.base64Decode', type: 'method', detail: '(input: string)', info: t('api.jsComp.contextUtilsBase64Decode') },
  { label: 'context.utils.urlEncode', type: 'method', detail: '(input: string)', info: t('api.jsComp.contextUtilsUrlEncode') },
  { label: 'context.utils.urlDecode', type: 'method', detail: '(input: string)', info: t('api.jsComp.contextUtilsUrlDecode') },
  { label: 'context.utils.uuid', type: 'method', detail: '()', info: t('api.jsComp.contextUtilsUuid') },
  { label: 'context.utils.randomString', type: 'method', detail: '(length: number)', info: t('api.jsComp.contextUtilsRandomString') },
  { label: 'context.utils.timestamp', type: 'method', detail: '()', info: t('api.jsComp.contextUtilsTimestamp') },
  { label: 'context.utils.now', type: 'method', detail: '(pattern?: string)', info: t('api.jsComp.contextUtilsNow') },
  { label: 'context.utils.parseJson', type: 'method', detail: '(json: string)', info: t('api.jsComp.contextUtilsParseJson') },
  { label: 'context.utils.toJson', type: 'method', detail: '(obj)', info: t('api.jsComp.contextUtilsToJson') },
  { label: 'context.utils.mock', type: 'method', detail: '(type, ...args)', info: t('api.jsComp.contextUtilsMock') },
  { label: 'context.utils.template', type: 'method', detail: '(templateId)', info: t('api.jsComp.contextUtilsTemplate') },
  { label: 'context.utils.templateBatch', type: 'method', detail: '(templateId, count)', info: t('api.jsComp.contextUtilsTemplateBatch') },
  { label: 'context.utils.custom', type: 'method', detail: '(functionId, ...args)', info: t('api.jsComp.contextUtilsCustom') },

  // 自定义函数（按名调用，文本字段与脚本统一语法 fn.名称(...)）
  { label: 'fn', type: 'property', detail: t('api.jsComp.fnDetail'), info: t('api.jsComp.fn') },

  // JS 内置函数（常用）
  { label: 'JSON.parse', type: 'method', detail: '(text)', info: t('api.jsComp.jsonParse') },
  { label: 'JSON.stringify', type: 'method', detail: '(obj, replacer?, space?)', info: t('api.jsComp.jsonStringify') },

  // console
  { label: 'console.log', type: 'method', detail: '(...args)', info: t('api.jsComp.consoleLog') },
  { label: 'console.error', type: 'method', detail: '(...args)', info: t('api.jsComp.consoleError') },
  { label: 'console.warn', type: 'method', detail: '(...args)', info: t('api.jsComp.consoleWarn') },
  { label: 'console.info', type: 'method', detail: '(...args)', info: t('api.jsComp.consoleInfo') },

  // 定时器（后端脚本引擎默认不提供，避免误导，先移除）
  // { label: 'setTimeout', type: 'function', detail: '(fn, delay)', info: '延迟执行函数（毫秒）' },
  // { label: 'setInterval', type: 'function', detail: '(fn, delay)', info: '定时重复执行函数（毫秒）' },
  // { label: 'clearTimeout', type: 'function', detail: '(id)', info: '取消 setTimeout' },
  // { label: 'clearInterval', type: 'function', detail: '(id)', info: '取消 setInterval' },

  // Promise / 异步
  { label: 'Promise', type: 'class', detail: '(executor)', info: t('api.jsComp.promise') },
  { label: 'Promise.resolve', type: 'method', detail: '(value)', info: t('api.jsComp.promiseResolve') },
  { label: 'Promise.reject', type: 'method', detail: '(reason)', info: t('api.jsComp.promiseReject') },
  { label: 'Promise.all', type: 'method', detail: '(promises)', info: t('api.jsComp.promiseAll') },
  { label: 'Promise.race', type: 'method', detail: '(promises)', info: t('api.jsComp.promiseRace') },

  // 全局函数
  { label: 'parseInt', type: 'function', detail: '(string, radix)', info: t('api.jsComp.parseint') },
  { label: 'parseFloat', type: 'function', detail: '(string)', info: t('api.jsComp.parsefloat') },
  { label: 'isNaN', type: 'function', detail: '(value)', info: t('api.jsComp.isnan') },
  { label: 'encodeURIComponent', type: 'function', detail: '(string)', info: t('api.jsComp.encodeuricomponent') },
  { label: 'decodeURIComponent', type: 'function', detail: '(string)', info: t('api.jsComp.decodeuricomponent') },
  { label: 'btoa', type: 'function', detail: '(string)', info: t('api.jsComp.btoa') },
  { label: 'atob', type: 'function', detail: '(string)', info: t('api.jsComp.atob') },

  // Array 常用方法
  { label: 'Array.isArray', type: 'method', detail: '(obj)', info: t('api.jsComp.arrayIsArray') },
  { label: 'Array.from', type: 'method', detail: '(iterable)', info: t('api.jsComp.arrayFrom') },
  { label: 'push', type: 'method', detail: '(...items)', info: t('api.jsComp.push') },
  { label: 'pop', type: 'method', detail: '()', info: t('api.jsComp.pop') },
  { label: 'shift', type: 'method', detail: '()', info: t('api.jsComp.shift') },
  { label: 'unshift', type: 'method', detail: '(...items)', info: t('api.jsComp.unshift') },
  { label: 'splice', type: 'method', detail: '(start, deleteCount, ...items)', info: t('api.jsComp.splice') },
  { label: 'slice', type: 'method', detail: '(start?, end?)', info: t('api.jsComp.slice') },
  { label: 'concat', type: 'method', detail: '(...arrays)', info: t('api.jsComp.concat') },
  { label: 'join', type: 'method', detail: '(separator)', info: t('api.jsComp.join') },
  { label: 'reverse', type: 'method', detail: '()', info: t('api.jsComp.reverse') },
  { label: 'sort', type: 'method', detail: '(compareFn?)', info: t('api.jsComp.sort') },
  { label: 'indexOf', type: 'method', detail: '(searchElement)', info: t('api.jsComp.indexof') },
  { label: 'lastIndexOf', type: 'method', detail: '(searchElement)', info: t('api.jsComp.lastindexof') },
  { label: 'includes', type: 'method', detail: '(value)', info: t('api.jsComp.includes') },
  { label: 'find', type: 'method', detail: '(predicate)', info: t('api.jsComp.find') },
  { label: 'findIndex', type: 'method', detail: '(predicate)', info: t('api.jsComp.findindex') },
  { label: 'filter', type: 'method', detail: '(predicate)', info: t('api.jsComp.filter') },
  { label: 'map', type: 'method', detail: '(callback)', info: t('api.jsComp.map') },
  { label: 'reduce', type: 'method', detail: '(callback, initialValue?)', info: t('api.jsComp.reduce') },
  { label: 'forEach', type: 'method', detail: '(callback)', info: t('api.jsComp.foreach') },
  { label: 'some', type: 'method', detail: '(predicate)', info: t('api.jsComp.some') },
  { label: 'every', type: 'method', detail: '(predicate)', info: t('api.jsComp.every') },
  { label: 'flat', type: 'method', detail: '(depth?)', info: t('api.jsComp.flat') },

  // String 常用方法
  { label: 'length', type: 'property', detail: '', info: t('api.jsComp.length') },
  { label: 'charAt', type: 'method', detail: '(index)', info: t('api.jsComp.charat') },
  { label: 'charCodeAt', type: 'method', detail: '(index)', info: t('api.jsComp.charcodeat') },
  { label: 'substring', type: 'method', detail: '(start, end?)', info: t('api.jsComp.substring') },
  { label: 'substr', type: 'method', detail: '(start, length?)', info: t('api.jsComp.substr') },
  { label: 'indexOf', type: 'method', detail: '(searchString)', info: t('api.jsComp.indexof2') },
  { label: 'lastIndexOf', type: 'method', detail: '(searchString)', info: t('api.jsComp.lastindexof2') },
  { label: 'startsWith', type: 'method', detail: '(searchString)', info: t('api.jsComp.startswith') },
  { label: 'endsWith', type: 'method', detail: '(searchString)', info: t('api.jsComp.endswith') },
  { label: 'replace', type: 'method', detail: '(searchValue, replaceValue)', info: t('api.jsComp.replace') },
  { label: 'replaceAll', type: 'method', detail: '(searchValue, replaceValue)', info: t('api.jsComp.replaceall') },
  { label: 'toLowerCase', type: 'method', detail: '()', info: t('api.jsComp.tolowercase') },
  { label: 'toUpperCase', type: 'method', detail: '()', info: t('api.jsComp.touppercase') },
  { label: 'trim', type: 'method', detail: '()', info: t('api.jsComp.trim') },
  { label: 'split', type: 'method', detail: '(separator)', info: t('api.jsComp.split') },
  { label: 'match', type: 'method', detail: '(regexp)', info: t('api.jsComp.match') },
  { label: 'matchAll', type: 'method', detail: '(regexp)', info: t('api.jsComp.matchall') },
  { label: 'test', type: 'method', detail: '(string)', info: t('api.jsComp.test') },
  { label: 'padStart', type: 'method', detail: '(targetLength, padString)', info: t('api.jsComp.padstart') },
  { label: 'padEnd', type: 'method', detail: '(targetLength, padString)', info: t('api.jsComp.padend') },

  // Object 常用方法
  { label: 'Object.keys', type: 'method', detail: '(obj)', info: t('api.jsComp.objectKeys') },
  { label: 'Object.values', type: 'method', detail: '(obj)', info: t('api.jsComp.objectValues') },
  { label: 'Object.entries', type: 'method', detail: '(obj)', info: t('api.jsComp.objectEntries') },
  { label: 'Object.assign', type: 'method', detail: '(target, ...sources)', info: t('api.jsComp.objectAssign') },
  { label: 'Object.hasOwn', type: 'method', detail: '(obj, key)', info: t('api.jsComp.objectHasOwn') },
  { label: 'hasOwnProperty', type: 'method', detail: '(key)', info: t('api.jsComp.hasownproperty') },

  // Math 常用方法
  { label: 'Math.abs', type: 'method', detail: '(x)', info: t('api.jsComp.mathAbs') },
  { label: 'Math.ceil', type: 'method', detail: '(x)', info: t('api.jsComp.mathCeil') },
  { label: 'Math.floor', type: 'method', detail: '(x)', info: t('api.jsComp.mathFloor') },
  { label: 'Math.round', type: 'method', detail: '(x)', info: t('api.jsComp.mathRound') },
  { label: 'Math.max', type: 'method', detail: '(...values)', info: t('api.jsComp.mathMax') },
  { label: 'Math.min', type: 'method', detail: '(...values)', info: t('api.jsComp.mathMin') },
  { label: 'Math.random', type: 'method', detail: '()', info: t('api.jsComp.mathRandom') },
  { label: 'Math.pow', type: 'method', detail: '(base, exponent)', info: t('api.jsComp.mathPow') },
  { label: 'Math.sqrt', type: 'method', detail: '(x)', info: t('api.jsComp.mathSqrt') },

  // Date 常用方法
  { label: 'Date.now', type: 'method', detail: '()', info: t('api.jsComp.dateNow') },
  { label: 'new Date', type: 'function', detail: '(value?)', info: t('api.jsComp.newDate') },
  { label: 'getTime', type: 'method', detail: '()', info: t('api.jsComp.gettime') },
  { label: 'getFullYear', type: 'method', detail: '()', info: t('api.jsComp.getfullyear') },
  { label: 'getMonth', type: 'method', detail: '()', info: t('api.jsComp.getmonth') },
  { label: 'getDate', type: 'method', detail: '()', info: t('api.jsComp.getdate') },
  { label: 'getHours', type: 'method', detail: '()', info: t('api.jsComp.gethours') },
  { label: 'getMinutes', type: 'method', detail: '()', info: t('api.jsComp.getminutes') },
  { label: 'getSeconds', type: 'method', detail: '()', info: t('api.jsComp.getseconds') },
  { label: 'toISOString', type: 'method', detail: '()', info: t('api.jsComp.toisostring') },

  // Number 常用方法
  { label: 'Number.isNaN', type: 'method', detail: '(value)', info: t('api.jsComp.numberIsNaN') },
  { label: 'Number.isInteger', type: 'method', detail: '(value)', info: t('api.jsComp.numberIsInteger') },
  { label: 'Number.parseFloat', type: 'method', detail: '(string)', info: t('api.jsComp.numberParseFloat') },
  { label: 'Number.parseInt', type: 'method', detail: '(string, radix)', info: t('api.jsComp.numberParseInt') },
  { label: 'toFixed', type: 'method', detail: '(digits)', info: t('api.jsComp.tofixed') },

  // 其他常用
  { label: 'typeof', type: 'keyword', detail: 'operand', info: t('api.jsComp.typeof') },
  { label: 'instanceof', type: 'keyword', detail: 'constructor', info: t('api.jsComp.instanceof') },
  { label: 'undefined', type: 'constant', detail: '', info: t('api.jsComp.undefined') },
  { label: 'null', type: 'constant', detail: '', info: t('api.jsComp.null') },
  { label: 'true', type: 'constant', detail: '', info: t('api.jsComp.true') },
  { label: 'false', type: 'constant', detail: '', info: t('api.jsComp.false') },

  // 控制流关键字（方便输入时提示）
  { label: 'function', type: 'keyword', detail: ' name() {}', info: t('api.jsComp.function') },
  { label: 'return', type: 'keyword', detail: ' value', info: t('api.jsComp.return') },
  { label: 'if', type: 'keyword', detail: '(condition) {}', info: t('api.jsComp.if') },
  { label: 'else', type: 'keyword', detail: ' {}', info: t('api.jsComp.else') },
  { label: 'else if', type: 'keyword', detail: '(condition) {}', info: t('api.jsComp.elseIf') },
  { label: 'for', type: 'keyword', detail: '(init; condition; step) {}', info: t('api.jsComp.for') },
  { label: 'while', type: 'keyword', detail: '(condition) {}', info: t('api.jsComp.while') },
  { label: 'do', type: 'keyword', detail: '...while(condition)', info: t('api.jsComp.do') },
  { label: 'break', type: 'keyword', detail: '', info: t('api.jsComp.break') },
  { label: 'continue', type: 'keyword', detail: '', info: t('api.jsComp.continue') },
  { label: 'switch', type: 'keyword', detail: '(expression) {}', info: t('api.jsComp.switch') },
  { label: 'case', type: 'keyword', detail: ' value:', info: t('api.jsComp.case') },
  { label: 'default', type: 'keyword', detail: ':', info: t('api.jsComp.default') },
  { label: 'try', type: 'keyword', detail: '{} catch(e) {}', info: t('api.jsComp.try') },
  { label: 'catch', type: 'keyword', detail: '(err) {}', info: t('api.jsComp.catch') },
  { label: 'finally', type: 'keyword', detail: '{}', info: t('api.jsComp.finally') },
  { label: 'throw', type: 'keyword', detail: ' new Error()', info: t('api.jsComp.throw') },
  { label: 'var', type: 'keyword', detail: ' name', info: t('api.jsComp.var') },
  { label: 'let', type: 'keyword', detail: ' name', info: t('api.jsComp.let') },
  { label: 'const', type: 'keyword', detail: ' name', info: t('api.jsComp.const') },
  { label: 'this', type: 'keyword', detail: '', info: t('api.jsComp.this') },
  { label: 'new', type: 'keyword', detail: ' Constructor()', info: t('api.jsComp.new') },
  { label: 'delete', type: 'keyword', detail: ' property', info: t('api.jsComp.delete') },
  { label: 'in', type: 'keyword', detail: ' property in obj', info: t('api.jsComp.in') },
  { label: 'void', type: 'keyword', detail: ' expression', info: t('api.jsComp.void') },

  // ES6+ 常用
  { label: 'class', type: 'keyword', detail: ' Name {}', info: t('api.jsComp.class') },
  { label: 'extends', type: 'keyword', detail: ' Parent', info: t('api.jsComp.extends') },
  { label: 'constructor', type: 'keyword', detail: '() {}', info: t('api.jsComp.constructor') },
  { label: 'super', type: 'keyword', detail: '()', info: t('api.jsComp.super') },
  { label: 'import', type: 'keyword', detail: ' { ... } from "..."', info: t('api.jsComp.import') },
  { label: 'export', type: 'keyword', detail: ' default ...', info: t('api.jsComp.export') },
  { label: 'async', type: 'keyword', detail: ' function', info: t('api.jsComp.async') },
  { label: 'await', type: 'keyword', detail: ' promise', info: t('api.jsComp.await') },
  { label: 'yield', type: 'keyword', detail: ' value', info: t('api.jsComp.yield') },
  { label: 'Symbol', type: 'class', detail: '(description?)', info: t('api.jsComp.symbol') },
  { label: 'Map', type: 'class', detail: '(iterable?)', info: t('api.jsComp.map2') },
  { label: 'Set', type: 'class', detail: '(iterable?)', info: t('api.jsComp.set') },
  { label: 'WeakMap', type: 'class', detail: '()', info: t('api.jsComp.weakmap') },
  { label: 'WeakSet', type: 'class', detail: '()', info: t('api.jsComp.weakset') },
]
