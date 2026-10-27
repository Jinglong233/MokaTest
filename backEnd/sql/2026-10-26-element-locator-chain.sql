-- 2026-10-26 element 表定位候选链升级
-- 1. 新增 locator_chain JSON 列：有序定位候选 [{type,value,score?}]，第一项为主定位，与 locator_type/locator_value 保持一致
-- 2. locator_type 枚举新增 text_contains（文本包含）
-- 3. 存量 text 类型元素改写为 text_contains：TEXT 语义由「包含」收窄为「精确匹配」，
--    此 UPDATE 保证存量元素执行语义不变
-- 存量元素无需回填 locator_chain：读侧在链为空时由单定位合成单元素链
-- 重入保护：MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，用 information_schema.COLUMNS 预查 + PREPARE 条件执行

SET @add_locator_chain := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'element'
              AND COLUMN_NAME = 'locator_chain'
        ),
        'SELECT 1',
        'ALTER TABLE element ADD COLUMN locator_chain json NULL COMMENT ''定位候选链（有序：[{type,value,score?}]，第一项为主定位）'' AFTER locator_value'
    )
);
PREPARE stmt_add_locator_chain FROM @add_locator_chain;
EXECUTE stmt_add_locator_chain;
DEALLOCATE PREPARE stmt_add_locator_chain;

ALTER TABLE element
    MODIFY COLUMN locator_type enum ('id', 'class', 'xpath', 'css', 'text', 'text_contains', 'placeholder', 'role',
        'test_id', 'label', 'title', 'alt') NULL COMMENT '定位类型';

UPDATE element SET locator_type = 'text_contains' WHERE locator_type = 'text';
