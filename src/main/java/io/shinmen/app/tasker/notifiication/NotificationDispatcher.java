package io.shinmen.app.tasker.notifiication;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import io.shinmen.app.tasker.service.EmailService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationDispatcher {
    private final SimpMessagingTemplate messagingTemplate;
    private final EmailService emailService;

    public void sendToUser(Long userId, String destination, Object payload) {
        messagingTemplate.convertAndSendToUser(
            userId.toString(),
            destination,
            payload
        );
    }

    public void broadcastToTeam(Long teamId, String destination, Object payload) {
        messagingTemplate.convertAndSend(
            "/topic/team." + teamId + destination,
            payload
        );
    }
}
