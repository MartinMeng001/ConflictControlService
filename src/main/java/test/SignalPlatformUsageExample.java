package test;

import adapter.SignalPlatformAdapter;
import model.PriorityStrategy;
import model.SignalPlatformRequest;
import model.SignalPlatformResponse;

/**
 * 信号机平台适配器使用示例
 */
public class SignalPlatformUsageExample {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== 信号机平台适配器使用示例 ===\n");

        // 1. 获取单例实例
        SignalPlatformAdapter adapter = SignalPlatformAdapter.getInstance();
        System.out.println("1. 获取适配器单例实例成功\n");

        // 2. 可选：配置冲突控制服务参数
        adapter.getConflictControlService().setConfiguration(
                5,      // 最大队列容量
                30000,  // 锁最大持有时间(ms)
                300000  // 最大等待时间(ms)
        );
        adapter.getConflictControlService().setPriorityStrategy(PriorityStrategy.FIFO);
        System.out.println("2. 配置冲突控制服务参数\n");

        // 3. 设备A首次控制信号机
        System.out.println("3. 设备A首次控制信号机 192.168.1.100:");
        SignalPlatformRequest requestA1 = new SignalPlatformRequest();
        requestA1.setDeviceId("deviceA");
        requestA1.setSignalIP("192.168.1.100");
        requestA1.setMode(10);  // 控制模式10
        requestA1.setLock(null);

        SignalPlatformResponse responseA1 = adapter.operate(requestA1);
        if (responseA1.isAllowed()) {
            System.out.println("   成功获取锁: " + responseA1.getLock());
        } else {
            System.out.println("   失败: " + responseA1.getReason());
        }
        String lockA = responseA1.getLock();
        System.out.println();

        // 4. 设备A刷新操作（更改模式）
        System.out.println("4. 设备A刷新操作，切换到模式15:");
        SignalPlatformRequest requestA2 = new SignalPlatformRequest();
        requestA2.setDeviceId("deviceA");
        requestA2.setSignalIP("192.168.1.100");
        requestA2.setMode(15);  // 切换到模式15
        requestA2.setLock(lockA);

        SignalPlatformResponse responseA2 = adapter.operate(requestA2);
        if (responseA2.isAllowed()) {
            System.out.println("   刷新成功，锁: " + responseA2.getLock());
        } else {
            System.out.println("   失败: " + responseA2.getReason());
        }
        System.out.println();

        // 5. 设备B尝试控制同一信号机，应该进入等待队列
        System.out.println("5. 设备B尝试控制同一信号机:");
        SignalPlatformRequest requestB1 = new SignalPlatformRequest();
        requestB1.setDeviceId("deviceB");
        requestB1.setSignalIP("192.168.1.100");  // 相同信号机IP
        requestB1.setMode(20);
        requestB1.setLock(null);

        SignalPlatformResponse responseB1 = adapter.operate(requestB1);
        if (responseB1.isAllowed()) {
            System.out.println("   成功获取锁: " + responseB1.getLock());
        } else {
            System.out.println("   " + responseB1.getReason() + "，位置: " + responseB1.getWaitPosition());
        }
        System.out.println();

        // 6. 设备A退出（mode=0表示退出）
        System.out.println("6. 设备A退出控制 (mode=0):");
        SignalPlatformRequest requestA3 = new SignalPlatformRequest();
        requestA3.setDeviceId("deviceA");
        requestA3.setSignalIP("192.168.1.100");
        requestA3.setMode(0);  // 退出模式
        requestA3.setLock(lockA);

        SignalPlatformResponse responseA3 = adapter.operate(requestA3);
        if (responseA3.isAllowed()) {
            System.out.println("   退出成功，锁已释放");
        } else {
            System.out.println("   失败: " + responseA3.getReason());
        }
        System.out.println();

        // 等待一下，让队列处理
        Thread.sleep(100);

        // 7. 设备B现在可以认领锁
        System.out.println("7. 设备B认领锁:");
        SignalPlatformRequest requestB2 = new SignalPlatformRequest();
        requestB2.setDeviceId("deviceB");
        requestB2.setSignalIP("192.168.1.100");
        requestB2.setMode(20);
        requestB2.setLock(null);

        SignalPlatformResponse responseB2 = adapter.operate(requestB2);
        if (responseB2.isAllowed()) {
            System.out.println("   成功认领锁: " + responseB2.getLock());
            String lockB = responseB2.getLock();

            // 8. 设备B使用其他退出模式退出 (mode=60)
            System.out.println("\n8. 设备B使用mode=60退出:");
            SignalPlatformRequest requestB3 = new SignalPlatformRequest();
            requestB3.setDeviceId("deviceB");
            requestB3.setSignalIP("192.168.1.100");
            requestB3.setMode(60);  // 另一个退出模式
            requestB3.setLock(lockB);

            SignalPlatformResponse responseB3 = adapter.operate(requestB3);
            if (responseB3.isAllowed()) {
                System.out.println("   退出成功");
            } else {
                System.out.println("   失败: " + responseB3.getReason());
            }
        } else {
            System.out.println("   失败: " + responseB2.getReason());
        }

        System.out.println("\n=== 示例结束 ===");
    }
}
