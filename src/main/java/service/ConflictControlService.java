package service;

import model.OperationResult;
import model.PriorityStrategy;

/**
 * 冲突控制管理服务接口
 */
public interface ConflictControlService {

    /**
     * 执行操作
     *
     * @param objectId 操作对象ID
     * @param action 操作动作
     *               - "read": 读操作（不互斥）
     *               - "exit": 退出操作（释放锁）
     *               - 其他: 写操作（互斥）
     * @param token 操作Token（首次为null，后续操作需要携带）
     * @param operatorId 操作者唯一标识（用于识别操作者身份，如用户ID）
     * @return 操作结果
     */
    OperationResult operate(String objectId, String action, String token, String operatorId);

    /**
     * 设置优先策略
     *
     * @param strategy 优先策略
     *                 - FIFO: 先进先出
     *                 - SAME_ACTION_FIRST: 相同动作优先
     */
    void setPriorityStrategy(PriorityStrategy strategy);

    /**
     * 设置配置参数
     *
     * @param maxQueueSize 最大队列容量（默认5）
     * @param lockMaxHoldTime 锁最大持有时间(ms)（默认30秒）
     * @param maxWaitTime 最大等待时间(ms)（默认5分钟）
     */
    void setConfiguration(int maxQueueSize, long lockMaxHoldTime, long maxWaitTime);
}
