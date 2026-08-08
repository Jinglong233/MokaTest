// 步骤类型
export enum StepType {
    // 点击
    CLICK = "CLICK",

    // 悬停
    HOVER = "HOVER",

    // 断言
    ASSERT = "ASSERT",

    // 关闭页面
    CLOSE_PAGE = "CLOSE_PAGE",

    // 打开页面
    OPEN_PAGE = "OPEN_PAGE",

    // 前进
    FORWARD = "FORWARD",

    // 后退
    BACK = "BACK",

    // 刷新
    REFRESH = "REFRESH",

    // 拖拽元素
    DRAG = "DRAG",

    // 关联提取
    EXTRACT = "EXTRACT",

    // 键盘操作
    KEYBOARD = "KEYBOARD",

    // 切换tab
    SWITCH_TAB = "SWITCH_TAB",

    // 等待
    WAIT = "WAIT",

    // if判断
    IF = "IF",

    // while循环
    WHILE = "WHILE",

    // for循环
    FOR = "FOR",

    // iframe操作
    IFRAME = "IFRAME",

    // API请求
    API_REQUEST = "API_REQUEST",

    // SQL查询
    SQL = "SQL",

    // JS脚本
    SCRIPT = "SCRIPT",

    // 文件上传
    FILE_UPLOAD = "FILE_UPLOAD",

    // 元素DOM操作
    ELEMENT_DOM_OPERATION = "ELEMENT_DOM_OPERATION",
}

// 中文映射对象
export const StepTypeChinese = {
    [StepType.OPEN_PAGE]: "打开页面",
    [StepType.CLICK]: "点击",
    [StepType.HOVER]: "悬停",
    [StepType.ASSERT]: "断言",
    [StepType.CLOSE_PAGE]: "关闭页面",
    [StepType.FORWARD]: "前进",
    [StepType.BACK]: "后退",
    [StepType.REFRESH]: "刷新",
    [StepType.DRAG]: "拖拽元素",
    [StepType.EXTRACT]: "关联提取",
    [StepType.KEYBOARD]: "键盘操作",
    [StepType.SWITCH_TAB]: "切换标签页",
    [StepType.WAIT]: "等待",
    [StepType.IF]: "条件判断",
    [StepType.WHILE]: "WHILE循环",
    [StepType.FOR]: "FOR循环",
    [StepType.IFRAME]: "iframe操作",
    [StepType.API_REQUEST]: "HTTP请求",
    [StepType.SQL]: "SQL查询",
    [StepType.SCRIPT]: "脚本",
    [StepType.FILE_UPLOAD]: "上传文件",
    [StepType.ELEMENT_DOM_OPERATION]: "元素DOM操作",
} as const;

// 反向映射：中文到StepType
export const ChineseToStepType: { [key: string]: StepType } = {
    "打开页面": StepType.OPEN_PAGE,
    "点击": StepType.CLICK,
    "悬停": StepType.HOVER,
    "断言": StepType.ASSERT,
    "关闭页面": StepType.CLOSE_PAGE,
    "前进": StepType.FORWARD,
    "后退": StepType.BACK,
    "刷新": StepType.REFRESH,
    "拖拽元素": StepType.DRAG,
    "关联提取": StepType.EXTRACT,
    "键盘操作": StepType.KEYBOARD,
    "切换标签页": StepType.SWITCH_TAB,
    "等待": StepType.WAIT,
    "条件判断": StepType.IF,
    "WHILE循环": StepType.WHILE,
    "FOR循环": StepType.FOR,
    "iframe操作": StepType.IFRAME,
    "HTTP请求": StepType.API_REQUEST,
    "SQL查询": StepType.SQL,
    "脚本": StepType.SCRIPT,
    "上传文件": StepType.FILE_UPLOAD,
    "元素DOM操作": StepType.ELEMENT_DOM_OPERATION,
};

// 获取中文描述
export function getStepTypeChinese(stepType: StepType): string {
    return stepType ? StepTypeChinese[stepType] || stepType : '未知';
}

// 从中文获取StepType
export function getStepTypeFromChinese(chinese: string): StepType | undefined {
    return ChineseToStepType[chinese];
}

// 判断是否为指定步骤类型（原有功能保持不变）
export function isSpecifiedStepType(value: string): boolean {
    const specifiedTypes = [StepType.IF, StepType.WHILE, StepType.FOR];
    return specifiedTypes.includes(value as StepType);
}

// 获取所有中文选项（用于下拉选择等场景）
export function getStepTypeChineseOptions(): { value: StepType; label: string }[] {
    return Object.entries(StepTypeChinese).map(([value, label]) => ({
        value: value as StepType,
        label
    }));
}