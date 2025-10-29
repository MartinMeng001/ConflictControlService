# 信号机平台适配器使用说明

## 概述

`SignalPlatformAdapter` 是一个中间兼容性转换服务，用于将信号机平台的参数转换为冲突控制服务的参数格式。该适配器采用单例模式设计，确保全局只有一个实例。

## 参数映射关系

| 信号机平台参数 | 冲突控制服务参数 | 说明 |
|--------------|----------------|------|
| `deviceId` | `operatorId` | 设备ID，标识操作者 |
| `signalIP` | `objectId` | 信号机IP地址，标识被操作对象 |
| `mode` | `action` | 操作模式，需要转换（见下表） |
| `lock` | `token` | 锁Token |

## Mode 转换规则

| Mode 值 | Action 值 | 说明 |
|---------|-----------|------|
| 0 | "exit" | 退出操作 |
| 60 | "exit" | 退出操作 |
| 62 | "exit" | 退出操作 |
| 63 | "exit" | 退出操作 |
| 其他整数值 | "mode_" + 值 | 有效控制动作，如 mode=10 → "mode_10" |

## 使用方法

### 1. 获取适配器实例

```java
SignalPlatformAdapter adapter = SignalPlatformAdapter.getInstance();
```

### 2. 构建请求参数

```java
SignalPlatformRequest request = new SignalPlatformRequest();
request.setDeviceId("device001");         // 设备ID
request.setSignalIP("192.168.1.100");     // 信号机IP
request.setMode(10);                      // 控制模式
request.setLock(null);                    // 首次操作为null
```

### 3. 调用适配器

```java
SignalPlatformResponse response = adapter.operate(request);
```

### 4. 处理响应结果

```java
if (response.isAllowed()) {
    // 操作允许
    String lock = response.getLock();  // 获取锁Token
    System.out.println("成功获取锁: " + lock);
} else {
    // 操作不允许
    String reason = response.getReason();
    Integer position = response.getWaitPosition();

    if (position != null) {
        System.out.println("进入等待队列，位置: " + position);
    } else {
        System.out.println("操作失败: " + reason);
    }
}
```

## 完整使用示例

### 场景1：首次控制信号机

```java
// 1. 获取适配器实例
SignalPlatformAdapter adapter = SignalPlatformAdapter.getInstance();

// 2. 首次控制请求
SignalPlatformRequest request = new SignalPlatformRequest();
request.setDeviceId("deviceA");
request.setSignalIP("192.168.1.100");
request.setMode(10);
request.setLock(null);

SignalPlatformResponse response = adapter.operate(request);

if (response.isAllowed()) {
    String lock = response.getLock();
    System.out.println("成功获取锁: " + lock);
    // 保存lock，用于后续操作
}
```

### 场景2：刷新操作（更改控制模式）

```java
// 使用之前获取的lock继续操作
SignalPlatformRequest request = new SignalPlatformRequest();
request.setDeviceId("deviceA");
request.setSignalIP("192.168.1.100");
request.setMode(15);  // 切换到新的控制模式
request.setLock(previousLock);  // 使用之前获取的lock

SignalPlatformResponse response = adapter.operate(request);

if (response.isAllowed()) {
    System.out.println("模式切换成功");
}
```

### 场景3：退出控制

```java
// 使用退出模式（0, 60, 62, 63任意一个）
SignalPlatformRequest request = new SignalPlatformRequest();
request.setDeviceId("deviceA");
request.setSignalIP("192.168.1.100");
request.setMode(0);  // 退出模式
request.setLock(previousLock);

SignalPlatformResponse response = adapter.operate(request);

if (response.isAllowed()) {
    System.out.println("退出成功，锁已释放");
}
```

### 场景4：等待队列

```java
// 当信号机已被其他设备控制时
SignalPlatformRequest request = new SignalPlatformRequest();
request.setDeviceId("deviceB");
request.setSignalIP("192.168.1.100");  // 同一个信号机
request.setMode(20);
request.setLock(null);

SignalPlatformResponse response = adapter.operate(request);

if (!response.isAllowed() && response.getWaitPosition() != null) {
    System.out.println("进入等待队列，位置: " + response.getWaitPosition());
    // 需要轮询或等待，直到能够认领锁
}
```

### 场景5：认领锁

```java
// 当前面的设备退出后，等待队列中的设备可以认领锁
SignalPlatformRequest request = new SignalPlatformRequest();
request.setDeviceId("deviceB");  // 之前在等待队列的设备
request.setSignalIP("192.168.1.100");
request.setMode(20);
request.setLock(null);  // 认领时lock为null

SignalPlatformResponse response = adapter.operate(request);

if (response.isAllowed()) {
    String lock = response.getLock();
    System.out.println("成功认领锁: " + lock);
}
```

## 可选配置

可以通过适配器访问底层冲突控制服务进行配置：

```java
SignalPlatformAdapter adapter = SignalPlatformAdapter.getInstance();

// 配置参数
adapter.getConflictControlService().setConfiguration(
    5,      // 最大队列容量
    30000,  // 锁最大持有时间(ms) - 30秒
    300000  // 最大等待时间(ms) - 5分钟
);

// 设置优先策略
adapter.getConflictControlService().setPriorityStrategy(
    PriorityStrategy.FIFO  // 先进先出
    // 或 PriorityStrategy.SAME_ACTION_FIRST  // 相同动作优先
);
```

## 响应结果说明

`SignalPlatformResponse` 包含以下字段：

| 字段 | 类型 | 说明 |
|-----|------|------|
| `allowed` | boolean | 是否允许操作 |
| `lock` | String | 锁Token（成功时返回） |
| `reason` | String | 失败原因 |
| `waitPosition` | Integer | 等待队列位置（进入队列时返回） |

## 运行示例代码

项目中提供了完整的使用示例：

```bash
# 运行使用示例
java -cp target/ConflictControlService-1.0.0.jar test.SignalPlatformUsageExample

# 运行单元测试
mvn test -Dtest=SignalPlatformAdapterTest
```

## 注意事项

1. **单例模式**：`SignalPlatformAdapter` 采用单例模式，全局只有一个实例
2. **Lock保存**：获取的lock需要客户端保存，用于后续刷新和退出操作
3. **轮询机制**：进入等待队列的设备需要定期轮询，检查是否可以认领锁
4. **退出模式**：支持4个退出模式值：0, 60, 62, 63
5. **线程安全**：适配器内部使用线程安全的冲突控制服务，支持多线程并发访问

## 类文件位置

- **适配器类**: `src/main/java/adapter/SignalPlatformAdapter.java`
- **请求模型**: `src/main/java/model/SignalPlatformRequest.java`
- **响应模型**: `src/main/java/model/SignalPlatformResponse.java`
- **使用示例**: `src/main/java/test/SignalPlatformUsageExample.java`
- **单元测试**: `src/test/java/SignalPlatformAdapterTest.java`
