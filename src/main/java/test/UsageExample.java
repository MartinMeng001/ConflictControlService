package test;

import model.OperationResult;
import model.PriorityStrategy;
import service.ConflictControlService;
import service.impl.ConflictControlServiceImpl;

/**
 * 冲突控制服务使用示例
 */
public class UsageExample {

    public static void main(String[] args) throws InterruptedException {
        // 创建服务实例
        ConflictControlService service = new ConflictControlServiceImpl();

        // 设置配置（可选）
        service.setConfiguration(
                5,          // 最大队列容量
                300000,      // 锁最大持有时间30秒
                300000      // 最大等待时间5分钟
        );

        // 设置优先策略（可选）
        service.setPriorityStrategy(PriorityStrategy.FIFO);

        System.out.println("=== 场景1: 基本的编辑流程 ===");
        basicEditFlow(service);

        System.out.println("\n=== 场景2: 冲突处理 ===");
        conflictHandling(service);

        System.out.println("\n=== 场景3: 读写混合 ===");
        readWriteMix(service);
    }

    /**
     * 场景1: 基本的编辑流程
     */
    private static void basicEditFlow(ConflictControlService service) {
        String objectId = "document-001";

        // 用户首次编辑文档
        OperationResult result1 = service.operate(objectId, "edit", null, "user1");
        if (result1.isAllowed()) {
            String token = result1.getToken();
            System.out.println("✓ 获取编辑权限，Token: " + token);

            // 执行一系列编辑操作，每次都刷新Token
            service.operate(objectId, "input_text", token, "user1");
            System.out.println("✓ 输入文本");

            service.operate(objectId, "format", token, "user1");
            System.out.println("✓ 格式化");

            service.operate(objectId, "save", token, "user1");
            System.out.println("✓ 保存");

            // 完成后退出
            service.operate(objectId, "exit", token, "user1");
            System.out.println("✓ 退出并释放锁");
        }
    }

    /**
     * 场景2: 冲突处理
     */
    private static void conflictHandling(ConflictControlService service) throws InterruptedException {
        String objectId = "document-002";

        // 用户A获取锁
        OperationResult resultA = service.operate(objectId, "edit", null, "user1");
        String tokenA = resultA.getToken();
        System.out.println("用户A获取锁: " + tokenA);

        // 用户B尝试编辑，进入等待队列
        OperationResult resultB = service.operate(objectId, "edit", null, "user2");
        if (!resultB.isAllowed()) {
            System.out.println("用户B进入等待队列，位置: " + resultB.getWaitPosition());
            System.out.println("原因: " + resultB.getReason());

            // 用户B需要轮询等待
            // 在实际应用中，可以设置定时任务进行轮询
            System.out.println("用户B开始轮询...");
        }

        // 用户A完成编辑
        Thread.sleep(1000);
        service.operate(objectId, "exit", tokenA, "user1");
        System.out.println("用户A退出");

        // 用户B再次尝试
        Thread.sleep(100); // 等待队列处理
        OperationResult resultB2 = service.operate(objectId, "edit", null, "user2");
        if (resultB2.isAllowed()) {
            System.out.println("用户B获取锁: " + resultB2.getToken());
        }
    }

    /**
     * 场景3: 读写混合
     */
    private static void readWriteMix(ConflictControlService service) {
        String objectId = "document-003";

        // 用户A写入
        OperationResult write = service.operate(objectId, "edit", null, "user1");
        System.out.println("用户A开始编辑: " + write.getToken());

        // 多个用户读取（不受写入影响）
        OperationResult read1 = service.operate(objectId, "read", null, "user1");
        OperationResult read2 = service.operate(objectId, "read", null, "user2");
        OperationResult read3 = service.operate(objectId, "read", null, "user3");

        System.out.println("用户B读取: " + (read1.isAllowed() ? "成功" : "失败"));
        System.out.println("用户C读取: " + (read2.isAllowed() ? "成功" : "失败"));
        System.out.println("用户D读取: " + (read3.isAllowed() ? "成功" : "失败"));

        // 读取完成后退出
        service.operate(objectId, "exit", read1.getToken(), "user1");
        service.operate(objectId, "exit", read2.getToken(), "user2");
        service.operate(objectId, "exit", read3.getToken(), "user3");
        System.out.println("所有读操作已完成");
    }

    /**
     * 客户端轮询示例
     */
    public static class ClientPollingExample {

        private final ConflictControlService service;
        private final String objectId;
        private final String action;
        private String token;

        public ClientPollingExample(ConflictControlService service, String objectId, String action) {
            this.service = service;
            this.objectId = objectId;
            this.action = action;
        }

        /**
         * 尝试获取锁，如果失败则轮询
         */
        public boolean acquireLockWithPolling(int maxRetries, long retryIntervalMs, String userId) throws InterruptedException {
            int retries = 0;

            while (retries < maxRetries) {
                OperationResult result = service.operate(objectId, action, null, userId);

                if (result.isAllowed()) {
                    this.token = result.getToken();
                    System.out.println("✓ 获取锁成功: " + token);
                    return true;
                } else if ("进入等待队列".equals(result.getReason())) {
                    System.out.println("⏳ 等待中，队列位置: " + result.getWaitPosition() +
                            ", 重试次数: " + (retries + 1));
                    Thread.sleep(retryIntervalMs);
                    retries++;
                } else {
                    System.out.println("✗ 获取锁失败: " + result.getReason());
                    return false;
                }
            }

            System.out.println("✗ 超过最大重试次数");
            return false;
        }

        /**
         * 执行操作
         */
        public boolean performAction(String actionName, String userId) {
            if (token == null) {
                System.out.println("✗ 未持有锁");
                return false;
            }

            OperationResult result = service.operate(objectId, actionName, token, userId);
            return result.isAllowed();
        }

        /**
         * 释放锁
         */
        public void release(String userId) {
            if (token != null) {
                service.operate(objectId, "exit", token, userId);
                System.out.println("✓ 锁已释放");
                token = null;
            }
        }
    }
}

