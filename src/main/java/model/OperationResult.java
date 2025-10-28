package model;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 操作结果
 */
@Data
@AllArgsConstructor
public class OperationResult {
    private boolean allowed;           // 是否允许操作
    private String token;              // 分配的Token（仅allowed=true时有值）
    private String reason;             // 不允许的原因
    private Integer waitPosition;      // 等待队列位置（如进入队列）

    public static OperationResult success(String token) {
        return new OperationResult(true, token, null, null);
    }

    public static OperationResult fail(String reason) {
        return new OperationResult(false, null, reason, null);
    }

    public static OperationResult waiting(int position) {
        return new OperationResult(false, null, "进入等待队列", position);
    }
}
