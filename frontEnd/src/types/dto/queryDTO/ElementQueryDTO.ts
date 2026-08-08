import {BasePageQueryDTO} from "@/types/dto/queryDTO/BasePageQueryDTO";
import {useProjectStore} from "@/store";

const projectStore = useProjectStore();

export class ElementQueryDTO extends BasePageQueryDTO {

    /**
     * 父id
     */
    parentId: Number = 0;

    /**
     * 元素名称
     */
    elementName: string = '';

    /**
     * 类型
     */
    elementType: String = "ELEMENT";

    /**
     * 定位类型
     */
    locatorType: String = "";


    /**
     * 所属项目ID
     */
    projectId: string | null = projectStore.getProjectId;


    /**
     * 是否共享元素(1-共享，0-私有)
     */
    isShared: Number = 0;
}
