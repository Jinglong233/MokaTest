package com.mokatest.platform.demos.api.handler;

import com.mokatest.platform.demos.api.domain.requestModel.ScriptItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 脚本列表类型处理器（兼容旧数据格式）
 *
 * 功能说明：
 *   - 将 List<ScriptItem> 序列化为 JSON 数组字符串存储到数据库
 *   - 从数据库反序列化时，兼容旧数据的纯字符串格式（自动包装为单元素数组）
 *
 * 兼容逻辑：
 *   - 新数据：JSON 数组，如 [{"id":"...","name":"...","content":"...","enabled":true}]
 *   - 旧数据：纯文本字符串，如 "context.log('hello')"，会被包装为单元素 ScriptItem 数组
 *
 * @author JingLong
 * @since 2026-05-27
 */
@MappedTypes(List.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class ScriptListTypeHandler extends BaseTypeHandler<List<ScriptItem>> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<ScriptItem> parameter, JdbcType jdbcType) throws SQLException {
        try {
            ps.setString(i, OBJECT_MAPPER.writeValueAsString(parameter));
        } catch (Exception e) {
            throw new SQLException("Failed to serialize script list", e);
        }
    }

    @Override
    public List<ScriptItem> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public List<ScriptItem> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public List<ScriptItem> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    /**
     * 解析数据库中的字符串为脚本列表
     *
     * @param json 数据库中的原始字符串
     * @return ScriptItem 列表；如果解析失败且不是纯字符串则返回 null
     */
    private List<ScriptItem> parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<List<ScriptItem>>() {});
        } catch (Exception e) {
            // 兼容旧数据：纯字符串包装为单元素数组
            String trimmed = json.trim();
            if (!trimmed.startsWith("[")) {
                String content = trimmed;
                // 去掉可能的外层引号（JSON 字符串序列化时可能添加）
                if ((content.startsWith("\"") && content.endsWith("\"")) ||
                    (content.startsWith("'") && content.endsWith("'"))) {
                    content = content.substring(1, content.length() - 1);
                }
                ScriptItem item = new ScriptItem();
                item.setId("legacy_" + System.currentTimeMillis());
                item.setName("遗留脚本");
                item.setContent(content);
                item.setEnabled(false); // 旧数据默认禁用，避免自动执行
                item.setSort(0);
                List<ScriptItem> list = new ArrayList<>();
                list.add(item);
                return list;
            }
            return null;
        }
    }
}
