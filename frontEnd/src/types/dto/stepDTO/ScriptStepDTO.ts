import {StepBaseDTO} from "@/types/dto/base/StepBaseDTO";

/**
 * 脚本步骤DTO
 *
 * 在场景变量池上下文中执行 JS（GraalJS 沙箱）：
 * - context.getVariable / setVariable 读写场景变量
 * - console.log 输出日志
 * - context.assertCondition 自定义断言
 */
export class ScriptStepDTO extends StepBaseDTO {
    stepType: string = 'SCRIPT';

    /**
     * JS 脚本内容
     */
    scriptContent: string = '';
}
