import axios from 'axios';

/**
 * 知识库文档（项目级）
 * projectId 由 axios 拦截器统一带 X-Project-Id
 */

export interface KnowledgeDocItem {
    id: number;
    title: string;
    docType: string;
    indexStatus: 'PENDING' | 'INDEXING' | 'READY' | 'FAILED';
    citeCount: number;
    chunkCount: number;
    createUserId?: string;
    updateUserId?: string;
    createTime?: string;
    updateTime?: string;
}

export interface KnowledgeDocDetail {
    id: number;
    title: string;
    docType: string;
    content: string;
    indexStatus: string;
}

export interface KnowledgeChunkItem {
    id: number;
    chunkIndex: number;
    chunkText: string;
    tokenCount: number;
    hasEmbedding: boolean;
}

export function listKnowledgeDocs(keyword?: string) {
    return axios.get('/api/knowledge/list', {params: {keyword}});
}

export function getKnowledgeDoc(id: number) {
    return axios.get('/api/knowledge/detail', {params: {id}});
}

export function saveKnowledgeDoc(body: { id?: number; title: string; docType: string; content: string }) {
    return axios.post('/api/knowledge/save', body);
}

export function uploadKnowledgeDoc(formData: FormData) {
    return axios.post('/api/knowledge/upload', formData, {
        headers: {'Content-Type': 'multipart/form-data'},
    });
}

export function deleteKnowledgeDoc(id: number) {
    return axios.post(`/api/knowledge/delete/${id}`);
}

export function listKnowledgeChunks(docId: number) {
    return axios.get('/api/knowledge/chunks', {params: {docId}});
}

export function rebuildKnowledgeIndex(id: number) {
    return axios.post(`/api/knowledge/rebuildIndex/${id}`);
}
