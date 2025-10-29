import adapter.SignalPlatformAdapter;
import model.SignalPlatformRequest;
import model.SignalPlatformResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 信号机平台适配器测试
 */
public class SignalPlatformAdapterTest {

    private SignalPlatformAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = SignalPlatformAdapter.getInstance();
    }

    @Test
    @DisplayName("测试1: 单例模式验证")
    void testSingletonPattern() {
        SignalPlatformAdapter adapter1 = SignalPlatformAdapter.getInstance();
        SignalPlatformAdapter adapter2 = SignalPlatformAdapter.getInstance();

        assertSame(adapter1, adapter2, "应该返回同一个实例");
        System.out.println("单例模式验证通过");
    }

    @Test
    @DisplayName("测试2: 首次操作获取锁")
    void testFirstOperationGetLock() {
        SignalPlatformRequest request = new SignalPlatformRequest();
        request.setDeviceId("device001");
        request.setSignalIP("192.168.1.100");
        request.setMode(10);  // 非退出模式
        request.setLock(null);

        SignalPlatformResponse response = adapter.operate(request);

        assertTrue(response.isAllowed(), "首次操作应该成功");
        assertNotNull(response.getLock(), "应该返回锁Token");
        System.out.println("首次操作获取锁: " + response.getLock());
    }

    @Test
    @DisplayName("测试3: mode转换为action - 退出操作")
    void testExitModeConversion() {
        // 测试mode=0 (退出)
        SignalPlatformRequest request1 = new SignalPlatformRequest();
        request1.setDeviceId("device001");
        request1.setSignalIP("192.168.1.100");
        request1.setMode(10);  // 先获取锁
        request1.setLock(null);

        SignalPlatformResponse response1 = adapter.operate(request1);
        String token = response1.getLock();

        // mode=0应该转换为exit
        SignalPlatformRequest exitRequest = new SignalPlatformRequest();
        exitRequest.setDeviceId("device001");
        exitRequest.setSignalIP("192.168.1.100");
        exitRequest.setMode(0);  // 退出模式
        exitRequest.setLock(token);

        SignalPlatformResponse exitResponse = adapter.operate(exitRequest);
        assertTrue(exitResponse.isAllowed(), "退出操作应该成功");
        System.out.println("mode=0退出测试通过");
    }

    @Test
    @DisplayName("测试4: mode转换为action - 所有退出值")
    void testAllExitModes() {
        int[] exitModes = {0, 60, 62, 63};

        for (int mode : exitModes) {
            // 先获取锁
            SignalPlatformRequest request = new SignalPlatformRequest();
            request.setDeviceId("device_" + mode);
            request.setSignalIP("192.168.1.10" + mode);
            request.setMode(100);  // 非退出模式
            request.setLock(null);

            SignalPlatformResponse response = adapter.operate(request);
            String token = response.getLock();

            // 使用退出模式
            SignalPlatformRequest exitRequest = new SignalPlatformRequest();
            exitRequest.setDeviceId("device_" + mode);
            exitRequest.setSignalIP("192.168.1.10" + mode);
            exitRequest.setMode(mode);
            exitRequest.setLock(token);

            SignalPlatformResponse exitResponse = adapter.operate(exitRequest);
            assertTrue(exitResponse.isAllowed(), "mode=" + mode + " 应该被识别为退出操作");
            System.out.println("mode=" + mode + " 退出测试通过");
        }
    }

    @Test
    @DisplayName("测试5: mode转换为action - 有效动作")
    void testValidActionModeConversion() {
        SignalPlatformRequest request = new SignalPlatformRequest();
        request.setDeviceId("device002");
        request.setSignalIP("192.168.1.101");
        request.setMode(25);  // 有效动作
        request.setLock(null);

        SignalPlatformResponse response = adapter.operate(request);

        assertTrue(response.isAllowed(), "有效动作应该成功");
        assertNotNull(response.getLock(), "应该返回锁Token");
        System.out.println("mode=25转换为有效动作测试通过");
    }

    @Test
    @DisplayName("测试6: 使用有效锁刷新操作")
    void testRefreshWithValidLock() {
        // 首次获取锁
        SignalPlatformRequest request1 = new SignalPlatformRequest();
        request1.setDeviceId("device003");
        request1.setSignalIP("192.168.1.102");
        request1.setMode(15);
        request1.setLock(null);

        SignalPlatformResponse response1 = adapter.operate(request1);
        String lock = response1.getLock();

        // 使用相同锁刷新
        SignalPlatformRequest request2 = new SignalPlatformRequest();
        request2.setDeviceId("device003");
        request2.setSignalIP("192.168.1.102");
        request2.setMode(20);
        request2.setLock(lock);

        SignalPlatformResponse response2 = adapter.operate(request2);

        assertTrue(response2.isAllowed(), "持有有效锁应该可以刷新");
        assertEquals(lock, response2.getLock(), "应该返回相同的锁Token");
        System.out.println("锁刷新测试通过");
    }

    @Test
    @DisplayName("测试7: 无效锁进入等待队列")
    void testInvalidLockEnqueueWaiting() {
        // 设备A获取锁
        SignalPlatformRequest requestA = new SignalPlatformRequest();
        requestA.setDeviceId("deviceA");
        requestA.setSignalIP("192.168.1.200");
        requestA.setMode(10);
        requestA.setLock(null);

        SignalPlatformResponse responseA = adapter.operate(requestA);
        assertTrue(responseA.isAllowed());

        // 设备B尝试获取锁，应该进入等待队列
        SignalPlatformRequest requestB = new SignalPlatformRequest();
        requestB.setDeviceId("deviceB");
        requestB.setSignalIP("192.168.1.200");  // 相同IP
        requestB.setMode(20);
        requestB.setLock(null);

        SignalPlatformResponse responseB = adapter.operate(requestB);

        assertFalse(responseB.isAllowed(), "设备B应该进入等待队列");
        assertEquals(1, responseB.getWaitPosition(), "应该在等待队列第1位");
        System.out.println("设备B进入等待队列，位置: " + responseB.getWaitPosition());
    }

    @Test
    @DisplayName("测试8: 参数验证")
    void testParameterValidation() {
        // 空请求
        SignalPlatformResponse response1 = adapter.operate(null);
        assertFalse(response1.isAllowed());
        assertEquals("请求参数为空", response1.getReason());

        // deviceId为null
        SignalPlatformRequest request2 = new SignalPlatformRequest();
        request2.setDeviceId(null);
        request2.setSignalIP("192.168.1.100");
        request2.setMode(10);

        SignalPlatformResponse response2 = adapter.operate(request2);
        assertFalse(response2.isAllowed());
        assertEquals("必要参数缺失", response2.getReason());

        // signalIP为null
        SignalPlatformRequest request3 = new SignalPlatformRequest();
        request3.setDeviceId("device001");
        request3.setSignalIP(null);
        request3.setMode(10);

        SignalPlatformResponse response3 = adapter.operate(request3);
        assertFalse(response3.isAllowed());
        assertEquals("必要参数缺失", response3.getReason());

        // mode为null
        SignalPlatformRequest request4 = new SignalPlatformRequest();
        request4.setDeviceId("device001");
        request4.setSignalIP("192.168.1.100");
        request4.setMode(null);

        SignalPlatformResponse response4 = adapter.operate(request4);
        assertFalse(response4.isAllowed());
        assertEquals("必要参数缺失", response4.getReason());

        System.out.println("参数验证测试通过");
    }

    @Test
    @DisplayName("测试9: 完整工作流")
    void testCompleteWorkflow() throws InterruptedException {
        System.out.println("\n=== 信号机平台完整工作流测试 ===");

        // 1. 设备A首次控制信号机
        SignalPlatformRequest req1 = new SignalPlatformRequest();
        req1.setDeviceId("deviceA");
        req1.setSignalIP("192.168.1.50");
        req1.setMode(10);
        req1.setLock(null);

        SignalPlatformResponse resp1 = adapter.operate(req1);
        System.out.println("1. 设备A获取锁: " + resp1.getLock());
        assertTrue(resp1.isAllowed());
        String lockA = resp1.getLock();

        // 2. 设备A继续操作（刷新）
        SignalPlatformRequest req2 = new SignalPlatformRequest();
        req2.setDeviceId("deviceA");
        req2.setSignalIP("192.168.1.50");
        req2.setMode(15);
        req2.setLock(lockA);

        SignalPlatformResponse resp2 = adapter.operate(req2);
        System.out.println("2. 设备A刷新操作成功");
        assertTrue(resp2.isAllowed());

        // 3. 设备B尝试控制，进入等待队列
        SignalPlatformRequest req3 = new SignalPlatformRequest();
        req3.setDeviceId("deviceB");
        req3.setSignalIP("192.168.1.50");
        req3.setMode(20);
        req3.setLock(null);

        SignalPlatformResponse resp3 = adapter.operate(req3);
        System.out.println("3. 设备B进入等待队列，位置: " + resp3.getWaitPosition());
        assertFalse(resp3.isAllowed());

        // 4. 设备A退出（mode=0）
        SignalPlatformRequest req4 = new SignalPlatformRequest();
        req4.setDeviceId("deviceA");
        req4.setSignalIP("192.168.1.50");
        req4.setMode(0);  // 退出
        req4.setLock(lockA);

        SignalPlatformResponse resp4 = adapter.operate(req4);
        System.out.println("4. 设备A退出，释放锁");
        assertTrue(resp4.isAllowed());

        Thread.sleep(100);

        // 5. 设备B现在可以认领锁
        SignalPlatformRequest req5 = new SignalPlatformRequest();
        req5.setDeviceId("deviceB");
        req5.setSignalIP("192.168.1.50");
        req5.setMode(20);
        req5.setLock(null);

        SignalPlatformResponse resp5 = adapter.operate(req5);
        System.out.println("5. 设备B认领锁: " + resp5.getLock());
        assertTrue(resp5.isAllowed());

        System.out.println("=== 信号机平台完整工作流测试通过 ===\n");
    }

    @Test
    @DisplayName("测试10: 不同mode值的转换")
    void testDifferentModeValues() {
        int[] testModes = {1, 5, 10, 20, 30, 50, 61, 64, 100};

        for (int mode : testModes) {
            SignalPlatformRequest request = new SignalPlatformRequest();
            request.setDeviceId("device_mode_" + mode);
            request.setSignalIP("192.168.2." + mode);
            request.setMode(mode);
            request.setLock(null);

            SignalPlatformResponse response = adapter.operate(request);

            assertTrue(response.isAllowed(), "mode=" + mode + " 应该成功获取锁");
            assertNotNull(response.getLock(), "mode=" + mode + " 应该返回锁Token");
            System.out.println("mode=" + mode + " 转换测试通过，锁: " + response.getLock());
        }
    }
}
