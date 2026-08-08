import {ClickType} from "@/types/enum/click/ClickType";
import {ClosePageMode,} from "@/types/enum/browser/ClosePageMode";
import {AssertType} from "@/types/enum/condation/AssertType";
import {AssertRelationship} from "@/types/enum/condation/AssertRelationship";
import {PageAttribute} from "@/types/enum/page/PageAttribute";
import {ConditionalRelationship} from "@/types/enum/condation/ConditionalRelationship";
import {SwitchIframeType} from "@/types/enum/iframe/SwitchIframeType";
import {SwitchTabMode} from "@/types/enum/browser/SwitchTabMode";
import {ExtractType} from "@/types/enum/extract/ExtractType";
import {ElementExtractValueType} from "@/types/enum/extract/ElementExtractValueType";
import {PageExtractValueType} from "@/types/enum/extract/PageExtractValueType";
import {StepErrorHandleStrategy} from "@/types/enum/StepErrorHandleStrategy";
import {ScreenshotConfig} from "@/types/enum/ScreenshotConfig";
import {KeyboardInputType} from "@/types/enum/keyboard/KeyboardInputType";
import {KeyboardKey} from "@/types/enum/keyboard/KeyboardKey";
import {ForCycleType} from "@/types/enum/cycle/ForCycleType";
import {WaitType} from "@/types/enum/WaitType";
import {StepType} from "@/types/enum/StepType";
import {DialogOperationType} from "@/types/enum/dialog/DialogOperationType";
import {ElementDomOperationType} from "@/types/enum/element/ElementDomOperationType";
import {StylePriority} from "@/types/enum/element/StylePriority";

// 打开页面
const OpenPage = [
    {
        key: 'stepName',
        label: '步骤名称',
        type: 'input',
        placeholder: '请输入步骤名称',
        required: true
    },
    {
        key: 'url',
        label: '网址',
        type: 'input',
        placeholder: '请输入网址',
        required: true
    },
    {
        key: 'recover',
        label: '是否覆盖写',
        type: 'switch',
        placeholder: '请选择元素',
        options: [
            {label: '是', value: 1},
            {label: '否', value: 0},
        ],
        defaultValue: 1,
        required: true
    },
]


// 关闭页面
const ClosePage = [
    {
        key: 'stepName',
        label: '步骤名称',
        type: 'input',
        placeholder: '请输入步骤名称',
        required: true
    },
    {
        key: 'closePageMode',
        label: '关闭方式',
        type: 'select',
        placeholder: '请输入步骤名称',
        options: [
            {label: '当前页面', value: ClosePageMode.CURRENT},
            {label: '第一个', value: ClosePageMode.FIRST},
            {label: '上一个', value: ClosePageMode.LAST},
            {label: '下一个', value: ClosePageMode.NEXT},
            {label: '最后一个', value: ClosePageMode.END},
            {label: '所有页面', value: ClosePageMode.ALL},
            {label: '自定义页面索引', value: ClosePageMode.CUSTOM_INDEX},
        ],
        default: "CURRENT",
        required: true
    },
    {
        key: 'customIndex',
        label: '页面索引',
        type: 'number',
        placeholder: '请输入页面索引',
        conditions: [
            {field: 'closePageMode', type: 'equals', value: ClosePageMode.CUSTOM_INDEX},
        ],
        default: 0,
        required: true
    },
]


// 切换tab
const SwitchTab = [
    {
        key: 'stepName',
        label: '步骤名称',
        type: 'input',
        placeholder: '请输入步骤名称',
        required: true
    },
    {
        key: 'switchTabMode',
        label: '切换方式',
        type: 'select',
        placeholder: '请输入步骤名称',
        options: [
            {label: '第一个页签', value: SwitchTabMode.FIRST},
            {label: '上一个页签', value: SwitchTabMode.LAST},
            {label: '下一个页签', value: SwitchTabMode.NEXT},
            {label: '最后一个页签', value: SwitchTabMode.END},
            {label: '自定义页面索引', value: SwitchTabMode.CUSTOM_INDEX},
        ],
        default: "CUSTOM_INDEX",
        required: true
    },
    {
        key: 'customIndex',
        label: '切换tab索引',
        type: 'number',
        placeholder: '请输入tab索引',
        default: 0,
        min: 0,
        max: 9999,
        conditions: [
            {field: 'switchTabMode', type: 'equals', value: SwitchTabMode.CUSTOM_INDEX},
        ],
        required: true
    }
]

// 前进
const Forward = [
    {
        key: 'stepName',
        label: '步骤名称',
        type: 'input',
        placeholder: '请输入步骤名称',
        required: true
    },
]

// 后退
const Back = [
    {
        key: 'stepName',
        label: '步骤名称',
        type: 'input',
        placeholder: '请输入步骤名称',
        required: true
    },
]

// 刷新
const Refresh = [
    {
        key: 'stepName',
        label: '步骤名称',
        type: 'input',
        placeholder: '请输入步骤名称',
        required: true
    },
]


// 点击表单
const ClickForm = [
    {
        key: 'stepName',
        label: '步骤名称',
        type: 'input',
        placeholder: '请输入步骤名称',
        required: true
    },
    {
        key: 'clickType',
        label: '点击类型',
        type: 'select',
        placeholder: '请选择点击类型',
        options: [
            {label: '单击', value: ClickType.SINGLE_CLICK},
            {label: '双击', value: ClickType.DOUBLE_CLICK},
            {label: '点击（右键）', value: ClickType.RIGHT_CLICK},
            {label: '长按', value: ClickType.LONG_PRESS},
            {label: 'select下拉', value: ClickType.SELECT},
        ],
        default: "SINGLE_CLICK",
        required: true
    },
    {
        key: 'element',
        label: '目标元素',
        type: 'elementSelect',
        placeholder: '请选择元素',
        required: true
    },
    {
        key: 'optionValue',
        label: '下拉选项值',
        type: 'input',
        placeholder: '请输入option对应的value值',
        conditions: [
            {field: 'clickType', type: 'equals', value: ClickType.SELECT},
        ],
        required: true
    }
]


// 悬停
const Hover = [
    {
        key: 'stepName',
        label: '步骤名称',
        type: 'input',
        placeholder: '请输入步骤名称',
        required: true
    },
    {
        key: 'element',
        label: '目标元素',
        type: 'elementSelect',
        placeholder: '请选择元素',
        required: true
    },
]

// 拖拽
const Drag = [
    {
        key: 'stepName',
        label: '步骤名称',
        type: 'input',
        placeholder: '请输入步骤名称',
        required: true
    },
    {
        key: 'dragElement',
        label: '拖拽元素',
        type: 'elementSelect',
        placeholder: '请选择拖拽元素',
        required: true
    },
    {
        key: 'targetElement',
        label: '目标元素',
        type: 'elementSelect',
        placeholder: '请选择目标元素',
        required: true
    },
]

// 键盘操作
const Keyboard = [
    {
        key: 'stepName',
        label: '步骤名称',
        type: 'input',
        placeholder: '请输入步骤名称',
        required: true
    },
    {
        key: 'inputType',
        label: '输入类型',
        type: 'select',
        placeholder: '请选择输入类型',
        options: [
            {label: '普通输入', value: KeyboardInputType.NORMAL},
            {label: '按键输入', value: KeyboardInputType.KEYBOARD},
        ],
        default: KeyboardInputType.KEYBOARD,
        required: true
    },
    {
        key: 'element',
        label: '目标元素',
        type: 'elementSelect',
        placeholder: '请选择目标元素',
        conditions: [
            {field: 'inputType', type: 'equals', value: KeyboardInputType.NORMAL},
        ],
        required: true
    },
    {
        key: 'keyboardKey',
        label: '按键',
        type: 'select',
        placeholder: '请选择按键',
        options: [
            {label: '键盘输入', value: KeyboardKey.PRESS_INPUT},
            {label: '回车', value: KeyboardKey.ENTER},
            {label: 'Delete删除', value: KeyboardKey.DELETE},
            {label: '空格', value: KeyboardKey.SPACE},
            {label: '回退Backspace', value: KeyboardKey.BACKSPACE},
        ],
        conditions: [
            {field: 'inputType', type: 'equals', value: KeyboardInputType.KEYBOARD},
        ],
        default: "ENTER",
        required: true
    },
    {
        key: 'inputValue',
        label: '输入内容',
        type: 'input',
        placeholder: '请填写输入内容',
        operator: "or",
        conditions: [
            {
                field: 'inputType',
                type: 'equals',
                value: KeyboardInputType.NORMAL
            },
            {
                field: 'keyboardKey',
                type: 'equals',
                value: KeyboardKey.PRESS_INPUT
            },

        ],
        required: true
    },
    {
        key: 'isAdditional',
        label: '是否追加输入',
        type: 'switch',
        placeholder: '请选择追加方式',
        options: [
            {label: '追加', value: 1},
            {label: '不追加', value: 0},
        ],
        default: 1,
        conditions: [
            {field: 'inputType', type: 'equals', value: KeyboardInputType.NORMAL},
        ],
        required: true
    },
]


// 断言操作
const Assert = [
    {
        key: 'stepName',
        label: '步骤名称',
        type: 'input',
        placeholder: '请输入步骤名称',
        required: true
    },
    {
        key: 'assertType',
        label: '断言类型',
        type: 'select',
        placeholder: '请选择断言类型',
        options: [
            {label: '元素存在', value: AssertType.ELEMENT_EXIST},
            {label: '元素不存在', value: AssertType.ELEMENT_NOT_EXIST},
            {label: '文字存在', value: AssertType.TEXT_EXIST},
            {label: '文字不存在', value: AssertType.TEXT_NOT_EXIST},
            {label: '元素属性', value: AssertType.ELEMENT_ARRTRIBUTE},
            {label: '页面属性', value: AssertType.PAGE_ARRTRIBUTE},
            {label: '自定义断言', value: AssertType.CUSTOM},
        ],
        required: true
    },
    {
        key: 'element',
        label: '目标元素',
        type: 'elementSelect',
        placeholder: '请选择目标元素',
        conditions: [
            {
                field: 'assertType',
                type: 'in',
                values: [AssertType.ELEMENT_EXIST, AssertType.ELEMENT_NOT_EXIST, AssertType.ELEMENT_ARRTRIBUTE]
            },
        ],
    },
    {
        key: 'assertText',
        label: '断言文本',
        type: 'input',
        placeholder: '请输入断言文本',
        conditions: [
            {
                field: 'assertType',
                type: 'in',
                values: [AssertType.TEXT_EXIST, AssertType.TEXT_NOT_EXIST, AssertType.CUSTOM]
            },
        ],
        required: true
    },
    {
        key: 'elementAttribute',
        label: '元素属性',
        type: 'input',
        placeholder: '请输入属性名称',
        conditions: [
            {field: 'assertType', type: 'equals', value: AssertType.ELEMENT_ARRTRIBUTE},
        ],
        required: true
    },
    {
        key: 'pageAttribute',
        label: '页面属性',
        type: 'select',
        placeholder: '请输入属性名称',
        options: [
            {label: '页面名称', value: PageAttribute.PAGE_NAME},
            {label: '页面URL', value: PageAttribute.PAGE_URL},
            {label: '页面标题', value: PageAttribute.PAGE_TITLE},
        ],
        conditions: [
            {field: 'assertType', type: 'equals', value: AssertType.PAGE_ARRTRIBUTE},
        ],
        required: true
    },
    {
        key: 'assertRelationship',
        label: '断言关系',
        type: 'select',
        placeholder: '请输入选择断言关系',
        options: [
            {label: '相等', value: AssertRelationship.EQUALS},
            {label: '不等', value: AssertRelationship.NOT_EQUALS},
            {label: '包含', value: AssertRelationship.CONTAINS},
            {label: '不包含', value: AssertRelationship.NOT_CONTAINS},
            {label: '大于', value: AssertRelationship.GT},
            {label: '小于', value: AssertRelationship.LT},
            {label: '大于等于', value: AssertRelationship.GE},
            {label: '小于等于', value: AssertRelationship.LE},
            {label: '正则匹配', value: AssertRelationship.REGULAR},
        ],
        conditions: [
            {
                field: 'assertType',
                type: 'in',
                values: [AssertType.ELEMENT_ARRTRIBUTE, AssertType.PAGE_ARRTRIBUTE, AssertType.CUSTOM]
            },
        ],
        required: true
    },
    {
        key: 'exceptValue',
        label: '期望值',
        type: 'input',
        placeholder: '请输入期望值',
        conditions: [
            {
                field: 'assertType',
                type: 'in',
                values: [AssertType.ELEMENT_ARRTRIBUTE, AssertType.PAGE_ARRTRIBUTE, AssertType.CUSTOM]
            },
        ],
        required: true
    },
]

// 后置断言操作（只是少了一个stepName）
const PostAssert = [
    {
        key: 'assertType',
        label: '断言类型',
        type: 'select',
        placeholder: '请选择断言类型',
        options: [
            {label: '元素存在', value: AssertType.ELEMENT_EXIST},
            {label: '元素不存在', value: AssertType.ELEMENT_NOT_EXIST},
            {label: '文字存在', value: AssertType.TEXT_EXIST},
            {label: '文字不存在', value: AssertType.TEXT_NOT_EXIST},
            {label: '元素属性', value: AssertType.ELEMENT_ARRTRIBUTE},
            {label: '页面属性', value: AssertType.PAGE_ARRTRIBUTE},
            {label: '自定义断言', value: AssertType.CUSTOM},
        ],
        required: true
    },
    {
        key: 'element',
        label: '目标元素',
        type: 'elementSelect',
        placeholder: '请选择目标元素',
        conditions: [
            {
                field: 'assertType',
                type: 'in',
                values: [AssertType.ELEMENT_EXIST, AssertType.ELEMENT_NOT_EXIST, AssertType.ELEMENT_ARRTRIBUTE]
            },
        ],
        required: true
    },
    {
        key: 'assertText',
        label: '断言文本',
        type: 'input',
        placeholder: '请输入断言文本',
        conditions: [
            {
                field: 'assertType',
                type: 'in',
                values: [AssertType.TEXT_EXIST, AssertType.TEXT_NOT_EXIST, AssertType.CUSTOM]
            },
        ],
        required: true
    },
    {
        key: 'elementAttribute',
        label: '元素属性',
        type: 'input',
        placeholder: '请输入属性名称',
        conditions: [
            {field: 'assertType', type: 'equals', value: AssertType.ELEMENT_ARRTRIBUTE},
        ],
        required: true
    },
    {
        key: 'pageAttribute',
        label: '页面属性',
        type: 'select',
        placeholder: '请输入属性名称',
        options: [
            {label: '页面名称', value: PageAttribute.PAGE_NAME},
            {label: '页面URL', value: PageAttribute.PAGE_URL},
            {label: '页面标题', value: PageAttribute.PAGE_TITLE},
        ],
        conditions: [
            {field: 'assertType', type: 'equals', value: AssertType.PAGE_ARRTRIBUTE},
        ],
        required: true
    },
    {
        key: 'assertRelationship',
        label: '断言关系',
        type: 'select',
        placeholder: '请输入选择断言关系',
        options: [
            {label: '相等', value: AssertRelationship.EQUALS},
            {label: '不等', value: AssertRelationship.NOT_EQUALS},
            {label: '包含', value: AssertRelationship.CONTAINS},
            {label: '不包含', value: AssertRelationship.NOT_CONTAINS},
            {label: '大于', value: AssertRelationship.GT},
            {label: '小于', value: AssertRelationship.LT},
            {label: '大于等于', value: AssertRelationship.GE},
            {label: '小于等于', value: AssertRelationship.LE},
            {label: '正则匹配', value: AssertRelationship.REGULAR},
        ],
        conditions: [
            {
                field: 'assertType',
                type: 'in',
                values: [AssertType.ELEMENT_ARRTRIBUTE, AssertType.PAGE_ARRTRIBUTE, AssertType.CUSTOM]
            },
        ],
        required: true
    },
    {
        key: 'exceptValue',
        label: '期望值',
        type: 'input',
        placeholder: '请输入期望值',
        conditions: [
            {
                field: 'assertType',
                type: 'in',
                values: [AssertType.ELEMENT_ARRTRIBUTE, AssertType.PAGE_ARRTRIBUTE, AssertType.CUSTOM]
            },
        ],
        required: true
    },
]


// 提取操作
const Extract = [
    {
        key: 'stepName',
        label: '步骤名称',
        type: 'input',
        placeholder: '请输入步骤名称',
        required: true
    },
    {
        key: 'variableName',
        label: '变量名',
        type: 'input',
        placeholder: '请输入变量名',
        required: true
    },
    {
        key: 'extractType',
        label: '提取类型',
        type: 'select',
        placeholder: '请选择提取类型',
        options: [
            {label: '元素', value: ExtractType.ELEMENT},
            {label: '页面', value: ExtractType.PAGE},
            // {label: '响应', value: ExtractType.RESPONSE},
            // {label: '元素提取', value: ExtractType.HEADER},
            // {label: '元素提取', value: ExtractType.COOKIE},
            // {label: '元素提取', value: ExtractType.VARIABLE},
            // {label: '元素提取', value: ExtractType.JAVASCRIPT},
        ],
        required: true
    },
    {
        key: 'element',
        label: '目标元素',
        type: 'elementSelect',
        placeholder: '请选择目标元素',
        conditions: [
            {field: 'extractType', type: 'equals', value: ExtractType.ELEMENT},
        ],
    },
    {
        key: 'elementExtractType',
        label: '元素提取类型',
        type: 'select',
        placeholder: '请选择元素提取类型',
        options: [
            {label: '元素文本', value: ElementExtractValueType.TEXT},
            {label: '元素属性', value: ElementExtractValueType.ATTRIBUTE},
            {label: 'HTML', value: ElementExtractValueType.HTML},
            {label: '元素Value', value: ElementExtractValueType.VALUE},
            {label: '元素数量', value: ElementExtractValueType.COUNT},

        ],
        conditions: [
            {field: 'extractType', type: 'equals', value: ExtractType.ELEMENT},
        ],
        required: true
    },
    {
        key: 'elementAttribute',
        label: '属性名称',
        type: 'input',
        placeholder: '请输入属性名称',
        conditions: [
            {field: 'extractType', type: 'equals', value: ExtractType.ELEMENT},
            {field: 'elementExtractType', type: 'equals', value: ElementExtractValueType.ATTRIBUTE},
        ],
        required: true
    },
    {
        key: 'pageExtractType',
        label: '页面提取类型',
        type: 'select',
        placeholder: '请选择元素提取类型',
        options: [
            {label: '页面URL', value: PageExtractValueType.URL},
            {label: '页面标题', value: PageExtractValueType.TITLE},
            // {label: '页面文本', value: PageExtractValueType.TEXT},
            {label: 'COOKIE', value: PageExtractValueType.COOKIE},

        ],
        conditions: [
            {field: 'extractType', type: 'equals', value: ExtractType.PAGE},
        ],
        required: true
    },
    {
        key: 'cookieName',
        label: 'Cookie名称',
        type: 'input',
        placeholder: '请输入cookie名称（置空默认提取全部）',
        conditions: [
            {field: 'extractType', type: 'equals', value: ExtractType.PAGE},
            {field: 'pageExtractType', type: 'equals', value: PageExtractValueType.COOKIE}
        ],
    }
]

// 后置抽取操作（只是少了一个stepName）
const PostExtract = [

    {
        key: 'variableName',
        label: '变量名',
        type: 'input',
        placeholder: '请输入变量名',
        required: true
    },
    {
        key: 'extractType',
        label: '提取类型',
        type: 'select',
        placeholder: '请选择提取类型',
        options: [
            {label: '元素', value: ExtractType.ELEMENT},
            {label: '页面', value: ExtractType.PAGE},
            // {label: '响应', value: ExtractType.RESPONSE},
            // {label: '元素提取', value: ExtractType.HEADER},
            // {label: '元素提取', value: ExtractType.COOKIE},
            // {label: '元素提取', value: ExtractType.VARIABLE},
            // {label: '元素提取', value: ExtractType.JAVASCRIPT},
        ],
        required: true
    },
    {
        key: 'element',
        label: '目标元素',
        type: 'elementSelect',
        placeholder: '请选择目标元素',
        conditions: [
            {field: 'extractType', type: 'equals', value: ExtractType.ELEMENT},
        ],
    },
    {
        key: 'elementExtractType',
        label: '元素提取类型',
        type: 'select',
        placeholder: '请选择元素提取类型',
        options: [
            {label: '元素文本', value: ElementExtractValueType.TEXT},
            {label: '元素属性', value: ElementExtractValueType.ATTRIBUTE},
            {label: 'HTML', value: ElementExtractValueType.HTML},
            {label: '元素Value', value: ElementExtractValueType.VALUE},
            {label: '元素数量', value: ElementExtractValueType.COUNT},

        ],
        conditions: [
            {field: 'extractType', type: 'equals', value: ExtractType.ELEMENT},
        ],
        required: true
    },
    {
        key: 'elementAttribute',
        label: '属性名称',
        type: 'input',
        placeholder: '请输入属性名称',
        conditions: [
            {field: 'extractType', type: 'equals', value: ExtractType.ELEMENT},
            {field: 'elementExtractType', type: 'equals', value: ElementExtractValueType.ATTRIBUTE},
        ],
        required: true
    },
    {
        key: 'pageExtractType',
        label: '页面提取类型',
        type: 'select',
        placeholder: '请选择元素提取类型',
        options: [
            {label: '页面URL', value: PageExtractValueType.URL},
            {label: '页面标题', value: PageExtractValueType.TITLE},
            {label: '页面文本', value: PageExtractValueType.TEXT},
            {label: 'COOKIE', value: PageExtractValueType.COOKIE},

        ],
        conditions: [
            {field: 'extractType', type: 'equals', value: ExtractType.PAGE},
        ],
        required: true
    },
]

// if判断
const IfAssert = [
    {
        key: 'stepName',
        label: '步骤名称',
        type: 'input',
        placeholder: '请输入步骤名称',
        required: true
    },
    {
        key: 'conditionalRelationship',
        label: '条件关系',
        type: 'select',
        placeholder: '请选择条件关系',
        options: [
            {label: '且', value: ConditionalRelationship.AND},
            {label: '或', value: ConditionalRelationship.OR},
        ],
        required: true
    },
    {
        key: 'conditionList',
        label: '条件列表',
        type: 'assertList',
        placeholder: '请填写条件关系',
    },

]

// iframe
const SwitchIframe = [
    {
        key: 'stepName',
        label: '步骤名称',
        type: 'input',
        placeholder: '请输入步骤名称',
        required: true
    },
    {
        key: 'switchIframeType',
        label: 'iframe切换类型',
        type: 'select',
        placeholder: '请选择iframe切换类型',
        options: [
            {label: 'ID', value: SwitchIframeType.ID},
            {label: 'name', value: SwitchIframeType.NAME},
            {label: '索引', value: SwitchIframeType.INDEX},
            {label: '退出', value: SwitchIframeType.EXIT},
            {label: '网址', value: SwitchIframeType.URL},
            {label: '元素定位', value: SwitchIframeType.ELEMENT},
        ],
        required: true
    },
    {
        key: 'element',
        label: 'iframe元素',
        type: 'elementSelect',
        placeholder: '请输入iframe元素',
        conditions: [
            {field: 'switchIframeType', type: 'equals', value: SwitchIframeType.ELEMENT},
        ],
        required: true
    },
    {
        key: 'url',
        label: '网址',
        type: 'input',
        placeholder: '请输入iframe地址',
        conditions: [
            {field: 'switchIframeType', type: 'equals', value: SwitchIframeType.URL},
        ],
        required: true
    },
    {
        key: 'iframeName',
        label: 'iframe名称',
        type: 'input',
        placeholder: '请输入iframe名称',
        conditions: [
            {field: 'switchIframeType', type: 'equals', value: SwitchIframeType.NAME},
        ],
        required: true
    },
    {
        key: 'iframeIndex',
        label: 'iframe索引',
        type: 'number',
        placeholder: '请输入iframe索引',
        conditions: [
            {field: 'switchIframeType', type: 'equals', value: SwitchIframeType.INDEX},
        ],
        required: true
    },
    {
        key: 'iframeId',
        label: 'iframeID',
        type: 'input',
        placeholder: '请输入iframeID',
        conditions: [
            {field: 'switchIframeType', type: 'equals', value: SwitchIframeType.ID},
        ],
        required: true
    }
]


// 步骤设置
const StepSetting = [
    {
        key: 'isSetting',
        label: '是否开启设置',
        type: 'switch',
        options: [
            {label: '是', value: 1},
            {label: '否', value: 0},
        ],
        default: 0,
        required: true
    },
    {
        key: 'preExecuteWaitingTime',
        label: '执行前等待时间',
        type: 'number',
        min: 0,
        conditions: [
            {field: 'isSetting', type: 'equals', value: 1},
        ],
        default: 0,
        required: true
    },
    {
        key: 'waitingTimeAfterExecution',
        label: '执行后等待时间',
        type: 'number',
        min: 0,
        conditions: [
            {field: 'isSetting', type: 'equals', value: 1},
        ],
        default: 0,
        required: true
    }
    ,
    {
        key: 'timeout',
        label: '超时时间',
        type: 'number',
        min: 0,
        conditions: [
            {field: 'isSetting', type: 'equals', value: 1},
        ],
        default: 0,
        required: true
    },
    {
        key: 'errorHandlingStrategy',
        label: '遇到错误的处理策略',
        type: 'select',
        options: [
            {label: '忽略', value: StepErrorHandleStrategy.IGNORE},
            {label: '停止', value: StepErrorHandleStrategy.STOP},
            {label: '重试（暂时搁置）', value: StepErrorHandleStrategy.RETRY, disabled: true},
        ],
        conditions: [
            {field: 'isSetting', type: 'equals', value: 1},
        ],
        default: StepErrorHandleStrategy.STOP,
        required: true
    }
    ,
    {
        key: 'screenshotConfiguration',
        label: '截图配置',
        type: 'select',
        options: [
            {label: '截图', value: ScreenshotConfig.SCREENSHOT},
            {label: '不截图', value: ScreenshotConfig.NOT_SCREENSHOT},
            {label: '出现异常截图', value: ScreenshotConfig.SCREENSHOT_EXCEPTION},
        ],
        conditions: [
            {field: 'isSetting', type: 'equals', value: 1},
        ],
        default: ScreenshotConfig.SCREENSHOT,
        required: true
    }
    ,
    {
        key: 'pageInformation',
        label: '页面信息',
        type: 'input',
        default: '',
        conditions: [
            {field: 'isSetting', type: 'equals', value: 1},
        ],
        required: true
    }
]

// for循环
const For = [
    {
        key: 'stepName',
        label: '步骤名称',
        type: 'input',
        placeholder: '请输入步骤名称',
        required: true
    },
    {
        key: 'cycleType',
        label: '循环类型',
        type: 'select',
        placeholder: '请选择循环类型',
        options: [
            {label: '固定次数', value: ForCycleType.TIMES},
            {label: '循环文件(待做...)', value: ForCycleType.FILES, disabled: true},
        ],
        default: ForCycleType.TIMES,
        required: true
    },
    {
        key: 'cycleTimes',
        label: '循环次数',
        type: 'number',
        min: 1,
        max: 9999,
        placeholder: '请输入循环次数',
        conditions: [
            {field: 'cycleType', type: 'equals', value: ForCycleType.TIMES},
        ],
        default: 1,
        required: true
    },
    // todo 待做
    /* {
         key: 'cycleFiles',
         label: '循环文件',
         type: 'uploadFile',
         min: 1,
         max: 9999,
         placeholder: '请输入循环次数',
         conditions: [
             {field: 'cycleType', type: 'equals', value: ForCycleType.FILES},
         ],
         fileList: [],
         default: 1,
         required: true
     }*/
]

// while循环
const While = [
    {
        key: 'stepName',
        label: '步骤名称',
        type: 'input',
        placeholder: '请输入步骤名称',
        required: true
    },
    {
        key: 'conditionalRelationship',
        label: '条件关系',
        type: 'select',
        placeholder: '请选择条件关系',
        options: [
            {label: '且', value: ConditionalRelationship.AND},
            {label: '或', value: ConditionalRelationship.OR},
        ],
        default: ConditionalRelationship.AND,
        required: true
    },
    {
        key: 'conditionList',
        label: '条件列表',
        type: 'assertList',
        placeholder: '请填写条件关系',
    },
    {
        key: 'maxLoopCount',
        label: '最大循环次数',
        type: 'number',
        min: 1,
        max: 9999,
        placeholder: '请输入最大循环次数',
        default: 1,
        required: true
    },

]


const Wait = [
    {
        key: 'stepName',
        label: '步骤名称',
        type: 'input',
        placeholder: '请输入步骤名称',
        required: true
    },
    {
        key: 'waitType',
        label: '等待类型',
        type: 'select',
        placeholder: '请选择等待类型',
        options: [
            {label: '固定时长', value: WaitType.TIME},
        ],
        default: WaitType.TIME,
        required: true
    },
    {
        key: 'waitTime',
        label: '等待时长',
        type: 'number',
        placeholder: '请填写等待时长',
        conditions: [
            {field: 'waitType', type: 'equals', value: WaitType.TIME},
        ],
        default: 0,
        required: true
    },
]

const Dialog = [
    {
        key: 'stepName',
        label: '步骤名称',
        type: 'input',
        placeholder: '请输入步骤名称',
        required: true
    },
    {
        key: 'dialogOperationType',
        label: '弹窗操作类型',
        type: 'select',
        placeholder: '请选择等待类型',
        options: [
            {label: '确认', value: DialogOperationType.ACCEPT},
            {label: '取消', value: DialogOperationType.DISMISS},
            {label: '获取对话框消息', value: DialogOperationType.MESSAGE}
        ],
        default: DialogOperationType.ACCEPT,
        required: true
    },
    {
        key: 'dialogMessage',
        label: '对话框消息',
        type: 'input',
        placeholder: '请填写对话框消息',
        conditions: [
            {field: 'dialogOperationType', type: 'equals', value: DialogOperationType.MESSAGE},
        ],
        default: '',
        required: true
    }
]

// API请求步骤使用专用编辑器（ApiRequestStepEditor），不通过DynamicForm渲染
const ApiRequest: any[] = [];

// SQL步骤使用专用编辑器（SqlRequestStepEditor），不通过DynamicForm渲染
const SqlRequest: any[] = [];

// 脚本步骤使用专用编辑器（ScriptStepEditor），不通过DynamicForm渲染
const ScriptRequest: any[] = [];

// 文件上传
const FileUpload = [
    {
        key: 'stepName',
        label: '步骤名称',
        type: 'input',
        placeholder: '请输入步骤名称',
        required: true
    },
    {
        key: 'element',
        label: '文件输入框元素',
        type: 'elementSelect',
        placeholder: '请选择文件输入框元素',
        required: true
    },
    {
        key: 'fileIds',
        label: '上传文件',
        type: 'uploadFile',
        placeholder: '请选择要上传的文件',
        required: true
    },
]

// 元素DOM操作
const ElementDomOperation = [
    {
        key: 'stepName',
        label: '步骤名称',
        type: 'input',
        placeholder: '请输入步骤名称',
        required: true
    },
    {
        key: 'operationType',
        label: '操作类型',
        type: 'select',
        placeholder: '请选择操作类型',
        options: [
            {label: '设置属性', value: ElementDomOperationType.SET_ATTRIBUTE},
            {label: '移除属性', value: ElementDomOperationType.REMOVE_ATTRIBUTE},
            {label: '设置内联样式', value: ElementDomOperationType.SET_STYLE},
            {label: '追加CSS类', value: ElementDomOperationType.ADD_CLASS},
            {label: '移除CSS类', value: ElementDomOperationType.REMOVE_CLASS},
            {label: '触发原生事件', value: ElementDomOperationType.DISPATCH_EVENT},
        ],
        default: ElementDomOperationType.SET_ATTRIBUTE,
        required: true
    },
    {
        key: 'element',
        label: '目标元素',
        type: 'elementSelect',
        placeholder: '请选择目标元素',
        required: true
    },
    // 属性操作
    {
        key: 'attributeName',
        label: '属性名',
        type: 'input',
        placeholder: '请输入属性名称，如 data-testid、readonly、disabled',
        conditions: [
            {
                field: 'operationType',
                type: 'in',
                values: [ElementDomOperationType.SET_ATTRIBUTE, ElementDomOperationType.REMOVE_ATTRIBUTE]
            },
        ],
        required: true
    },
    {
        key: 'attributeValue',
        label: '属性值',
        type: 'input',
        placeholder: '请输入属性值，支持 {{变量名}}，允许空字符串',
        conditions: [
            {field: 'operationType', type: 'equals', value: ElementDomOperationType.SET_ATTRIBUTE},
        ],
        required: true
    },
    // 样式操作
    {
        key: 'styleName',
        label: 'CSS属性名',
        type: 'input',
        placeholder: '如 display、color、background-color、z-index',
        conditions: [
            {field: 'operationType', type: 'equals', value: ElementDomOperationType.SET_STYLE},
        ],
        required: true
    },
    {
        key: 'styleValue',
        label: 'CSS属性值',
        type: 'input',
        placeholder: '如 block、red、#f97316、9999，支持 {{变量名}}',
        conditions: [
            {field: 'operationType', type: 'equals', value: ElementDomOperationType.SET_STYLE},
        ],
        required: true
    },
    {
        key: 'stylePriority',
        label: '优先级',
        type: 'select',
        placeholder: '请选择优先级',
        options: [
            {label: '普通', value: StylePriority.NORMAL},
            {label: '!important', value: StylePriority.IMPORTANT},
        ],
        default: StylePriority.NORMAL,
        conditions: [
            {field: 'operationType', type: 'equals', value: ElementDomOperationType.SET_STYLE},
        ],
        required: true
    },
    // 类名操作
    {
        key: 'classNames',
        label: 'CSS类名',
        type: 'inputTag',
        placeholder: '输入类名后回车添加，可添加多个',
        conditions: [
            {
                field: 'operationType',
                type: 'in',
                values: [ElementDomOperationType.ADD_CLASS, ElementDomOperationType.REMOVE_CLASS]
            },
        ],
        required: true
    },
    // 事件操作
    {
        key: 'eventType',
        label: '事件类型',
        type: 'select',
        placeholder: '请选择事件类型',
        options: [
            {label: 'input', value: 'input'},
            {label: 'change', value: 'change'},
            {label: 'blur', value: 'blur'},
            {label: 'focus', value: 'focus'},
            {label: 'click', value: 'click'},
        ],
        default: 'input',
        conditions: [
            {field: 'operationType', type: 'equals', value: ElementDomOperationType.DISPATCH_EVENT},
        ],
        required: true
    },
    {
        key: 'eventBubbles',
        label: '是否冒泡 (bubbles)',
        type: 'select',
        placeholder: '请选择是否冒泡',
        options: [
            {label: 'true', value: true},
            {label: 'false', value: false},
        ],
        default: true,
        conditions: [
            {field: 'operationType', type: 'equals', value: ElementDomOperationType.DISPATCH_EVENT},
        ],
        required: true
    },
    {
        key: 'eventCancelable',
        label: '是否可取消 (cancelable)',
        type: 'select',
        placeholder: '请选择是否可取消',
        options: [
            {label: 'true', value: true},
            {label: 'false', value: false},
        ],
        default: true,
        conditions: [
            {field: 'operationType', type: 'equals', value: ElementDomOperationType.DISPATCH_EVENT},
        ],
        required: true
    },
]


export {
    OpenPage,
    ClosePage,
    SwitchTab,
    Forward,
    Back,
    Refresh,
    ClickForm,
    Hover,
    Drag,
    Keyboard,
    Assert,
    PostAssert,
    IfAssert,
    SwitchIframe,
    Extract,
    PostExtract,
    StepSetting,
    For,
    While,
    Wait,
    Dialog,
    ApiRequest,
    SqlRequest,
    ScriptRequest,
    FileUpload,
    ElementDomOperation,
};

// 集中注册所有 schema
export const STEP_REGISTRY = {
    "OPEN_PAGE": OpenPage,
    "CLOSE_PAGE": ClosePage,
    "SWITCH_TAB": SwitchTab,
    "FORWARD": Forward,
    "BACK": Back,
    "REFRESH": Refresh,
    "CLICK": ClickForm,
    "HOVER": Hover,
    "DRAG": Drag,
    "KEYBOARD": Keyboard,
    "ASSERT": Assert,
    "IF": IfAssert,
    "IFRAME": SwitchIframe,
    "EXTRACT": Extract,
    "FOR": For,
    "WHILE": While,
    "WAIT": Wait,
    "DIALOG": Dialog,
    "API_REQUEST": ApiRequest,
    "SQL": SqlRequest,
    "SCRIPT": ScriptRequest,
    "FILE_UPLOAD": FileUpload,
    "ELEMENT_DOM_OPERATION": ElementDomOperation,

} as const;

// 类型：自动推断所有可用的标识
export type StepKey = keyof typeof STEP_REGISTRY;


// 获取步骤配置的函数
export function getStepConfig(stepType: StepType) {
    return STEP_REGISTRY[stepType];
}