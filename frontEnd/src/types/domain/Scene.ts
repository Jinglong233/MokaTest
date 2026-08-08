import {SceneConfig} from "@/types/domain/SceneSetting";

export interface Scene {
    id?: string,       // 场景id
    projectId?: string,       // 所属项目id
    name?: string,       // 场景名称
    parentId?: string,       // 父场景id
    description?: string,       // 场景描述
    sort?: number,       // 排序
    sceneType?: string,       // 场景类型
    sceneCategory?: string,   // 场景分类：UI/API
    sceneSetting?: SceneConfig,   // 场景配置
    createAt?: number,       // 创建时间
    createUserId?: string,       // 创建人id
    updateUserId?: string,       // 更新人id
}