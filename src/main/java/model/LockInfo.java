package model;

import lombok.Data;

/**
 * 锁信息
 */
@Data
public class LockInfo {
    private String token;              // 锁Token
    private String action;             // 执行的动作
    private String ownerId;            // 操作者ID（用action作为标识）
    private long acquireTime;          // 获取时间
    private long lastRefreshTime;      // 最后刷新时间
    private long maxHoldTime;          // 最大持有时间(ms)，默认30秒
    private boolean pendingClaim;      // 待认领标志（自动分配但未被认领）

    public boolean isExpired() {
        return System.currentTimeMillis() - lastRefreshTime > maxHoldTime;
    }

    public void refresh() {
        this.lastRefreshTime = System.currentTimeMillis();
    }

    /**
     * 认领锁（将待认领状态转为正式持有）
     */
    public void claim() {
        this.pendingClaim = false;
        this.lastRefreshTime = System.currentTimeMillis();
    }
}

