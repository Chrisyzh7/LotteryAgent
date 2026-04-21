package com.yzh.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzh.model.entity.ChatHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 对话历史记录 Mapper 接口
 * 基础 CRUD 由 MyBatis-Plus BaseMapper 提供；
 * 自定义查询在 resources/mapper/ChatHistoryMapper.xml 中定义。
 */
@Mapper
public interface ChatHistoryMapper extends BaseMapper<ChatHistory> {

    /**
     * 按 sessionId 查询最近 N 条对话（时间倒序，取最新的）
     * 用于构造 AI 上下文 / RAG 语义检索
     *
     * @param sessionId 会话 ID
     * @param limit     最多返回条数
     * @return 按时间倒序的对话列表
     */
    List<ChatHistory> selectRecentBySessionId(
            @Param("sessionId") String sessionId,
            @Param("limit") int limit
    );

    /**
     * 按 userId + modelKey 查询最近 N 条记录（降级方案，无 sessionId 时使用）
     *
     * @param userId   用户 ID
     * @param modelKey 模型标识
     * @param limit    最多返回条数
     * @return 对话列表
     */
    List<ChatHistory> selectRecentByUserId(
            @Param("userId") String userId,
            @Param("modelKey") String modelKey,
            @Param("limit") int limit
    );

    /**
     * 按 sessionId 查询全部对话（时间正序）
     * 用于 RAG 向量化时批量读取历史消息
     *
     * @param sessionId 会话 ID
     * @return 全量对话列表（正序）
     */
    /**
     * 查询某用户的所有历史会话列表（按最后更新时间倒序）
     *
     * @param userId 用户 ID
     * @return 包含标题、最新时间的会话列表
     */
    List<com.yzh.model.vo.HistorySessionVO> selectSessionList(@Param("userId") String userId);
}
