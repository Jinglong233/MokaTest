import axios from "axios";

export interface RecordCandidate {
    locatorType: string;
    locatorValue: string;
    score: number;
}

export interface RecordStepDraft {
    stepType: string;
    stepName: string;
    stepDetail: any;
    isPassword?: boolean;
}

export interface RecordImportResult {
    steps: RecordStepDraft[];
    warnings: string[];
    skipped: Record<string, number>;
}

export interface RecordSaveDTO {
    projectId: number | string;
    name?: string;           // 追加到已有场景（sceneId）时不必填
    parentId?: number;
    description?: string;
    steps: RecordStepDraft[];
    sceneId?: number;        // 追加到已有场景 = 继续录制
    batchId?: string;        // 幂等键，重复提交不产生重复步骤
    syncElementsToLibrary?: boolean;
    elementParentId?: number;
}

/**
 * 上传录制 JSON 文件，解析为草稿步骤
 */
export function importRecord(file: File, projectId: string) {
    const form = new FormData();
    form.append('file', file);
    form.append('projectId', projectId);
    return axios.post<RecordImportResult>('/api/record/import', form, {
        headers: {
            'Content-Type': 'multipart/form-data'
        }
    });
}

/**
 * 保存确认后的草稿步骤，生成 UI 场景
 */
export function saveRecord(data: RecordSaveDTO) {
    return axios.post<number>('/api/record/save', data);
}
