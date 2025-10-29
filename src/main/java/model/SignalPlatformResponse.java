package model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 信号机平台响应结果
 */
@Data
@AllArgsConstructor
public class SignalPlatformResponse {
    private boolean allowed;        // 是否允许操作
    private String lock;            // 分配的锁Token
    private String reason;          // 不允许的原因
    private Integer waitPosition;   // 等待队列位置

    public static SignalPlatformResponse success(String lock) {
        return new SignalPlatformResponse(true, lock, null, null);
    }

    public static SignalPlatformResponse fail(String reason) {
        return new SignalPlatformResponse(false, null, reason, null);
    }

    public static SignalPlatformResponse waiting(int position) {
        return new SignalPlatformResponse(false, null, "进入等待队列", position);
    }
}
