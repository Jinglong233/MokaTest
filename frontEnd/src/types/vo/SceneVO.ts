import {Scene} from "@/types/domain/Scene";

// 场景视图对象
export interface SceneVO extends Scene{
    children?: SceneVO[];
}