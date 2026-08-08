import axios from "axios";

/**
 * 获取文件预览 URL。
 * 后端根据当前存储类型返回可访问地址（local 返回代理地址，MinIO 返回直链/代理地址等）。
 */
export function getFilePreviewUrl(fileId: string): Promise<string> {
  if (!fileId) {
    return Promise.reject(new Error('fileId 不能为空'));
  }
  return axios.get(`/api/file/preview`, { params: { fileId } }).then((res: any) => {
    return res.data || '';
  });
}

/**
 * 获取文件下载 URL（后端代理地址）。
 */
export function getFileDownloadUrl(fileId: string): string {
  return `/api/file/download?fileId=${encodeURIComponent(fileId)}`;
}
