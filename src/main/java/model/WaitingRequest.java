package model;

import lombok.Data;

/**
 * 等待队列项
 */

@Data
public class WaitingRequest {
    private String requestId;          // 请求ID
    private String action;             // 请求动作
    private String operatorId;         // 操作者ID（用于匹配锁的持有者）
    private long enqueueTime;          // 入队时间
    private long maxWaitTime;          // 最大等待时间(ms)，默认5分钟

    public boolean isTimeout() {
        return System.currentTimeMillis() - enqueueTime > maxWaitTime;
    }
}

