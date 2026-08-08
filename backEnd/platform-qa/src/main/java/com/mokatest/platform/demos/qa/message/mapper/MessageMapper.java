package com.mokatest.platform.demos.qa.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mokatest.platform.demos.qa.message.domain.Message;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 消息 Mapper
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /**
     * 统计未读消息数
     */
    @Select("SELECT COUNT(*) FROM message WHERE receiver_id = #{userId} AND is_read = 0 AND is_deleted = 0")
    int countUnread(@Param("userId") Integer userId);

    /**
     * 查询最近消息
     */
    @Select("SELECT * FROM message WHERE receiver_id = #{userId} AND is_deleted = 0 ORDER BY is_read ASC, create_time DESC LIMIT #{limit}")
    List<Message> listRecent(@Param("userId") Integer userId, @Param("limit") int limit);

    /**
     * 标记单条已读
     */
    @Update("UPDATE message SET is_read = 1, read_time = NOW() WHERE id = #{id} AND receiver_id = #{userId} AND is_deleted = 0")
    int markRead(@Param("id") Integer id, @Param("userId") Integer userId);

    /**
     * 全部已读
     */
    @Update("UPDATE message SET is_read = 1, read_time = NOW() WHERE receiver_id = #{userId} AND is_read = 0 AND is_deleted = 0")
    int markAllRead(@Param("userId") Integer userId);
}
