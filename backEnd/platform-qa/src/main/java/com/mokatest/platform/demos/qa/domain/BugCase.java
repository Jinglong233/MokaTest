package com.mokatest.platform.demos.qa.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * BUG-用例关联实体（多对多）
 */
@TableName(value = "bug_case")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BugCase {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer bugId;

    private Integer testCaseId;
}
