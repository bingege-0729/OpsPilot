package com.orders.opsservice.scheduler;

import com.orders.opsservice.service.DemoCheckService;
import com.orders.opsservice.service.IncidentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 定时健康检查：不依赖人工请求，持续探测 demo-service 可用性。
 * <p>
 * 成功 -> 说明 demo 已恢复，关闭其所有未关闭事件（状态机：OPEN -> CLOSED）。
 * 失败 -> 异常/超时已被 DemoCheckService + RemoteCallRecorder 内部记录为事件，
 *         这里只做健康检查层日志，不重复落库，避免双重记录。
 * <p>
 * 间隔由 ops.check.interval-ms 配置（默认 10000ms）；
 * fixedDelay 保证上一次执行完成后才开始计时间隔，避免任务堆叠。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HealthCheckScheduler {

    private static final String DEMO_SERVICE = "demo-service";

    private final DemoCheckService demoCheckService;
    private final IncidentService incidentService;

    @Scheduled(fixedDelayString = "${ops.check.interval-ms:10000}")
    public void checkDemo() {
        try {
            demoCheckService.check();
            int closed = incidentService.closeOpenByService(DEMO_SERVICE, LocalDateTime.now());
            if (closed > 0) {
                log.info("demo-service 健康检查恢复，关闭 {} 条未关闭事件", closed);
            }
        } catch (Exception e) {
            log.warn("demo-service 健康检查失败：{}", e.getMessage());
        }
    }
}
