package io.shinmen.app.tasker.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import io.shinmen.app.tasker.model.UserAudit;
import io.shinmen.app.tasker.repository.mongo.UserAuditRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final UserAuditRepository userAuditRepository;

    @Async
    public void auditUserAction(Long userId, String action, String status) {
        auditUserAction(userId, action, status, null);
    }

    @Async
    public void auditUserAction(Long userId, String action, String status, Map<String, Object> details) {
        HttpServletRequest request = getCurrentRequest();

        UserAudit audit = UserAudit.builder()
                .userId(userId)
                .action(action)
                .status(status)
                .details(details != null ? details : new HashMap<>())
                .ipAddress(getClientIp(request))
                .userAgent(request != null ? request.getHeader("User-Agent") : "System")
                .timestamp(LocalDateTime.now())
                .build();

        userAuditRepository.save(audit);
    }

    private HttpServletRequest getCurrentRequest() {
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ServletRequestAttributes.class::isInstance)
                .map(ServletRequestAttributes.class::cast)
                .map(ServletRequestAttributes::getRequest)
                .orElse(null);
    }

    private String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "0.0.0.0";
        }

        String[] IP_HEADERS = {
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA",
            "REMOTE_ADDR"
        };

        for (String header : IP_HEADERS) {
            String value = request.getHeader(header);
            if (value != null && value.length() != 0 && !"unknown".equalsIgnoreCase(value)) {
                return value.split(",")[0];
            }
        }

        return request.getRemoteAddr();
    }
}
