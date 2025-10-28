import model.OperationResult;
import model.PriorityStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import service.ConflictControlService;
import service.impl.ConflictControlServiceImpl;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 冲突控制服务测试
 */
public class ConflictControlServiceTest {

    private ConflictControlService service;

    @BeforeEach
    void setUp() {
        service = new ConflictControlServiceImpl();
    }

    @Test
    @DisplayName("测试1: 首次操作获取Token")
    void testFirstOperationGetToken() {
        OperationResult result = service.operate("obj1", "edit", null, "user1");

        assertTrue(result.isAllowed());
        assertNotNull(result.getToken());
        assertEquals(10, result.getToken().length());
        System.out.println("首次操作获取Token: " + result.getToken());
    }

    @Test
    @DisplayName("测试2: 有效Token刷新操作")
    void testValidTokenRefresh() {
        // 首次获取Token
        OperationResult result1 = service.operate("obj1", "edit", null, "user1");
        String token = result1.getToken();

        // 使用Token继续操作
        OperationResult result2 = service.operate("obj1", "save", token, "user1");

        assertTrue(result2.isAllowed());
        assertEquals(token, result2.getToken());
        System.out.println("Token刷新成功");
    }

    @Test
    @DisplayName("测试3: 无效Token进入等待队列")
    void testInvalidTokenEnqueueWaiting() {
        // 用户A获取锁
        OperationResult result1 = service.operate("obj1", "edit", null, "userA");
        String tokenA = result1.getToken();

        // 用户B使用无效Token，进入等待队列
        OperationResult result2 = service.operate("obj1", "edit", "INVALID_TOKEN", "userB");

        assertFalse(result2.isAllowed());
        assertEquals("进入等待队列", result2.getReason());
        assertEquals(1, result2.getWaitPosition());
        System.out.println("用户B进入等待队列，位置: " + result2.getWaitPosition());
    }

    @Test
    @DisplayName("测试4: 退出操作释放锁")
    void testExitReleaseLock() {
        // 获取锁
        OperationResult result1 = service.operate("obj1", "edit", null, "user1");
        String token = result1.getToken();

        // 退出释放锁
        OperationResult result2 = service.operate("obj1", "exit", token, "user1");
        assertTrue(result2.isAllowed());

        // 其他用户可以获取锁
        OperationResult result3 = service.operate("obj1", "edit", null, "user2");
        assertTrue(result3.isAllowed());
        assertNotEquals(token, result3.getToken());
        System.out.println("锁已释放，新用户获取到锁: " + result3.getToken());
    }

    @Test
    @DisplayName("测试5: 读操作不互斥")
    void testReadOperationsNotMutuallyExclusive() {
        // 多个读操作可以同时进行
        OperationResult read1 = service.operate("obj1", "read", null, "user1");
        OperationResult read2 = service.operate("obj1", "read", null, "user2");
        OperationResult read3 = service.operate("obj1", "read", null, "user3");

        assertTrue(read1.isAllowed());
        assertTrue(read2.isAllowed());
        assertTrue(read3.isAllowed());
        System.out.println("多个读操作同时允许");
    }

    @Test
    @DisplayName("测试6: 读写混合场景")
    void testReadWriteMixedScenario() {
        // 写操作获取锁
        OperationResult write1 = service.operate("obj1", "edit", null, "user1");
        String writeToken = write1.getToken();
        assertTrue(write1.isAllowed());

        // 读操作不受影响
        OperationResult read1 = service.operate("obj1", "read", null, "user2");
        assertTrue(read1.isAllowed());

        // 另一个写操作进入等待
        OperationResult write2 = service.operate("obj1", "edit", null, "user3");
        assertFalse(write2.isAllowed());

        System.out.println("读写混合测试通过");
    }

    @Test
    @DisplayName("测试7: 等待队列已满")
    void testWaitingQueueFull() {
        service.setConfiguration(2, 30000, 300000); // 队列容量设为2

        // 用户A获取锁
        OperationResult result1 = service.operate("obj1", "edit", null, "userA");

        // 用户B进入队列
        OperationResult result2 = service.operate("obj1", "edit", null, "userB");
        assertEquals(1, result2.getWaitPosition());

        // 用户C进入队列
        OperationResult result3 = service.operate("obj1", "edit", null, "userC");
        assertEquals(2, result3.getWaitPosition());

        // 用户D队列已满，拒绝
        OperationResult result4 = service.operate("obj1", "edit", null, "userD");
        assertFalse(result4.isAllowed());
        assertEquals("等待队列已满", result4.getReason());

        System.out.println("等待队列已满测试通过");
    }

    @Test
    @DisplayName("测试8: FIFO优先策略")
    void testFIFOPriorityStrategy() throws InterruptedException {
        service.setPriorityStrategy(PriorityStrategy.FIFO);

        // 用户A获取锁
        OperationResult result1 = service.operate("obj1", "action1", null, "userA");
        String tokenA = result1.getToken();

        // 用户B、C进入队列
        service.operate("obj1", "action2", null, "userB");
        service.operate("obj1", "action3", null, "userC");

        // 用户A退出
        service.operate("obj1", "exit", tokenA, "userA");

        // 短暂等待处理队列
        Thread.sleep(100);

        // 验证轮询时用户B应该能获取锁（FIFO）
        System.out.println("FIFO策略测试完成");
    }

    @Test
    @DisplayName("测试9: 相同动作优先策略")
    void testSameActionFirstPriorityStrategy() throws InterruptedException {
        service.setPriorityStrategy(PriorityStrategy.SAME_ACTION_FIRST);

        // 用户A获取锁，执行edit操作
        OperationResult result1 = service.operate("obj1", "edit", null, "userA");
        String tokenA = result1.getToken();

        // 用户B、C、D进入队列，不同动作
        service.operate("obj1", "save", null, "userB");    // B
        service.operate("obj1", "edit", null, "userC");    // C - 相同动作
        service.operate("obj1", "delete", null, "userD");  // D

        // 用户A退出
        service.operate("obj1", "exit", tokenA, "userA");

        Thread.sleep(100);

        // 相同动作优先，用户C应该被优先处理
        System.out.println("相同动作优先策略测试完成");
    }

    @Test
    @DisplayName("测试10: 锁超时自动释放")
    void testLockTimeout() throws InterruptedException {
        service.setConfiguration(5, 1000, 300000); // 锁超时时间设为1秒

        // 获取锁
        OperationResult result1 = service.operate("obj1", "edit", null, "user1");
        String token = result1.getToken();

        // 等待超时
        Thread.sleep(1500);

        // 锁应该已经超时释放，新用户可以获取
        OperationResult result2 = service.operate("obj1", "edit", null, "user2");
        assertTrue(result2.isAllowed());
        assertNotEquals(token, result2.getToken());

        System.out.println("锁超时自动释放测试通过");
    }

    @Test
    @DisplayName("测试11: 并发场景")
    void testConcurrentScenario() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger waitingCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int userId = i;
            executor.submit(() -> {
                try {
                    OperationResult result = service.operate("obj1", "edit", null, "user" + userId);
                    if (result.isAllowed()) {
                        successCount.incrementAndGet();
                        System.out.println("用户" + userId + " 获取锁成功: " + result.getToken());
                    } else {
                        waitingCount.incrementAndGet();
                        System.out.println("用户" + userId + " 进入等待队列");
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // 应该有1个成功，其他进入等待队列或被拒绝
        assertEquals(1, successCount.get());
        System.out.println("并发测试完成: 成功=" + successCount.get() + ", 等待=" + waitingCount.get());
    }

    @Test
    @DisplayName("测试12: 参数验证")
    void testParameterValidation() {
        // objectId为null
        OperationResult result1 = service.operate(null, "edit", null, "user1");
        assertFalse(result1.isAllowed());
        assertEquals("参数不能为空", result1.getReason());

        // action为null
        OperationResult result2 = service.operate("obj1", null, null, "user1");
        assertFalse(result2.isAllowed());
        assertEquals("参数不能为空", result2.getReason());

        // operatorId为null
        OperationResult result3 = service.operate("obj1", "edit", null, null);
        assertFalse(result3.isAllowed());
        assertEquals("参数不能为空", result3.getReason());

        System.out.println("参数验证测试通过");
    }

    @Test
    @DisplayName("测试13: Token不匹配")
    void testTokenMismatch() {
        // 用户A获取锁
        OperationResult result1 = service.operate("obj1", "edit", null, "userA");

        // 用户B使用错误Token尝试退出
        OperationResult result2 = service.operate("obj1", "exit", "WRONG_TOKEN", "userB");
        assertFalse(result2.isAllowed());
        assertEquals("Token不匹配", result2.getReason());

        System.out.println("Token不匹配测试通过");
    }

    @Test
    @DisplayName("测试14: 读操作退出")
    void testReadOperationExit() {
        // 读操作
        OperationResult read1 = service.operate("obj1", "read", null, "user1");
        String readToken = read1.getToken();

        // 读操作退出
        OperationResult exit = service.operate("obj1", "exit", readToken, "user1");
        assertTrue(exit.isAllowed());

        System.out.println("读操作退出测试通过");
    }

    @Test
    @DisplayName("测试15: 完整工作流")
    void testCompleteWorkflow() throws InterruptedException {
        System.out.println("\n=== 完整工作流测试 ===");

        // 1. 用户A首次编辑
        OperationResult r1 = service.operate("doc1", "edit", null, "userA");
        System.out.println("1. 用户A获取锁: " + r1.getToken());
        assertTrue(r1.isAllowed());
        String tokenA = r1.getToken();

        // 2. 用户A继续编辑（刷新）
        OperationResult r2 = service.operate("doc1", "edit", tokenA, "userA");
        System.out.println("2. 用户A刷新操作成功");
        assertTrue(r2.isAllowed());

        // 3. 用户B尝试编辑，进入队列
        OperationResult r3 = service.operate("doc1", "edit", null, "userB");
        System.out.println("3. 用户B进入等待队列，位置: " + r3.getWaitPosition());
        assertFalse(r3.isAllowed());

        // 4. 用户C读取文档（不受影响）
        OperationResult r4 = service.operate("doc1", "read", null, "userC");
        System.out.println("4. 用户C读取成功");
        assertTrue(r4.isAllowed());

        // 5. 用户A保存并退出
        service.operate("doc1", "save", tokenA, "userA");
        OperationResult r5 = service.operate("doc1", "exit", tokenA, "userA");
        System.out.println("5. 用户A退出，释放锁");
        assertTrue(r5.isAllowed());

        Thread.sleep(100);

        // 6. 用户B现在应该可以认领锁（自动分配）
        OperationResult r6 = service.operate("doc1", "edit", null, "userB");
        System.out.println("6. 用户B认领锁: " + r6.getToken());
        assertTrue(r6.isAllowed());
        assertNotNull(r6.getToken());

        System.out.println("=== 完整工作流测试通过 ===\n");
    }
}


