package model;

import lombok.Data;

/**
 * 信号机平台请求参数
 */
@Data
public class SignalPlatformRequest {
    private String deviceId;    // 设备ID，对应operatorId
    private String signalIP;    // 信号机IP，对应objectId
    private Integer mode;       // 模式，对应action (0,60,62,63为exit，其他为有效动作)
    private String lock;        // 锁Token，对应token
}
