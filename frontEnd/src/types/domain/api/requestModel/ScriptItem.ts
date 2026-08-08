/**
 * 脚本项（用于前置/后置脚本列表）
 */
export interface ScriptItem {
  /** 脚本唯一标识 */
  id?: string;
  /** 脚本名称 */
  name?: string;
  /** 脚本内容（JavaScript 代码） */
  content?: string;
  /** 是否启用 */
  enabled?: boolean;
  /** 排序 */
  sort?: number;
}
