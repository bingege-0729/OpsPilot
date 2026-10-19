package com.orders.opsservice.resilience;

import com.orders.opsservice.service.IncidentService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * 远程调用守门员：包住每一次 Feign 调用，失败时把故障聚合进状态机
 * （同一个服务 + 类型的故障持续失败时只更新同一条事件，而不是每条记一笔），
 * 然后把异常原样抛出去。
 * <p>
 * 设计要点：
 * 1. 只记录、不吞异常 —— 响应格式由 GlobalExceptionHandler 负责，这里管"故障留痕"。
 * 2. 记录动作自带 try-catch —— 记库失败绝不能影响主流程。
 * 3. description 带 traceId —— 数据库记录与日志可互相定位。
 * 4. 状态机聚合逻辑下沉到 IncidentService.recordFailure，本类只负责拼描述 + 编排。
 */
@Component
@Slf4j
public class RemoteCallRecorder {

    public static final String EVENT_TYPE_TIMEOUT = "REMOTE_CALL_TIMEOUT";
    public static final String EVENT_TYPE_ERROR = "REMOTE_CALL_ERROR";
    public static final String EVENT_TYPE_CIRCUIT_OPEN = "CIRCUIT_OPEN";

    private final IncidentService incidentService;

    public RemoteCallRecorder(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    /**
     * 执行一次远程调用，失败则记录故障事件后原样抛出
     *
     * @param targetService 被调用方服务名
     * @param action        远程调用动作
     */
    public <T> T call(String targetService, Supplier<T> action) {
        try {
            return action.get();
        } catch (Exception e) {
            recordFailure(targetService, e);
            throw e;
        }
    }

    private void recordFailure(String targetService, Exception e) {
        try {
            String eventType = isTimeout(e) ? EVENT_TYPE_TIMEOUT : EVENT_TYPE_ERROR;
            String traceId = MDC.get("traceId");
            String description = String.format(
                    "%s 远程调用失败：%s。已按 500 返回调用方，traceId=%s",
                    targetService, e.getMessage(), traceId);
            incidentService.recordFailure(targetService, eventType, "HIGH", description);
            log.warn("故障已记录 service={} type={} traceId={}", targetService, eventType, traceId);
        } catch (Exception ex) {
            log.error("故障事件记录失败，不影响主流程", ex);
        }
    }

    /**
     * 熔断器已开门：请求被快速拒绝，没有真的发出去。
     * 与 REMOTE_CALL_TIMEOUT 区分开——那是"调用失败"，这是"熔断保护"。
     * 同样走状态机聚合：同一服务的熔断事件只保留一条并累加开门次数。
     */
    public void recordCircuitOpen(String targetService) {
        try {
            String traceId = MDC.get("traceId");
            String description = String.format(
                    "%s 熔断器已开启，请求被快速拒绝（降级），未发起真实调用。traceId=%s",
                    targetService, traceId);
            incidentService.recordFailure(targetService, EVENT_TYPE_CIRCUIT_OPEN, "MEDIUM", description);
            log.warn("熔断降级已记录 service={} traceId={}", targetService, traceId);
        } catch (Exception ex) {
            log.error("熔断事件记录失败，不影响主流程", ex);
        }
    }

    /**
     * 判断是否为超时类故障：顺着 cause 链找 SocketTimeoutException / TimeoutException
     */
    private boolean isTimeout(Throwable t) {
        Throwable current = t;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                    || current instanceof TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
