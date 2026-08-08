export class TeamMemberVO {
    id?: number;
    teamId?: number;
    userId?: number;
    username?: string;
    nickname?: string;
    avatar?: string;
    phone?: string;
    email?: string;
    roleId?: number;
    roleName?: string;
    roleCode?: string;
    status?: number;
    joinTime?: string;
    superAdmin?: boolean;
    teamOwner?: boolean;
}
