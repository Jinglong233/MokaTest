import {ClickStepDTO} from "@/types/dto/stepDTO/ClickStepDTO";
import {DragElementStepDTO} from "@/types/dto/stepDTO/DragElementStepDTO";
import {KeyboardStepDTO} from "@/types/dto/stepDTO/KeyboardStepDTO";
import {OpenPageStepDTO} from "@/types/dto/stepDTO/OpenPageStepDTO";
import {WaitStepDTO} from "@/types/dto/stepDTO/WaitStepDTO";
import {SwitchTabStepDTO} from "@/types/dto/stepDTO/SwitchTabStepDTO";
import {AssertStepDTO} from "@/types/dto/stepDTO/AssertStepDTO";
import {ExtractStepDTO} from "@/types/dto/stepDTO/ExtractStepDTO";
import {SettingDTO} from "@/types/dto/SettingDTO";
import {ClosePageStepDTO} from "@/types/dto/stepDTO/ClosePageStepDTO";
import {ForwardStepDTO} from "@/types/dto/stepDTO/ForwardStepDTO";
import {BackStepDTO} from "@/types/dto/stepDTO/BackStepDTO";
import {RefreshStepDTO} from "@/types/dto/stepDTO/RefreshStepDTO";
import {IframeStepDTO} from "@/types/dto/stepDTO/IframeStepDTO";
import {IFStepDTO} from "@/types/dto/stepDTO/IFStepDTO";
import {ForStepDTO} from "@/types/dto/stepDTO/ForStepDTO";
import {WhileStepDTO} from "@/types/dto/stepDTO/WhileStepDTO";
import {HoverStepDTO} from "@/types/dto/stepDTO/HoverStepDTO";
import {DialogStepDTO} from "@/types/dto/stepDTO/DialogStepDTO";
import {ApiRequestStepDTO} from "@/types/dto/stepDTO/ApiRequestStepDTO";
import {SqlRequestStepDTO} from "@/types/dto/stepDTO/SqlRequestStepDTO";
import {ScriptStepDTO} from "@/types/dto/stepDTO/ScriptStepDTO";
import {UploadFileStepDTO} from "@/types/dto/stepDTO/UploadFileStepDTO";
import {ElementDomOperationStepDTO} from "@/types/dto/stepDTO/ElementDomOperationStepDTO";


export type StepType =
    | ClickStepDTO
    | HoverStepDTO
    | DragElementStepDTO
    | KeyboardStepDTO
    | OpenPageStepDTO
    | WaitStepDTO
    | SwitchTabStepDTO
    | AssertStepDTO
    | ExtractStepDTO
    | SettingDTO
    | ClosePageStepDTO
    | ApiRequestStepDTO
    | SqlRequestStepDTO
    | UploadFileStepDTO
    | ElementDomOperationStepDTO


// 定义步骤类型到类的映射
const stepConstructors = {
    // 浏览器操作
    open_page: new OpenPageStepDTO(),
    close_page: new ClosePageStepDTO(),
    switch_tab: new SwitchTabStepDTO(),
    forward: new ForwardStepDTO(),
    back: new BackStepDTO(),
    refresh: new RefreshStepDTO(),
    // 鼠标操作
    click: new ClickStepDTO(),
    // 悬停
    hover: new HoverStepDTO(),
    drag: new DragElementStepDTO(),
    // 键盘操作
    keyboard: new KeyboardStepDTO(),
    // 等待
    wait: new WaitStepDTO(),
    // 断言
    assert: new AssertStepDTO(),
    // 数据提取
    extract: new ExtractStepDTO(),
    // if判断
    if: new IFStepDTO(),
    // for循环
    for: new ForStepDTO(),
    // while循环
    while: new WhileStepDTO(),

    // iframe操作
    iframe: new IframeStepDTO(),
    setting: new SettingDTO(),

    // dialog操作
    dialog: new DialogStepDTO(),
    // API请求
    api_request: new ApiRequestStepDTO(),
    // SQL查询
    sql: new SqlRequestStepDTO(),
    // JS脚本
    script: new ScriptStepDTO(),
    // 文件上传
    file_upload: new UploadFileStepDTO(),
    // 元素DOM操作
    element_dom_operation: new ElementDomOperationStepDTO(),
};

export const createStep = (type: keyof typeof stepConstructors) => {
    return stepConstructors[type];
}