// utils/zodToArcoRules.ts
import type { ZodTypeAny } from 'zod';
import type { FieldRule } from '@arco-design/web-vue';

/**
 * 将 Zod 校验规则转换为 Arco Design 表单规则
 * @param zodSchema Zod 校验器
 * @param fieldName 字段名称（用于生成错误消息）
 * @returns Arco Design 表单规则数组
 */
export function zodToArcoRules(zodSchema: ZodTypeAny, fieldName?: string): FieldRule[] {
    const rules: FieldRule[] = [];

    if (!zodSchema) return rules;

    const checks = (zodSchema as any)._def?.checks || [];
    const hasRequired = checks.some((c: any) => c.kind === 'min' && c.value === 1);

    // 1. 必填校验
    if (hasRequired) {
        rules.push({
            required: true,
            message: fieldName ? `${fieldName}不能为空` : '该字段为必填项'
        });
    }

    // 2. 字符串校验
    checks.forEach((check: any) => {
        switch (check.kind) {
            case 'min':
                if (check.value > 1) { // 排除已经处理的 required 校验
                    rules.push({
                        minLength: check.value,
                        message: fieldName
                            ? `${fieldName}至少需要${check.value}个字符`
                            : `至少需要${check.value}个字符`
                    });
                }
                break;

            case 'max':
                rules.push({
                    maxLength: check.value,
                    message: fieldName
                        ? `${fieldName}不能超过${check.value}个字符`
                        : `不能超过${check.value}个字符`
                });
                break;

            case 'email':
                rules.push({
                    type: 'email',
                    message: fieldName
                        ? `${fieldName}必须是有效的邮箱地址`
                        : '请输入有效的邮箱地址'
                });
                break;

            case 'url':
                rules.push({
                    type: 'url',
                    message: fieldName
                        ? `${fieldName}必须是有效的URL`
                        : '请输入有效的URL'
                });
                break;

            case 'regex':
                rules.push({
                    pattern: check.regex,
                    message: check.message || (fieldName
                        ? `${fieldName}格式不正确`
                        : '格式不正确')
                });
                break;
        }
    });

    // 3. 数字校验
    const numberChecks = checks.filter((c: any) =>
        ['int', 'min', 'max', 'positive', 'negative'].includes(c.kind)
    );

    if (numberChecks.length > 0) {
        const numberRule: FieldRule & { type: 'number' } = { type: 'number' };

        numberChecks.forEach((check: any) => {
            switch (check.kind) {
                case 'min':
                    numberRule.min = check.value;
                    numberRule.message = fieldName
                        ? `${fieldName}不能小于${check.value}`
                        : `值不能小于${check.value}`;
                    break;

                case 'max':
                    numberRule.max = check.value;
                    numberRule.message = fieldName
                        ? `${fieldName}不能大于${check.value}`
                        : `值不能大于${check.value}`;
                    break;

                case 'int':
                    numberRule.message = fieldName
                        ? `${fieldName}必须是整数`
                        : '请输入整数';
                    break;

                case 'positive':
                    numberRule.min = 0;
                    numberRule.message = fieldName
                        ? `${fieldName}必须是正数`
                        : '请输入正数';
                    break;

                case 'negative':
                    numberRule.max = 0;
                    numberRule.message = fieldName
                        ? `${fieldName}必须是负数`
                        : '请输入负数';
                    break;
            }
        });

        rules.push(numberRule);
    }

    // 4. 自定义错误消息
    const customError = (zodSchema as any)._def.error?.message;
    if (customError) {
        rules.push({ message: customError });
    }

    return rules;
}