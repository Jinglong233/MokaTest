import axios from "axios";

export interface UploadFileResult {
    fileId: string;
    fileName: string;
}

/**
 * 删除富文本图片
 */
export function deleteRichTextImages(fileIds: string[]) {
    return axios.post('/api/file/deleteImage', fileIds);
}

/**
 * 上传临时文件（用于 API 测试参数中的 FILE 类型）
 */
export function uploadFile(file: File) {
    const formData = new FormData();
    formData.append('file', file);
    return axios.post<UploadFileResult>('/api/file/upload', formData, {
        headers: {
            'Content-Type': 'multipart/form-data'
        }
    });
}
