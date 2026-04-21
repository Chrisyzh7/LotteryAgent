package com.yzh.draw.model;

import com.yzh.draw.model.request.DrawRequest;
import com.yzh.draw.model.response.DrawResponse;
import lombok.Data;

@Data
public class DrawContext {
    private DrawRequest request;
    private DrawResponse response;
    
    /** 选中的奖品信息缓冲对象，可由策略计算得出 */
    private Object prizeInfo;
    
    /** 标记是否在责任链校验中被拦截 */
    private boolean intercept;
    
    /** 拦截的具体原因 */
    private String interceptMessage;
}
