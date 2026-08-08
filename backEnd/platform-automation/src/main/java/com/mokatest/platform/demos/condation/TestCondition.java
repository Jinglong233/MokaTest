package com.mokatest.platform.demos.condation;

import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.result.AssertResult;

public interface TestCondition {


    AssertResult evaluate(TestExecutionContext context);

    /**
     * 获取条件类型
     */

    String getType();
}

