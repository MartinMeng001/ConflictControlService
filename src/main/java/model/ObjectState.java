package model;

import lombok.Data;
import java.util.Queue;

/**
 * 操作对象状态
 */
@Data
public class ObjectState {
    private String objectId;
    private LockInfo currentLock;      // 当前锁信息（null表示空闲）
    private int readCount;             // 当前读操作数量
    private Queue<WaitingRequest> waitingQueue; // 等待队列
    private int maxQueueSize;          // 最大队列容量，默认5
}
