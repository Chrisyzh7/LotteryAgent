package com.yzh.model.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 历史会话 VO（用于侧边栏渲染）
 */
@Data
public class HistorySessionVO {
    
    /** 会话 ID */
    private String sessionId;
    
    /** 会话标题（通常取第一条用户消息的截断内容） */
    private String title;
    
    /** 会话最后更新时间（用于排序） */
    private LocalDateTime updateTime;
}
