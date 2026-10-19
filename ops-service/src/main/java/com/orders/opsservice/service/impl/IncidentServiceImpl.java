package com.orders.opsservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.orders.opsservice.dto.IncidentCreateDTO;
import com.orders.opsservice.dto.IncidentUpdateDTO;
import com.orders.opsservice.entity.Incident;
import com.orders.opsservice.exception.BusinessException;
import com.orders.opsservice.mapper.IncidentMapper;
import com.orders.opsservice.service.IncidentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IncidentServiceImpl implements IncidentService {

    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_CLOSED = "CLOSED";

    private final IncidentMapper incidentMapper;

    /**
     * 创建故障事件（外部/手动入口）。
     * <p>
     * eventNo 服务端生成；status 固定 OPEN（故障进行中）；
     * createdAt/updatedAt/firstFailedAt 不手动 set（MP 对 null 字段不拼 INSERT 列，数据库默认值生效）。
     */
    @Override
    public Incident create(IncidentCreateDTO incidentCreateDTO) {
        Incident incident = Incident.builder()
                .eventNo("INCIDENT_" + UUID.randomUUID())
                .serviceName(incidentCreateDTO.getServiceName())
                .serviceVersion(incidentCreateDTO.getServiceVersion())
                .eventType(incidentCreateDTO.getEventType())
                .severity(incidentCreateDTO.getSeverity() == null
                        ? "MEDIUM" : incidentCreateDTO.getSeverity())
                .description(incidentCreateDTO.getDescription())
                .status(STATUS_OPEN)
                .build();

        int rows = incidentMapper.insert(incident);
        if (rows != 1) {
            throw new BusinessException("创建故障事件失败");
        }
        return incident;
    }

    /**
     * 根据 ID 查询故障事件
     */
    @Override
    public Incident getById(Long id) {
        return incidentMapper.selectById(id);
    }

    /**
     * 查询所有故障事件（按 id 倒序，最新的在最前）
     * <p>
     * 运维查故障永远只看最新几条，selectList(null) 不带 ORDER BY
     * 会按主键升序返回，最旧的排在前面——顺序对运维工具是反的。
     */
    @Override
    public List<Incident> list() {
        return incidentMapper.selectList(new LambdaQueryWrapper<Incident>()
                .orderByDesc(Incident::getId));
    }

    /**
     * 更新故障事件（部分更新）
     * <p>
     * 先查再改：不存在直接抛业务异常；
     * MP 的 updateById 只更新实体中非 null 字段，正好配合部分更新语义；
     * updated_at 由数据库的 on update CURRENT_TIMESTAMP 自动刷新，
     * 所以更新后重新 select 一次，把 DB 刷新后的完整数据返回给调用方。
     */
    @Override
    public Incident update(Long id, IncidentUpdateDTO incidentUpdateDTO) {
        Incident incident = incidentMapper.selectById(id);
        if (incident == null) {
            throw new BusinessException("故障事件不存在: " + id);
        }

        if (incidentUpdateDTO.getSeverity() != null) {
            incident.setSeverity(incidentUpdateDTO.getSeverity());
        }
        if (incidentUpdateDTO.getStatus() != null) {
            incident.setStatus(incidentUpdateDTO.getStatus());
        }
        if (incidentUpdateDTO.getServiceVersion() != null) {
            incident.setServiceVersion(incidentUpdateDTO.getServiceVersion());
        }
        if (incidentUpdateDTO.getDescription() != null) {
            incident.setDescription(incidentUpdateDTO.getDescription());
        }

        incidentMapper.updateById(incident);
        return incidentMapper.selectById(id);
    }

    /**
     * 查找某服务某类型当前未关闭（OPEN）的故障事件，状态机聚合更新用。
     * <p>
     * 命中 (service_name, event_type, status) -> idx_service_status 索引，避免全表扫描。
     */
    @Override
    public Incident findActive(String serviceName, String eventType) {
        return incidentMapper.selectOne(new LambdaQueryWrapper<Incident>()
                .eq(Incident::getServiceName, serviceName)
                .eq(Incident::getEventType, eventType)
                .eq(Incident::getStatus, STATUS_OPEN)
                .orderByDesc(Incident::getId)
                .last("LIMIT 1"));
    }

    /**
     * 关闭某服务所有未关闭事件（恢复检测成功时调用），写入 resolved_at。
     *
     * @return 被关闭的事件条数
     */
    @Override
    public int closeOpenByService(String serviceName, LocalDateTime resolvedAt) {
        return incidentMapper.update(null, new UpdateWrapper<Incident>()
                .eq("service_name", serviceName)
                .eq("status", STATUS_OPEN)
                .set("status", STATUS_CLOSED)
                .set("resolved_at", resolvedAt));
    }

    /**
     * 故障状态机核心：记录一次失败。
     * <p>
     * 已存在 (serviceName, eventType, status=OPEN) 事件 -> fail_count + 1（聚合更新，不新增）；
     * 否则新建事件（status=OPEN, failCount=1, firstFailedAt=now）。
     */
    @Override
    public void recordFailure(String serviceName, String eventType,
                              String severity, String description) {
        Incident active = findActive(serviceName, eventType);
        if (active == null) {
            incidentMapper.insert(Incident.builder()
                    .eventNo("INCIDENT_" + UUID.randomUUID())
                    .serviceName(serviceName)
                    .eventType(eventType)
                    .severity(severity)
                    .description(description)
                    .status(STATUS_OPEN)
                    .failCount(1)
                    .firstFailedAt(LocalDateTime.now())
                    .build());
        } else {
            incidentMapper.update(null, new UpdateWrapper<Incident>()
                    .eq("id", active.getId())
                    .setSql("fail_count = fail_count + 1"));
        }
    }
}
