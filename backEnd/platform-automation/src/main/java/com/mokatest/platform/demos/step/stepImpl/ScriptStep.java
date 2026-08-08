package com.mokatest.platform.demos.step.stepImpl;

import com.mokatest.platform.demos.api.domain.requestModel.ScriptItem;
import com.mokatest.platform.demos.api.script.ScriptContext;
import com.mokatest.platform.demos.api.script.ScriptExecutor;
import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType;
import com.mokatest.platform.demos.result.AssertResult;
import com.mokatest.platform.demos.result.BaseStepResult;
import com.mokatest.platform.demos.result.StepResult;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 脚本步骤执行类
 *
 * 在场景共享变量池上下文中执行用户 JS（GraalJS 沙箱）：
 * - context.getVariable / setVariable 读写场景变量（执行后回写变量池，后续步骤可 ${var} 引用）
 * - console.log / context.log 输出日志（写入步骤结果 additionalInfo 展示）
 * - context.assertCondition 自定义断言（任一失败则步骤 FAILURE）
 *
 * 说明：纯 API 场景执行时由 {@link com.mokatest.platform.demos.api.service.ApiSceneDebugService}
 * 直接分发执行，不走本类的 doExecute；本类服务于 UI 场景（调试与计划执行共用）。
 */
public class ScriptStep extends AbstractTestStep {

    /**
     * JS 脚本内容。transient：步骤结果会被 GSON 序列化推送到 /ws/debug，执行期数据不应进入 JSON。
     */
    private final transient String scriptContent;

    /**
     * 项目 id（脚本内 fn.名称(...) 按名调用自定义函数时解析用）
     */
    private final transient Integer projectId;

    public ScriptStep(String scriptContent, Integer projectId) {
        this.scriptContent = scriptContent;
        this.projectId = projectId;
    }

    @Override
    protected StepResult doExecute(TestExecutionContext context) {
        BaseStepResult common = context.getCurrentCommonStepResult();

        if (scriptContent == null || scriptContent.trim().isEmpty()) {
            common.setStatus(StepExecutionType.SUCCESS);
            common.setAdditionalInfo("脚本内容为空，跳过执行");
            return context.getCurrentStepResult();
        }

        ScriptExecutor.ScriptResult result = executeScript(scriptContent, context.getVariables(), projectId);

        // 日志写入步骤结果，前端结果抽屉展示
        if (result.getConsoleLogs() != null && !result.getConsoleLogs().isEmpty()) {
            common.setAdditionalInfo(String.join("\n", result.getConsoleLogs()));
        }

        // 脚本自定义断言 → BaseStepResult.assertResults，任一失败则步骤失败
        boolean assertionFailed = false;
        List<ScriptContext.ScriptAssertion> scriptAssertions = result.getScriptAssertions();
        if (scriptAssertions != null && !scriptAssertions.isEmpty()) {
            Map<Integer, AssertResult> assertMap = new LinkedHashMap<>();
            int idx = 1;
            for (ScriptContext.ScriptAssertion sa : scriptAssertions) {
                assertMap.put(idx++, new AssertResult(sa.isSuccess(), sa.getMessage(), "SCRIPT"));
                if (!sa.isSuccess()) {
                    assertionFailed = true;
                }
            }
            common.setAssertResults(assertMap);
        }

        if (!result.isSuccess()) {
            common.setStatus(StepExecutionType.FAILURE);
            common.setErrorMessage("脚本执行失败: " + result.getErrorMessage());
        } else if (assertionFailed) {
            common.setStatus(StepExecutionType.FAILURE);
            common.setErrorMessage("存在脚本断言失败");
        } else {
            common.setStatus(StepExecutionType.SUCCESS);
        }
        return context.getCurrentStepResult();
    }

    /**
     * 执行脚本并回写变量池（ScriptContext 内部是变量副本，必须合并回场景变量池）。
     * 供本类与 ApiSceneDebugService 的 SCRIPT 分支共用。
     *
     * @param scriptContent JS 脚本内容
     * @param variables     场景变量池（执行结果被合并回该 Map）
     * @param projectId     项目 id（脚本内 fn.名称(...) 按名调用自定义函数时解析用，可为 null）
     * @return 脚本执行结果（日志/断言/错误）
     */
    public static ScriptExecutor.ScriptResult executeScript(String scriptContent, Map<String, Object> variables,
                                                            Integer projectId) {
        ScriptItem item = new ScriptItem();
        item.setName("脚本步骤");
        item.setContent(scriptContent);
        item.setEnabled(true);
        item.setSort(0);

        ScriptExecutor.ScriptResult result = ScriptExecutor.executePreScripts(List.of(item), variables, null, projectId);
        if (result.getVariables() != null) {
            variables.putAll(result.getVariables());
        }
        return result;
    }

    /**
     * 脚本步骤无需 Playwright 截图（且场景首步是脚本时还没有 page），覆写为 no-op 避免截图报错。
     */
    @Override
    protected void stepScreenshot(TestExecutionContext context) {
        // no-op
    }
}
