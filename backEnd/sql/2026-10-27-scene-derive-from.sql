-- 批次二（AI 派生场景）：scene 表新增派生来源标记列
-- 口径：derive_from = 源场景 id；NULL 表示非 AI 派生场景
-- 重入保护：MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，用 information_schema.COLUMNS 预查 + PREPARE 条件执行

SET @add_derive_from := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'scene'
              AND COLUMN_NAME = 'derive_from'
        ),
        'SELECT 1',
        'ALTER TABLE scene ADD COLUMN derive_from INT NULL COMMENT ''AI派生来源场景id（null=非AI派生）'' AFTER scene_category'
    )
);
PREPARE stmt_add_derive_from FROM @add_derive_from;
EXECUTE stmt_add_derive_from;
DEALLOCATE PREPARE stmt_add_derive_from;
