package com.yzh.util;

/**
 * 使用 ThreadLocal 存储当前请求的用户上下文信息 (UserId)
 * 在 LoginInterceptor 中放入，在请求结束时清理
 */
public class UserContext {

    private static final ThreadLocal<String> USER_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME_HOLDER = new ThreadLocal<>();

    /**
     * 存入 userId
     */
    public static void setUserId(String userId) {
        USER_ID_HOLDER.set(userId);
    }

    /**
     * 获取 userId
     */
    public static String getUserId() {
        return USER_ID_HOLDER.get();
    }

    /**
     * 存入 username
     */
    public static void setUsername(String username) {
        USERNAME_HOLDER.set(username);
    }

    /**
     * 获取 username
     */
    public static String getUsername() {
        return USERNAME_HOLDER.get();
    }

    /**
     * 清理 ThreadLocal（必须在请求处理完成后调用，防止内存泄漏）
     */
    public static void remove() {
        USER_ID_HOLDER.remove();
        USERNAME_HOLDER.remove();
    }
}
