package com.orders.opsservice.service;

import com.orders.opsservice.dto.IncidentCreateDTO;
import com.orders.opsservice.dto.IncidentUpdateDTO;
import com.orders.opsservice.entity.Incident;

import java.time.LocalDateTime;
import java.util.List;

public interface IncidentService {

    /**
     * 创建故障事件
     * @param incidentCreateDTO 故障创建DTO
     * @return 创建成功的故障实体（含数据库回填的自增 id、eventNo）
     */
    Incident create(IncidentCreateDTO incidentCreateDTO);

    /**
     * 根据 ID 查询故障事件
     * @param id 故障事件ID
     * @return 故障事件实体，不存在时返回 null
     */
    Incident getById(Long id);

    /**
     * 查询所有故障事件
     * @return 所有故障事件列表
     */
    List<Incident> list();

    /**
     * 更新故障事件（部分更新，DTO 中为 null 的字段不修改）
     * @param id 故障事件ID
     * @param incidentUpdateDTO 更新DTO
     * @return 更新后的故障实体
     * @throws com.orders.opsservice.exception.BusinessException 事件不存在时抛出
     */
    Incident update(Long id, IncidentUpdateDTO incidentUpdateDTO);

    /**
     * 查找某服务某类型当前未关闭（OPEN）的故障事件，状态机聚合更新用。
     */
    Incident findActive(String serviceName, String eventType);

    /**
     * 关闭某服务所有未关闭事件（恢复检测成功时调用），写入 resolvedAt。
     * @return 被关闭的事件条数
     */
    int closeOpenByService(String serviceName, LocalDateTime resolvedAt);

    /**
     * 故障状态机核心：记录一次失败。
     * 已存在 (serviceName, eventType, status=OPEN) 事件 -> fail_count + 1；
     * 否则新建事件（status=OPEN, failCount=1, firstFailedAt=now）。
     */
    void recordFailure(String serviceName, String eventType, String severity, String description);
}
