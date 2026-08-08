import axios from "axios";

/**
 * 调试API场景
 * @param sceneId 场景ID
 */
export function debugApiScene(sceneId: number) {
    // 设置 120 秒超时：API 场景是同步执行，步骤多或网络慢时可能耗时较长
    return axios.post('/api/apiScene/debug', { sceneId }, { timeout: 120000 })
}
