import {TestStep} from "@/types/domain/TestStep";

export class AddAdjacentStepDTO {
    targetStepId: number = 0;
    addStep: TestStep = null;

    /**
     * 是否是子步骤
     */
    isChildren: boolean = false;
}