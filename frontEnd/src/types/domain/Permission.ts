export class Permission {
    id?: number;
    name?: string;
    code?: string;
    type?: 'MENU' | 'BUTTON' | 'API';
    parentId?: number;
    sort?: number;
    createTime?: string;
    children?: Permission[];
}
