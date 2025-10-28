package service.impl;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.ConflictControlService;

/**
 * 冲突控制管理服务实现
 */
public class ConflictControlServiceImpl implements ConflictControlService {

    private static final Logger logger = LoggerFactory.getLogger(ConflictControlServiceImpl.class);

    // 操作对象状态映射
    private final ConcurrentHashMap<String, ObjectState> objectStates = new ConcurrentHashMap<>();

    // 每个对象的读写锁
    private final ConcurrentHashMap<String, ReentrantReadWriteLock> objectLocks = new ConcurrentHashMap<>();

    // 优先策略
    private volatile PriorityStrategy priorityStrategy = PriorityStrategy.FIFO;

    // 配置参数
    private volatile int maxQueueSize = 5;
    private volatile long lockMaxHoldTime = 30000;  // 30秒
    private volatile long maxWaitTime = 300000;      // 5分钟

    // Token生成器
    private final TokenGenerator tokenGenerator = new TokenGenerator();

    @Override
    public OperationResult operate(String objectId, String action, String token, String operatorId) {
        if (objectId == null || action == null || operatorId == null) {
            return OperationResult.fail("参数不能为空");
        }

        logger.info("操作请求: objectId={}, action={}, token={}, operatorId={}",
                objectId, action, token, operatorId);

        // 获取或创建对象锁
        ReentrantReadWriteLock rwLock = objectLocks.computeIfAbsent(
                objectId, k -> new ReentrantReadWriteLock(true)
        );

        // 判断是读操作还是写操作
        boolean isReadOperation = "read".equalsIgnoreCase(action);
        boolean isExitOperation = "exit".equalsIgnoreCase(action);

        if (isReadOperation) {
            return handleReadOperation(objectId, rwLock);
        } else if (isExitOperation) {
            return handleExitOperation(objectId, token, rwLock);
        } else {
            return handleWriteOperation(objectId, action, token, operatorId, rwLock);
        }
    }

    /**
     * 处理读操作（不互斥）
     */
    private OperationResult handleReadOperation(String objectId, ReentrantReadWriteLock rwLock) {
        rwLock.writeLock().lock();
        try {
            ObjectState state = getOrCreateObjectState(objectId);

            // 清理过期锁
            cleanExpiredLock(state);

            // 读操作不需要Token，直接增加读计数
            state.setReadCount(state.getReadCount() + 1);

            logger.info("读操作成功: objectId={}, 当前读数量={}", objectId, state.getReadCount());
            return OperationResult.success("READ_" + UUID.randomUUID().toString().substring(0, 8));

        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 处理退出操作
     */
    private OperationResult handleExitOperation(String objectId, String token, ReentrantReadWriteLock rwLock) {
        rwLock.writeLock().lock();
        try {
            ObjectState state = objectStates.get(objectId);
            if (state == null) {
                return OperationResult.fail("对象不存在");
            }

            // 如果是读操作的退出
            if (token != null && token.startsWith("READ_")) {
                if (state.getReadCount() > 0) {
                    state.setReadCount(state.getReadCount() - 1);
                    logger.info("读操作退出: objectId={}, 剩余读数量={}", objectId, state.getReadCount());
                }
                return OperationResult.success(null);
            }

            // 写操作的退出
            LockInfo currentLock = state.getCurrentLock();
            if (currentLock == null) {
                return OperationResult.fail("对象未被锁定");
            }

            if (!currentLock.getToken().equals(token)) {
                return OperationResult.fail("Token不匹配");
            }

            // 释放锁
            releaseLock(state);
            logger.info("写操作退出，锁已释放: objectId={}, token={}", objectId, token);

            // 处理等待队列
            processWaitingQueue(state, rwLock);

            return OperationResult.success(null);

        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 处理写操作
     */
    private OperationResult handleWriteOperation(String objectId, String action, String token,
                                                 String operatorId, ReentrantReadWriteLock rwLock) {
        rwLock.writeLock().lock();
        try {
            ObjectState state = getOrCreateObjectState(objectId);

            // 清理过期锁
            cleanExpiredLock(state);

            LockInfo currentLock = state.getCurrentLock();

            // 情况1：对象空闲，首次操作
            if (currentLock == null && token == null) {
                return acquireNewLock(state, action, operatorId);
            }

            // 情况2：持有有效Token，刷新操作
            if (currentLock != null && token != null && currentLock.getToken().equals(token)) {
                currentLock.refresh();
                currentLock.setAction(action);
                logger.info("Token刷新成功: objectId={}, action={}, token={}, operatorId={}",
                        objectId, action, token, operatorId);
                return OperationResult.success(token);
            }

            // 情况3：Token不匹配，检查是否为待认领状态
            if (currentLock != null && token == null) {
                // 检查操作者ID是否匹配
                if (currentLock.isPendingClaim() && operatorId.equals(currentLock.getOwnerId())) {
                    // 操作者ID匹配且锁处于待认领状态，允许认领
                    currentLock.claim();
                    String claimedToken = currentLock.getToken();
                    logger.info("锁认领成功: objectId={}, action={}, token={}, operatorId={}",
                            objectId, action, claimedToken, operatorId);
                    return OperationResult.success(claimedToken);
                }
            }

            // 情况4：Token无效
            if (token != null) {
                logger.warn("Token无效或已过期: objectId={}, token={}", objectId, token);
            }

            // 情况5：进入等待队列
            return enqueueWaitingRequest(state, action, operatorId, objectId);

        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 获取新锁
     */
    private OperationResult acquireNewLock(ObjectState state, String action, String operatorId) {
        String newToken = tokenGenerator.generate();
        LockInfo lockInfo = new LockInfo();
        lockInfo.setToken(newToken);
        lockInfo.setAction(action);
        lockInfo.setOwnerId(operatorId); // 使用传入的operatorId
        long now = System.currentTimeMillis();
        lockInfo.setAcquireTime(now);
        lockInfo.setLastRefreshTime(now);
        lockInfo.setMaxHoldTime(lockMaxHoldTime);
        lockInfo.setPendingClaim(false); // 正常获取的锁，不需要认领

        state.setCurrentLock(lockInfo);

        logger.info("获取新锁: objectId={}, action={}, token={}, operatorId={}",
                state.getObjectId(), action, newToken, operatorId);

        return OperationResult.success(newToken);
    }

    /**
     * 自动分配锁（待认领状态）
     */
    private void acquireNewLockWithPendingClaim(ObjectState state, String action, String operatorId) {
        String newToken = tokenGenerator.generate();
        LockInfo lockInfo = new LockInfo();
        lockInfo.setToken(newToken);
        lockInfo.setAction(action);
        lockInfo.setOwnerId(operatorId); // 使用等待队列中的operatorId
        long now = System.currentTimeMillis();
        lockInfo.setAcquireTime(now);
        lockInfo.setLastRefreshTime(now);
        lockInfo.setMaxHoldTime(lockMaxHoldTime);
        lockInfo.setPendingClaim(true); // 设置为待认领状态

        state.setCurrentLock(lockInfo);

        logger.info("自动分配锁（待认领）: objectId={}, action={}, token={}, operatorId={}",
                state.getObjectId(), action, newToken, operatorId);
    }

    /**
     * 加入等待队列
     */
    private OperationResult enqueueWaitingRequest(ObjectState state, String action, String operatorId, String objectId) {
        Queue<WaitingRequest> queue = state.getWaitingQueue();

        // 清理超时的等待请求
        queue.removeIf(WaitingRequest::isTimeout);

        if (queue.size() >= state.getMaxQueueSize()) {
            logger.warn("等待队列已满: objectId={}, queueSize={}", objectId, queue.size());
            return OperationResult.fail("等待队列已满");
        }

        WaitingRequest waitingRequest = new WaitingRequest();
        waitingRequest.setRequestId(UUID.randomUUID().toString());
        waitingRequest.setAction(action);
        waitingRequest.setOperatorId(operatorId); // 使用传入的operatorId
        waitingRequest.setEnqueueTime(System.currentTimeMillis());
        waitingRequest.setMaxWaitTime(maxWaitTime);

        queue.offer(waitingRequest);

        int position = queue.size();
        logger.info("进入等待队列: objectId={}, action={}, operatorId={}, position={}",
                objectId, action, operatorId, position);

        return OperationResult.waiting(position);
    }

    /**
     * 释放锁
     */
    private void releaseLock(ObjectState state) {
        state.setCurrentLock(null);
    }

    /**
     * 清理过期锁
     */
    private void cleanExpiredLock(ObjectState state) {
        LockInfo currentLock = state.getCurrentLock();
        if (currentLock != null && currentLock.isExpired()) {
            logger.warn("锁已超时自动释放: objectId={}, token={}",
                    state.getObjectId(), currentLock.getToken());
            releaseLock(state);
            processWaitingQueue(state, null);
        }
    }

    /**
     * 处理等待队列
     * 自动分配锁给队列中的下一个操作者
     */
    private void processWaitingQueue(ObjectState state, ReentrantReadWriteLock rwLock) {
        Queue<WaitingRequest> queue = state.getWaitingQueue();

        // 清理超时请求
        queue.removeIf(req -> {
            boolean timeout = req.isTimeout();
            if (timeout) {
                logger.info("等待请求超时移除: objectId={}, action={}",
                        state.getObjectId(), req.getAction());
            }
            return timeout;
        });

        if (queue.isEmpty()) {
            return;
        }

        // 根据优先策略获取下一个请求
        WaitingRequest nextRequest = getNextRequest(queue, state.getCurrentLock());

        if (nextRequest != null) {
            logger.info("从等待队列自动分配锁: objectId={}, action={}, operatorId={}",
                    state.getObjectId(), nextRequest.getAction(), nextRequest.getOperatorId());
            // 自动分配锁，设置为待认领状态
            acquireNewLockWithPendingClaim(state, nextRequest.getAction(), nextRequest.getOperatorId());
        }
    }

    /**
     * 根据优先策略获取下一个请求
     */
    private WaitingRequest getNextRequest(Queue<WaitingRequest> queue, LockInfo lastLock) {
        if (priorityStrategy == PriorityStrategy.FIFO) {
            return queue.poll();
        } else if (priorityStrategy == PriorityStrategy.SAME_ACTION_FIRST) {
            // 优先查找相同动作
            if (lastLock != null) {
                String lastAction = lastLock.getAction();
                Iterator<WaitingRequest> iterator = queue.iterator();
                while (iterator.hasNext()) {
                    WaitingRequest req = iterator.next();
                    if (req.getAction().equals(lastAction)) {
                        iterator.remove();
                        logger.info("相同动作优先: action={}", lastAction);
                        return req;
                    }
                }
            }
            // 没有相同动作，返回队首
            return queue.poll();
        }
        return queue.poll();
    }

    /**
     * 获取或创建对象状态
     */
    private ObjectState getOrCreateObjectState(String objectId) {
        return objectStates.computeIfAbsent(objectId, k -> {
            ObjectState state = new ObjectState();
            state.setObjectId(objectId);
            state.setCurrentLock(null);
            state.setReadCount(0);
            state.setWaitingQueue(new LinkedList<>());
            state.setMaxQueueSize(maxQueueSize);
            return state;
        });
    }

    @Override
    public void setPriorityStrategy(PriorityStrategy strategy) {
        this.priorityStrategy = strategy;
        logger.info("优先策略已设置为: {}", strategy);
    }

    @Override
    public void setConfiguration(int maxQueueSize, long lockMaxHoldTime, long maxWaitTime) {
        this.maxQueueSize = maxQueueSize;
        this.lockMaxHoldTime = lockMaxHoldTime;
        this.maxWaitTime = maxWaitTime;
        logger.info("配置已更新: maxQueueSize={}, lockMaxHoldTime={}ms, maxWaitTime={}ms",
                maxQueueSize, lockMaxHoldTime, maxWaitTime);
    }

    /**
     * Token生成器
     */
    private static class TokenGenerator {
        private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        private final Random random = new Random();

        public String generate() {
            StringBuilder sb = new StringBuilder(10);
            for (int i = 0; i < 10; i++) {
                sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
            }
            return sb.toString();
        }
    }
}


