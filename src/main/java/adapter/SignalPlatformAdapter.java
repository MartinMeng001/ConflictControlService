package adapter;

import model.OperationResult;
import model.SignalPlatformRequest;
import model.SignalPlatformResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.ConflictControlService;
import service.impl.ConflictControlServiceImpl;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 信号机平台适配器
 * 提供单例模式访问，用于将信号机平台参数转换为冲突控制服务参数
 */
public class SignalPlatformAdapter {

    private static final Logger logger = LoggerFactory.getLogger(SignalPlatformAdapter.class);

    // 单例实例 - 使用双重检查锁
    private static volatile SignalPlatformAdapter instance;

    // 冲突控制服务
    private final ConflictControlService conflictControlService;

    // 定义退出操作的mode值集合
    private static final Set<Integer> EXIT_MODES = new HashSet<Integer>(
            Arrays.asList(0, 60, 62, 63)
    );

    /**
     * 私有构造函数
     */
    private SignalPlatformAdapter() {
        this.conflictControlService = new ConflictControlServiceImpl();
        logger.info("信号机平台适配器初始化完成");
    }

    /**
     * 获取单例实例
     *
     * @return SignalPlatformAdapter实例
     */
    public static SignalPlatformAdapter getInstance() {
        if (instance == null) {
            synchronized (SignalPlatformAdapter.class) {
                if (instance == null) {
                    instance = new SignalPlatformAdapter();
                }
            }
        }
        return instance;
    }

    /**
     * 处理信号机平台操作请求
     *
     * @param request 信号机平台请求参数
     * @return 信号机平台响应结果
     */
    public SignalPlatformResponse operate(SignalPlatformRequest request) {
        // 参数验证
        if (request == null) {
            logger.error("请求参数为空");
            return SignalPlatformResponse.fail("请求参数为空");
        }

        if (request.getDeviceId() == null || request.getSignalIP() == null || request.getMode() == null) {
            logger.error("必要参数缺失: deviceId={}, signalIP={}, mode={}",
                    request.getDeviceId(), request.getSignalIP(), request.getMode());
            return SignalPlatformResponse.fail("必要参数缺失");
        }

        // 参数转换
        String operatorId = request.getDeviceId();  // deviceId -> operatorId
        String objectId = request.getSignalIP();    // signalIP -> objectId
        String action = convertModeToAction(request.getMode());  // mode -> action
        String token = request.getLock();           // lock -> token

        logger.info("信号机平台请求转换: deviceId={} -> operatorId={}, signalIP={} -> objectId={}, mode={} -> action={}, lock={}",
                request.getDeviceId(), operatorId, request.getSignalIP(), objectId, request.getMode(), action, token);

        // 调用冲突控制服务
        OperationResult result = conflictControlService.operate(objectId, action, token, operatorId);

        // 结果转换
        SignalPlatformResponse response = convertToSignalPlatformResponse(result);

        logger.info("信号机平台响应: allowed={}, lock={}, reason={}, waitPosition={}",
                response.isAllowed(), response.getLock(), response.getReason(), response.getWaitPosition());

        return response;
    }

    /**
     * 将mode转换为action
     * mode为0,60,62,63时转换为"exit"
     * 其他值转换为"mode_" + mode值
     *
     * @param mode 模式值
     * @return action字符串
     */
    private String convertModeToAction(Integer mode) {
        if (EXIT_MODES.contains(mode)) {
            return "exit";
        }
        return "mode_" + mode;
    }

    /**
     * 将冲突控制服务的结果转换为信号机平台响应
     *
     * @param result 冲突控制服务的操作结果
     * @return 信号机平台响应
     */
    private SignalPlatformResponse convertToSignalPlatformResponse(OperationResult result) {
        return new SignalPlatformResponse(
                result.isAllowed(),
                result.getToken(),
                result.getReason(),
                result.getWaitPosition()
        );
    }

    /**
     * 获取内部的冲突控制服务（用于配置）
     *
     * @return ConflictControlService实例
     */
    public ConflictControlService getConflictControlService() {
        return conflictControlService;
    }
}
