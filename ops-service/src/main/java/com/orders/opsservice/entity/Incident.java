package com.orders.opsservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("ops_incident")
public class Incident {
    @TableId(type = IdType.AUTO)
    private Long id;                    // 主键ID
    private String eventNo;             // 故障事件编号
    private String serviceName;         // 发生故障的服务名称
    private String serviceVersion;      // 发生故障的服务版本
    private String eventType;           // 故障类型
    private String severity;            // 故障严重程度
    private String description;         // 故障描述
    private String status;              // 故障状态：OPEN（故障中）/ CLOSED（已恢复）
    private Integer failCount;          // 连续失败次数（持续失败每次 +1）
    private LocalDateTime firstFailedAt; // 首次失败时间（仅创建时写入，不随后续失败刷新）
    private LocalDateTime resolvedAt;   // 恢复关闭时间
    private LocalDateTime createdAt;    // 创建时间
    private LocalDateTime updatedAt;    // 更新时间
}
