package io.shinmen.app.tasker.model;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Document(collection = "user_audits")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAudit {

    @Id
    private String id;

    @Field("user_id")
    private Long userId;

    @Field("action")
    private String action;

    @Field("details")
    private Map<String, Object> details;

    @Field("ip_address")
    private String ipAddress;

    @Field("user_agent")
    private String userAgent;

    @Field("timestamp")
    private LocalDateTime timestamp;

    @Field("status")
    private String status;
}
