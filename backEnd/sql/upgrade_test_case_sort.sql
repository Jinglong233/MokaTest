-- test_case 增加同级排序字段（脑图兄弟节点顺序持久化）
-- 历史数据统一为 0，列表按 sort 升序 + 创建时间倒序回退，行为与旧版一致

ALTER TABLE `test_case`
    ADD COLUMN `sort` int NOT NULL DEFAULT 0 COMMENT '同级排序值(脑图顺序)' AFTER `expect_duration`;
