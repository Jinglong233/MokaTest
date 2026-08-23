import axios from 'axios';

export interface MessageRecord {
  id: number;
  title: string;
  content: string;
  eventType: string;
  bizType: string;
  bizId: number;
  teamId: number;
  projectId: number;
  isRead: number;
  createTime: string;
  extraData?: string;
  snapshot?: Record<string, any>;
  accessible?: boolean;
}

export type MessageListType = MessageRecord[];

/**
 * 查询消息列表
 */
export function queryMessageList(page?: number, pageSize?: number, isRead?: number, bizType?: string, isMention?: boolean) {
  return axios.get('/api/qa/message/list', {
    params: { page, pageSize, isRead, bizType, isMention }
  });
}

/**
 * 获取未读消息数
 */
export function getUnreadCount() {
  return axios.get('/api/qa/message/unreadCount');
}

/**
 * 标记单条消息已读
 */
export function markMessageRead(id: number) {
  return axios.post(`/api/qa/message/read/${id}`);
}

/**
 * 批量标记已读（兼容旧接口，内部逐条调用）
 */
export function setMessageStatus(data: { ids: number[] }) {
  const promises = data.ids.map((id) => markMessageRead(id));
  return Promise.all(promises);
}

/**
 * 全部已读
 */
export function markAllMessagesRead() {
  return axios.post('/api/qa/message/readAll');
}

/**
 * 删除消息
 */
export function deleteMessage(id: number) {
  return axios.post(`/api/qa/message/delete/${id}`);
}

/**
 * 校验消息来源资源是否存在
 */
export function validateMessageSource(bizType: string, bizId: number, commentId?: number) {
  return axios.get('/api/qa/message/validate-source', {
    params: { bizType, bizId, commentId },
  });
}

export interface ChatRecord {
  id: number;
  username: string;
  content: string;
  time: string;
  isCollect: boolean;
}

export function queryChatList() {
  return axios.post<ChatRecord[]>('/api/chat/list');
}
