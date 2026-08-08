package com.mokatest.platform.demos.enums;

/**
 * 断言关系枚举（公共枚举，API 和 UI 自动化共用）
 *
 * 功能说明：定义断言时的比较关系，用于判断实际值与预期值之间的关系是否成立。
 * 该枚举是 API 测试和 UI 自动化测试的共享组件，分别用于：</p>
 *   API 测试：断言 HTTP 响应的状态码、响应体、响应头等是否符合预期
 *   UI 自动化：断言页面元素文本、属性值等是否符合预期
 *
 * 使用场景：
 * <pre>
 *   // API 断言示例
 *   AssertParameter assertParam = new AssertParameter();
 *   assertParam.setAssertRelationship(AssertRelationship.EQUALS);
 *   assertParam.setExpectValue("200");
 *   assertParam.setActualValue(response.getStatusCode().toString());
 *   boolean result = assertExecutor.assert(assertParam);
 *
 *   // UI 断言示例
 *   StepDTO step = new StepDTO();
 *   step.setAssertRelationship(AssertRelationship.CONTAINS);
 *   step.setExpectValue("欢迎");
 *   step.setActualValue(element.getText());
 *   boolean result = assertStep.execute(step);
 * </pre>
 *
 * 迁移历史：
 *   原位置：{@code com.mokaTest.platform.demos.domain.ui.uiEnum.condation.AssertRelationship}
 *   新位置：{@code com.mokaTest.platform.demos.enums.AssertRelationship}
 *   迁移原因：API 测试和 UI 自动化都需要使用断言关系，抽离为公共枚举避免循环依赖
 *
 * @author JingLong
 * @since 2025-07-25
 */
public enum AssertRelationship {

    /**
     * 相等断言
     *
     * 判断实际值与预期值是否完全相等（字符串比较）
     * 示例：statusCode == 200，responseBody == "success"
     */
    EQUALS("相等"),

    /**
     * 不相等断言
     *
     * 判断实际值与预期值是否不相等
     * 示例：statusCode != 404
     */
    NOT_EQUALS("不相等"),

    /**
     * 包含断言
     *
     * 判断实际值字符串中是否包含预期值字符串
     * 示例：responseBody.contains("token")，elementText.contains("欢迎")
     */
    CONTAINS("包含"),

    /**
     * 不包含断言
     *
     * 判断实际值字符串中是否不包含预期值字符串
     * 示例：responseBody 不包含 "error"
     */
    NOT_CONTAINS("不包含"),

    /**
     * 大于断言
     *
     * 将实际值和预期值转为数值后比较，判断实际值是否大于预期值
     * 示例：responseTime > 1000，statusCode > 200
     */
    GT("大于"),

    /**
     * 小于断言
     *
     * 将实际值和预期值转为数值后比较，判断实际值是否小于预期值
     * 示例：responseTime < 5000，price < 100
     */
    LT("小于"),

    /**
     * 大于等于断言
     *
     * 将实际值和预期值转为数值后比较，判断实际值是否大于等于预期值
     * 示例：statusCode >= 200
     */
    GE("大于等于"),

    /**
     * 小于等于断言
     *
     * 将实际值和预期值转为数值后比较，判断实际值是否小于等于预期值
     * 示例：responseTime <= 3000
     */
    LE("小于等于"),

    /**
     * 正则匹配断言
     *
     * 使用正则表达式匹配实际值，判断是否符合预期模式
     * 示例：responseBody 匹配 "\d{4}-\d{2}-\d{2}"（日期格式）
     */
    REGULAR("正则匹配");

    /**
     * 断言关系的中文名称，用于前端下拉框展示和日志输出
     */
    private String name;

    /**
     * 构造方法
     *
     * @param name 断言关系的中文名称
     */
    AssertRelationship(String name) {
        this.name = name;
    }

    /**
     * 获取断言关系的中文名称
     *
     * @return 中文名称，如 "相等"、"包含"、"大于" 等
     */
    public String getName() {
        return name;
    }
}
