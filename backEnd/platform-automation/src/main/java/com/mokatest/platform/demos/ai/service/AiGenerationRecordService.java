package com.mokatest.platform.demos.ai.service;

import com.mokatest.platform.demos.ai.domain.AiGenerationRecord;

import java.util.List;

/**
 * AI 生成记录服务（会话锚点）
 *
 * 会话隔离核心规则：
 * 1. 采纳/保存只传 recordNo，实体归属（projectId/entityId）一律从记录锚点读取，不信前端传参；
 * 2. 跨项目 recordNo 一律拒绝；
 * 3. 记录 24h 过期，过期后不可追加、不可采纳。
 */
public interface AiGenerationRecordService {

    /**
     * 创建生成记录（新会话首轮）
     *
     * @param projectId    项目ID（请求头上下文解析）
     * @param teamId       团队ID
     * @param userId       用户ID
     * @param scene        场景
     * @param entityId     锚定实体ID（项目级场景传 null）
     * @param inputSummary 输入摘要（含引用知识库 chunk 记录、裁剪说明）
     * @return 新建记录
     */
    AiGenerationRecord create(Integer projectId, Integer teamId, String userId,
                              String scene, Long entityId, String inputSummary);

    /**
     * 按 recordNo 查询并校验锚点
     *
     * @param recordNo  记录编号
     * @param projectId 当前项目（必须与记录一致，否则视为不存在）
     * @return 有效记录
     * @throws com.mokatest.platform.demos.exception.BusinessException 记录不存在/跨项目/已过期
     */
    AiGenerationRecord requireValid(String recordNo, Integer projectId);

    /**
     * 追加输出快照（追加生成时累积）
     *
     * @param recordNo      记录编号
     * @param projectId     当前项目
     * @param outputSnapshot 最新完整输出快照（JSON）
     */
    void appendSnapshot(String recordNo, Integer projectId, String outputSnapshot);

    /**
     * 追加一轮生成（多轮对话结构）
     *
     * output_snapshot 统一为轮次数组：[{round, instruction, time, drafts:[...]}]；
     * 历史单层草稿数组自动按第 1 轮兼容包装后再追加。
     *
     * @param recordNo    记录编号
     * @param projectId   当前项目
     * @param instruction 本轮用户指令（首轮为生成参数摘要）
     * @param draftsJson  本轮草稿（JSON 数组字符串）
     */
    void appendRound(String recordNo, Integer projectId, String instruction, String draftsJson);

    /**
     * 开始一轮生成（发送即落库）：追加 {round, instruction, time, status: GENERATING, drafts: []}
     *
     * @return 本轮轮次号
     */
    int beginRound(String recordNo, Integer projectId, String instruction);

    /**
     * 开始一轮（带类型）：type = gen（生成用例）/ qa（自由问答）
     *
     * @return 本轮轮次号
     */
    int beginRound(String recordNo, Integer projectId, String instruction, String type);

    /**
     * 完成一轮问答：最新 GENERATING 轮次标记 DONE 并写入回答（type=qa，drafts 为空）
     */
    void finishQaRound(String recordNo, Integer projectId, String answer);

    /**
     * 完成一轮生成：将最新的 GENERATING 轮次替换为 {status: DONE, drafts}；无 GENERATING 轮时追加
     */
    void finishRound(String recordNo, Integer projectId, String draftsJson);

    /**
     * 失败一轮生成：将最新的 GENERATING 轮次标记 error；无 GENERATING 轮时追加失败轮
     */
    void failRound(String recordNo, Integer projectId, String errorMsg);

    /**
     * 流式期间更新最新 GENERATING 轮次的 rawText（定期落库，供重开弹窗回放生成过程）
     */
    void updateRoundRawText(String recordNo, Integer projectId, String rawText);

    /**
     * 停止一轮生成：最新 GENERATING 轮次标记 STOPPED，保留已生成 rawText
     */
    void stopRound(String recordNo, Integer projectId, String rawText);

    /**
     * 完成一轮生成：将最新的 GENERATING 轮次替换为 {status: DONE, drafts, rawText}；无 GENERATING 轮时追加
     */
    void finishRound(String recordNo, Integer projectId, String draftsJson, String rawText);

    /**
     * 完成一轮生成（带需求不确定点）：额外写入 uncertainties 数组，供前端提示用户补充需求
     */
    void finishRound(String recordNo, Integer projectId, String draftsJson, String rawText, String uncertaintiesJson);

    /**
     * 完成一轮生成（带不确定点 + 知识库引用）：citationsJson 为 [{docId,title,snippet}] 数组
     */
    void finishRound(String recordNo, Integer projectId, String draftsJson, String rawText,
                     String uncertaintiesJson, String citationsJson);

    /**
     * 删除最后一轮（重新生成用）；仅允许删除最新一轮
     */
    void removeLastRound(String recordNo, Integer projectId);

    /**
     * 删除生成记录（会话删除；不影响已入库的用例）
     */
    void delete(String recordNo, Integer projectId);

    /**
     * 解析快照为轮次数组（兼容旧格式：单层草稿数组 → 包装为第 1 轮）
     */
    com.alibaba.fastjson.JSONArray parseRounds(String outputSnapshot);

    /**
     * 登记采纳明细
     *
     * @param recordNo      记录编号
     * @param projectId     当前项目
     * @param adoptedDetail 采纳明细（JSON：入库条目及生成的实体ID）
     */
    void recordAdoption(String recordNo, Integer projectId, String adoptedDetail);

    /**
     * 解析已采纳的 draftId 集合（防重复入库）
     *
     * adoptedDetail 新格式为明细数组 [{draftId, caseId}]；
     * 旧格式 {caseIds, count} 无 draftId，返回空集合（老记录不拦截，前端按 caseId 标记）。
     */
    java.util.Set<String> parseAdoptedDraftIds(String adoptedDetail);

    /**
     * 追加采纳明细（与既有明细合并，自动迁移旧格式）
     *
     * @param recordNo  记录编号
     * @param projectId 当前项目
     * @param entries   本轮新采纳明细 [{draftId, caseId}]
     */
    void mergeAdoption(String recordNo, Integer projectId, com.alibaba.fastjson.JSONArray entries);

    /**
     * 解析采纳明细数组（旧格式自动迁移为 [{caseId}] 条目）
     */
    com.alibaba.fastjson.JSONArray parseAdoptionEntries(String adoptedDetail);

    /**
     * 查询某实体下的生成记录（回溯列表）
     *
     * @param projectId 项目ID（强制过滤）
     * @param scene     场景
     * @param entityId  锚定实体ID
     */
    List<AiGenerationRecord> listByEntity(Integer projectId, String scene, Long entityId);
}
