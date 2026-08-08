import {BasePageQueryDTO} from "./BasePageQueryDTO";


export class UserQueryDTO extends BasePageQueryDTO {

    /**
     * 用户名，唯一
     */
    username?: string;

    /**
     * 昵称
     */
    nickname?: string;

    /**
     * 手机号
     */
    phone?: string;

    /**
     * 邮箱
     */
    email?: string;

    /**
     * 状态：0-禁用，1-正常
     */
    status?: number;

    /**
     * 项目ID（可选）。传入时按项目维度收窄可选人（项目成员 ∪ 团队成员，剔除超管）
     */
    projectId?: number;

}
