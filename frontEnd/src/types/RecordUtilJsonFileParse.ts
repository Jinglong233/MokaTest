// 录制文件解析
import {OpenPageStepDTO} from "@/types/dto/stepDTO/OpenPageStepDTO";
import {ClickStepDTO} from "@/types/dto/stepDTO/ClickStepDTO";
import {ClickType} from "@/types/enum/click/ClickType";
import {KeyboardStepDTO} from "@/types/dto/stepDTO/KeyboardStepDTO";
import {ElementLocatorType} from "@/types/enum/element/ElementLocatorType";
import {StepType} from "@/types/dto/StepDetailDTO";
import {KeyboardInputType} from "@/types/enum/keyboard/KeyboardInputType";
import {KeyboardKey} from "@/types/enum/keyboard/KeyboardKey";
import {getStepTypeChinese} from "@/types/enum/StepType";

export class RecordUtilJsonFileParse {
    // 获取
    static getStepType(stepJson: string) {
        const step = JSON.parse(stepJson);
        return step.stepType;
    }

    static getStepInfoByKey(stepJson: string, key: string) {
        const step = JSON.parse(stepJson);
        return step[key];
    }

    // 解析定位
    static parseLocator(stepJson: string) {
        const locator = RecordUtilJsonFileParse.getStepInfoByKey(stepJson, 'locator');
        // 暂时只不支持next多个定位
        return {
            kind: locator['kind'] === 'default' ? 'xpath' : locator['kind'],
            body: locator['body'],
            name: locator['options']['name'],
        }
    }

    // 解析点击信息
    static parseClickInfo(stepJson: string) {
        const clickInfo = RecordUtilJsonFileParse.getStepInfoByKey(stepJson, 'button');
        return {}
    }


    static getElementLocator = (stepJson: string, result: any) => {
        const locator = RecordUtilJsonFileParse.parseLocator(stepJson);
        switch (locator.kind) {
            case 'xpath':
                result.element.customLocator.locatorType = locator.kind.toUpperCase();
                result.element.customLocator.locatorValue = locator.body;
                break;
            case 'role':
                result.element.customLocator.locatorType = ElementLocatorType.ROLE;
                result.element.customLocator.locatorValue = locator.body + "::" + locator.name ? '' : locator.name.trim();
                break;
        }
    }


    static buildStepInfo = (stepJson: string) => {
        // 获取stepType
        const stepType = RecordUtilJsonFileParse.getStepInfoByKey(stepJson, 'name');

        let result = null;


        if (stepType === 'navigate' || stepType === 'openPage') {
            result = new OpenPageStepDTO();
            result.url = RecordUtilJsonFileParse.getStepInfoByKey(stepJson, 'url');
        } else if (stepType === 'click') {
            result = new ClickStepDTO();

            // 赋值定位
            RecordUtilJsonFileParse.getElementLocator(stepJson, result);


            // 判断左右击
            let buttonClick = RecordUtilJsonFileParse.getStepInfoByKey(stepJson, 'button');
            if (buttonClick === 'left') {
                // 获取点击次数
                let clickCount = RecordUtilJsonFileParse.getStepInfoByKey(stepJson, 'clickCount');
                if (clickCount > 1) {
                    result.clickType = ClickType.DOUBLE_CLICK;
                } else {
                    result.clickType = ClickType.SINGLE_CLICK;
                }
            }
            if (buttonClick === 'right') {
                result.clickType = ClickType.RIGHT_CLICK;
            }
        } else if (stepType === 'fill') {
            result = new KeyboardStepDTO();
            // 赋值定位
            RecordUtilJsonFileParse.getElementLocator(stepJson, result);
            result.inputType = KeyboardInputType.NORMAL;
            result.inputValue = RecordUtilJsonFileParse.getStepInfoByKey(stepJson, 'text');

        } else if (stepType === 'press') {
            result = new KeyboardStepDTO();
            result.inputType = KeyboardInputType.KEYBOARD;
            // 赋值定位
            RecordUtilJsonFileParse.getElementLocator(stepJson, result);
            // 目前只支持一个ENTER回车
            const inputKey = RecordUtilJsonFileParse.getStepInfoByKey(stepJson, 'key')
            result.keyboardKey = inputKey.toUpperCase();
        }
        return result;
    }


// 构建步骤列表
    static buildSceneInfoStepList = (stepList: any[]) => {
        const stepListDTO = [] as any;
        if (stepList.length > 0) {
            stepList.forEach((item: any, index: number) => {
                const dto = RecordUtilJsonFileParse.buildStepInfo(JSON.stringify(item)) as any;
                dto.stepName = getStepTypeChinese(dto.stepType);
                if (dto) {
                    dto.orderIndex = index + 1;
                    stepListDTO.push(dto);
                }
            })
        }
        return stepListDTO;
    }
}


