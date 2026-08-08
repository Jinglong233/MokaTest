export enum SceneExecuteType {
    // 顺序
    ORDER="ORDER",
    // 并行
    PARALLEL="PARALLEL"
}


/**
 * 根据SceneExecuteType返回对应的中文描述
 * @param sceneExecuteType 场景执行类型
 * @returns 对应的中文描述
 */
export function getSceneExecuteTypeDescription(sceneExecuteType: SceneExecuteType): string {
    switch (sceneExecuteType) {
        case SceneExecuteType.ORDER:
            return '顺序';
        case SceneExecuteType.PARALLEL:
            return '并行';
        default:
            return '未知执行类型';
    }
}

