package com.yzh.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一 API 响应体 VO（泛型）
 *
 * @param <T> 数据载体类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse<T> {

    /** 状态码：200 成功，其他为错误 */
    private Integer code;

    /** 提示信息 */
    private String message;

    /** 响应数据 */
    private T data;

    // -------- 快速构造工厂方法 --------

    public static <T> ChatResponse<T> success(T data) {
        return new ChatResponse<>(200, "success", data);
    }

    public static <T> ChatResponse<T> error(String message) {
        return new ChatResponse<>(500, message, null);
    }
}
