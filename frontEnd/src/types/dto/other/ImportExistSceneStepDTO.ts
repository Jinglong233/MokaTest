export class ImportExistSceneStepDTO {
    /**
     * 目标场景Id
     */
    targetSceneId: number;

    /**
     * 需要添加的场景id集合（旧逻辑）
     */
    sourceSceneIds: number[];

    /**
     * 需要添加的具体步骤id集合（新逻辑）
     */
    sourceStepIds: number[];
}
