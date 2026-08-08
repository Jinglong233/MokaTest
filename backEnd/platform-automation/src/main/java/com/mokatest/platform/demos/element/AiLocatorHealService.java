package com.mokatest.platform.demos.element;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mokatest.platform.demos.ai.gateway.AiGatewayService;
import com.mokatest.platform.demos.ai.gateway.ChatMessage;
import com.mokatest.platform.demos.ai.gateway.ChatOptions;
import com.mokatest.platform.demos.ai.gateway.ChatResult;
import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.domain.ui.Element;
import com.mokatest.platform.demos.domain.ui.dto.ElementDTO;
import com.mokatest.platform.demos.domain.ui.uiEnum.element.ElementLocatorRoleType;
import com.mokatest.platform.demos.domain.ui.uiEnum.element.ElementLocatorType;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Locator;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 定位自愈服务：步骤因定位器失效失败时，抓取页面无障碍树快照交给大模型推断新定位器，
 * 并在真实页面上验证（唯一且可见）后才返回，供步骤原地重试。
 *
 * 设计约束：
 * 1. 全程任意异常都返回 null，由调用方按原失败逻辑处理，自愈只加分不兜底；
 * 2. LLM 输出必须过 {@link #verify} 真实页面验证，不信任模型直出；
 * 3. 修复结果仅用于本次重跑，是否写回元素库由前端人工确认。
 */
@Component
@Slf4j
public class AiLocatorHealService {

    private static final Gson GSON = new Gson();

    /** 快照最大字符数，防止大页面 token 爆炸 */
    private static final int SNAPSHOT_MAX_CHARS = 8000;

    /** 失败信息带入 prompt 的最大长度 */
    private static final int ERROR_MAX_CHARS = 300;

    private static final String SYSTEM_PROMPT =
            "你是 UI 自动化测试的元素定位修复专家。根据页面无障碍树快照、目标元素名称和已失效的旧定位器，" +
            "推断当前页面中最可能匹配目标元素的新定位器；若页面上确实不存在目标元素，分析失败原因。" +
            "只输出 JSON 对象，不要输出任何其他内容。";

    @Resource
    private AiGatewayService aiGatewayService;

    @Resource
    private ElementProcessor elementProcessor;

    /**
     * 自愈结果：定位修复与失败诊断二选一（也可能都有）。
     * element 非空表示找到并验证了新定位器；diagnosis 非空表示 AI 给出的失败原因分析。
     */
    public static class HealResult {
        public Element element;
        public String diagnosis;
        public List<Element> candidates = new ArrayList<>();
    }

    /**
     * 尝试为失败步骤寻找新定位器并给出失败诊断。
     *
     * @param context    执行上下文（取当前 frame）
     * @param elementDTO 步骤的元素配置
     * @param stepName   步骤名称（语义兜底）
     * @param error      原始失败异常
     * @return 自愈/诊断结果；AI 未配置、调用失败或快照不可用时返回 null（调用方按原异常处理）
     */
    public HealResult tryHeal(TestExecutionContext context, ElementDTO elementDTO, String stepName, Exception error) {
        try {
            Frame frame = context.getCurrentFrame();
            if (frame == null || frame.isDetached()) {
                return null;
            }
            Element old = elementProcessor.getElementLocator(elementDTO);

            String snapshot = captureSnapshot(frame);
            if (snapshot == null) {
                return null;
            }

            String prompt = buildPrompt(resolveElementName(elementDTO, old), stepName, old, error, snapshot);

            ChatOptions options = new ChatOptions();
            options.setScene("UI_LOCATOR_HEAL");
            options.setPromptSummary("步骤[" + stepName + "]定位自愈");
            options.setMaxTokens(1200); // 推理模型会先生成思考内容，预留足够 token 防止 JSON 被截断
            options.setTemperature(0.1);
            options.setTimeoutMs(30000);
            ChatResult result = aiGatewayService.chat(
                    List.of(ChatMessage.system(SYSTEM_PROMPT), ChatMessage.user(prompt)), options);

            HealResult healResult = parseResult(result.getContent());
            for (Element candidate : healResult.candidates) {
                if (verify(frame, candidate)) {
                    log.info("[AiHeal] 步骤[{}] 自愈命中: {}={}", stepName, candidate.getLocatorType(), candidate.getLocatorValue());
                    healResult.element = candidate;
                    return healResult;
                }
            }
            // 当前 frame 修不了：若页面存在多个 frame，追加跨 iframe 诊断（只诊断，不自动修）
            String crossFrameNote = diagnoseCrossFrame(context, frame, elementDTO, old, stepName);
            if (crossFrameNote != null) {
                healResult.diagnosis = healResult.diagnosis == null
                        ? crossFrameNote
                        : healResult.diagnosis + "；" + crossFrameNote;
            }
            log.info("[AiHeal] 步骤[{}] 候选定位器均未通过页面验证，诊断: {}", stepName, healResult.diagnosis);
            return healResult;
        } catch (Exception e) {
            // AI 未配置/限流/超时/解析异常等：返回 null，调用方回退原异常
            log.warn("[AiHeal] 定位自愈不可用，按原失败处理: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 抓取页面无障碍树快照（YAML 结构，token 友好且语义稳定）
     */
    private String captureSnapshot(Frame frame) {
        try {
            String snapshot = frame.locator("body").ariaSnapshot();
            if (!StringUtils.hasText(snapshot)) {
                return null;
            }
            return snapshot.length() > SNAPSHOT_MAX_CHARS
                    ? snapshot.substring(0, SNAPSHOT_MAX_CHARS) + "\n...（快照过长已截断）"
                    : snapshot;
        } catch (Exception e) {
            log.warn("[AiHeal] 抓取页面快照失败: {}", e.getMessage());
            return null;
        }
    }

    private String buildPrompt(String elementName, String stepName, Element old, Exception error, String snapshot) {
        String errorMsg = error.getMessage() == null ? "" : error.getMessage();
        if (errorMsg.length() > ERROR_MAX_CHARS) {
            errorMsg = errorMsg.substring(0, ERROR_MAX_CHARS);
        }
        return "目标元素名称：" + (StringUtils.hasText(elementName) ? elementName : "（未知，参考步骤名推断）") + "\n"
                + "所属步骤：" + stepName + "\n"
                + "旧定位器（已失效）：" + old.getLocatorType() + " = " + old.getLocatorValue() + "\n"
                + "失败信息：" + errorMsg + "\n\n"
                + "当前页面无障碍树快照：\n" + snapshot + "\n\n"
                + "可选定位类型（type 取值必须来自此列表）：\n"
                + "- TEXT：可见文本，value 为文本内容\n"
                + "- ROLE：角色定位，value 格式 \"button::登录\"（角色::名称），角色仅限 button/textbox/link/checkbox/radio/heading/combobox/menuitem/option/img/listitem\n"
                + "- PLACEHOLDER / LABEL / TITLE / ALT / TEST_ID：value 为对应属性值\n"
                + "- CSS / XPATH：value 为选择器表达式\n"
                + "优先级：ROLE/TEXT/PLACEHOLDER/LABEL > CSS > XPATH。禁止输出带动态 class 或位置索引的脆弱定位器。\n"
                + "硬性要求：定位器在页面上必须唯一匹配且可见。避免使用页面上可能重复出现的泛文本（如『确定』『取消』『提交』等常见按钮文案），"
                + "优先结合上下文消歧（如弹窗标题、所在区域、相关联的 label）。\n\n"
                + "输出 JSON 对象：{\"candidates\":[{\"type\":\"TEXT\",\"value\":\"登录\",\"reason\":\"原因\"}],\"diagnosis\":\"失败原因分析\"}\n"
                + "- candidates：1~3 个候选定位器；页面上确实找不到目标元素时输出空数组 []\n"
                + "- diagnosis：一句话失败原因。找不到元素时说明（如「页面上不存在匹配『登录按钮』的元素，可能是产品 BUG 或前置流程缺失」）；"
                + "若在快照中看到相似但不确定的元素也注明（如「发现相似按钮『确定』，但名称不匹配，未采用」）";
    }

    /**
     * 跨 iframe 诊断（只诊断，不自动修）：当前 frame 无法自愈时，
     * 抓取其余 frame 的快照（最多 3 个），让 AI 判断目标元素是否在其他 iframe 中。
     *
     * @return 诊断提示（如「目标元素疑似位于 iframe(https://...) 中，请检查 Iframe 步骤配置」）；无法判断返回 null
     */
    private String diagnoseCrossFrame(TestExecutionContext context, Frame currentFrame,
                                      ElementDTO elementDTO, Element old, String stepName) {
        try {
            if (context.getCurrentPage() == null || context.getCurrentPage().isClosed()) {
                return null;
            }
            List<Frame> allFrames = context.getCurrentPage().frames();
            List<Frame> others = new ArrayList<>();
            // frame → 在 page.frames() 中的真实下标（IframeStep 按序号切换时使用）
            Map<Frame, Integer> frameIndexes = new HashMap<>();
            for (int idx = 0; idx < allFrames.size(); idx++) {
                Frame f = allFrames.get(idx);
                if (!f.equals(currentFrame) && !f.isDetached()) {
                    others.add(f);
                    frameIndexes.put(f, idx);
                }
            }
            if (others.isEmpty()) {
                return null;
            }

            StringBuilder framesSection = new StringBuilder();
            // url → 富标识（id > name > url，附 index），诊断信息最终展示给用户用于配置 IframeStep
            Map<String, String> frameLabels = new HashMap<>();
            int used = 0;
            for (Frame f : others) {
                if (used >= 3) {
                    break;
                }
                String snap = captureSnapshot(f);
                if (snap == null) {
                    continue;
                }
                used++;
                frameLabels.put(f.url(), buildFrameLabel(f, frameIndexes.get(f)));
                framesSection.append("=== iframe[").append(f.url()).append("] ===\n")
                        .append(snap, 0, Math.min(snap.length(), 3000)).append("\n\n");
            }
            if (used == 0) {
                return null;
            }

            String prompt = "目标元素名称：" + resolveElementName(elementDTO, old) + "\n"
                    + "所属步骤：" + stepName + "\n"
                    + "该元素在当前 frame 中定位失败。以下是页面中其他 iframe 的快照，请判断目标元素是否在其中某个 iframe 里。\n\n"
                    + framesSection
                    + "输出 JSON 对象：{\"found\":true或false,\"frame\":\"所在iframe的url\",\"note\":\"一句话结论\"}\n"
                    + "只有快照中能明确找到语义匹配的元素才 found=true，否则 found=false。";

            ChatOptions options = new ChatOptions();
            options.setScene("UI_LOCATOR_HEAL");
            options.setPromptSummary("步骤[" + stepName + "]跨iframe诊断");
            options.setMaxTokens(300);
            options.setTemperature(0.1);
            options.setTimeoutMs(30000);
            ChatResult result = aiGatewayService.chat(
                    List.of(ChatMessage.system(SYSTEM_PROMPT), ChatMessage.user(prompt)), options);

            String content = result.getContent();
            if (!StringUtils.hasText(content)) {
                return null;
            }
            String cleaned = cleanModelOutput(content);
            JsonObject obj = cleaned == null ? null : tryParseObject(cleaned);
            if (obj == null) {
                return null;
            }
            if (!obj.has("found") || !obj.get("found").getAsBoolean()) {
                return null;
            }
            String frameUrl = obj.has("frame") ? obj.get("frame").getAsString() : "";
            String note = obj.has("note") ? obj.get("note").getAsString() : "";
            // 优先展示 id/name 等可直接配置到 Iframe 步骤的标识，url 兜底
            String label = frameLabels.getOrDefault(frameUrl, frameUrl);
            return "目标元素疑似位于 iframe(" + label + ") 中：" + note + "，请检查 Iframe 步骤的 frame 配置";
        } catch (Exception e) {
            log.warn("[AiHeal] 跨iframe诊断失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 组装 frame 的展示标识：优先 iframe 标签的 id 属性，其次 name，最后 url（截断），并附上序号
     */
    private String buildFrameLabel(Frame frame, int index) {
        String id = null;
        try {
            if (frame.frameElement() != null) {
                id = frame.frameElement().getAttribute("id");
            }
        } catch (Exception ignored) {
        }
        if (StringUtils.hasText(id)) {
            return "id=" + id + "，序号=" + index;
        }
        if (StringUtils.hasText(frame.name())) {
            return "name=" + frame.name() + "，序号=" + index;
        }
        String url = frame.url();
        if (url != null && url.length() > 80) {
            url = url.substring(0, 80) + "...";
        }
        return "url=" + url + "，序号=" + index;
    }

    /**
     * 解析模型输出：对象格式 {"candidates":[...],"diagnosis":"..."}，同时兼容旧裸数组格式 [...]
     */
    private HealResult parseResult(String content) {
        HealResult result = new HealResult();
        String cleaned = cleanModelOutput(content);
        if (!StringUtils.hasText(cleaned)) {
            return result;
        }
        // 优先按对象解析（避免 diagnosis 文本中的 [ ] 干扰候选数组截取）
        JsonObject obj = tryParseObject(cleaned);
        if (obj != null) {
            if (obj.has("candidates") && obj.get("candidates").isJsonArray()) {
                result.candidates = filterCandidates(obj.getAsJsonArray("candidates"));
            } else {
                result.candidates = salvageCandidates(cleaned);
            }
            if (obj.has("diagnosis") && !obj.get("diagnosis").isJsonNull()) {
                result.diagnosis = obj.get("diagnosis").getAsString().trim();
            }
            return result;
        }
        // 对象解析失败（截断/散文包裹）：抢救 candidates 数组或兼容旧裸数组输出
        result.candidates = salvageCandidates(cleaned);
        return result;
    }

    /**
     * 清洗模型输出：剥离 <think> 思考块（推理模型）、markdown 代码围栏、首尾散文。
     */
    private String cleanModelOutput(String content) {
        if (!StringUtils.hasText(content)) {
            return content;
        }
        String cleaned = content
                .replaceAll("(?s)<think>.*?</think>", "")
                .replaceAll("(?s)```(?:json|JSON)?", "")
                .trim();
        return cleaned;
    }

    /**
     * 尝试从文本中提取并解析 JSON 对象（按括号配对扫描，兼容尾部散文；
     * 失败时去尾逗号重试一次）。
     */
    private JsonObject tryParseObject(String text) {
        int start = text.indexOf('{');
        if (start < 0) {
            return null;
        }
        int end = findMatchingBracket(text, start, '{', '}');
        if (end < 0) {
            end = text.lastIndexOf('}');
        }
        if (end <= start) {
            return null;
        }
        String json = text.substring(start, end + 1);
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            // 部分模型会输出尾逗号/不规范 JSON，去掉尾逗号再试一次
            try {
                String fixed = json.replaceAll(",\\s*([}\\]])", "$1");
                return JsonParser.parseString(fixed).getAsJsonObject();
            } catch (Exception e2) {
                log.warn("[AiHeal] 解析模型 JSON 对象失败: {}", e2.getMessage());
                return null;
            }
        }
    }

    /**
     * 括号配对扫描（跳过字符串内容，处理转义），返回与 start 处开括号匹配的闭括号下标，未闭合返回 -1。
     */
    private int findMatchingBracket(String text, int start, char open, char close) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * 兜底提取：外层对象被截断（无闭合 }）时，定位 "candidates" 键并配对扫描其数组，
     * 尽量抢救已完整输出的候选部分。
     */
    private List<Element> salvageCandidates(String cleaned) {
        int keyIdx = cleaned.indexOf("\"candidates\"");
        int arrStart = keyIdx >= 0 ? cleaned.indexOf('[', keyIdx) : cleaned.indexOf('[');
        if (arrStart < 0) {
            return new ArrayList<>();
        }
        int arrEnd = findMatchingBracket(cleaned, arrStart, '[', ']');
        if (arrEnd < 0) {
            log.warn("[AiHeal] 候选数组未闭合（输出可能被截断）");
            return new ArrayList<>();
        }
        String json = cleaned.substring(arrStart, arrEnd + 1);
        try {
            return filterCandidates(JsonParser.parseString(json).getAsJsonArray());
        } catch (Exception e) {
            try {
                return filterCandidates(JsonParser.parseString(
                        json.replaceAll(",\\s*([}\\]])", "$1")).getAsJsonArray());
            } catch (Exception e2) {
                log.warn("[AiHeal] 解析候选数组失败: {}", e2.getMessage());
                return new ArrayList<>();
            }
        }
    }

    /**
     * 过滤候选定位器：丢弃非法 type 和非法 ROLE 格式
     */
    private List<Element> filterCandidates(JsonArray arr) {
        List<Element> candidates = new ArrayList<>();
        if (arr == null) {
            return candidates;
        }
        for (JsonElement item : arr) {
            if (!item.isJsonObject()) {
                continue;
            }
            JsonObject obj = item.getAsJsonObject();
            String type = obj.has("type") && !obj.get("type").isJsonNull()
                    ? obj.get("type").getAsString().trim().toUpperCase() : null;
            String value = obj.has("value") && !obj.get("value").isJsonNull()
                    ? obj.get("value").getAsString().trim() : null;
            if (!StringUtils.hasText(type) || !StringUtils.hasText(value)) {
                continue;
            }
            try {
                ElementLocatorType.valueOf(type);
            } catch (IllegalArgumentException e) {
                continue;
            }
            if ("ROLE".equals(type)) {
                // 提前校验 role 部分可解析，避免验证阶段必败
                String rolePart = value.split("::", 2)[0];
                try {
                    ElementLocatorRoleType.parseRoleType(rolePart);
                } catch (IllegalArgumentException e) {
                    continue;
                }
            }
            Element element = new Element();
            element.setLocatorType(type);
            element.setLocatorValue(value);
            candidates.add(element);
        }
        return candidates;
    }

    /**
     * 在真实页面验证候选定位器：必须唯一且可见。
     * count() 与 isVisible() 均为立即执行（不等待），页面已在步骤超时后处于稳定状态。
     */
    private boolean verify(Frame frame, Element candidate) {
        try {
            Locator locator = ElementLocatorProcessor.process(
                    frame,
                    ElementLocatorType.valueOf(candidate.getLocatorType().toString().toUpperCase()),
                    candidate.getLocatorValue());
            return locator.count() == 1 && locator.first().isVisible();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 元素名称：库选元素取库中名称（语义锚点），自定义定位无名称返回 null
     */
    private String resolveElementName(ElementDTO elementDTO, Element resolved) {
        if (StringUtils.hasText(resolved.getElementName())) {
            return resolved.getElementName();
        }
        // 步骤内快照可能带名称
        if (elementDTO.getLocator() != null && StringUtils.hasText(elementDTO.getLocator().getElementName())) {
            return elementDTO.getLocator().getElementName();
        }
        return null;
    }
}
