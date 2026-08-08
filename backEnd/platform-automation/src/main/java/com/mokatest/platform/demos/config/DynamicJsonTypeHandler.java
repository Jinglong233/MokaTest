package com.mokatest.platform.demos.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DynamicJsonTypeHandler extends BaseTypeHandler<Object> {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType) throws SQLException {
        // 如果已经是字符串，直接使用；否则序列化为 JSON
        if (parameter instanceof String) {
            ps.setString(i, (String) parameter);
        } else {
            ps.setString(i, toJson(parameter));
        }
    }

    @Override
    public Object getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String json = rs.getString(columnName);
        return fromJson(json);
    }

    @Override
    public Object getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String json = rs.getString(columnIndex);
        return fromJson(json);
    }

    @Override
    public Object getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String json = cs.getString(columnIndex);
        return fromJson(json);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON serialization failed", e);
        }
    }

    private Object fromJson(String json) {
        try {
            if (json == null || json.trim().isEmpty()) {
                return null;
            }
            // 解析为 Map 或 List，保持动态结构
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            // 如果解析失败，返回原始字符串
            return json;
        }
    }
}