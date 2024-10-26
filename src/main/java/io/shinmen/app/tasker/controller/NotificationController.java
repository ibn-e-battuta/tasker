package io.shinmen.app.tasker.controller;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import io.shinmen.app.tasker.security.UserPrincipal;
import io.shinmen.app.tasker.service.NotificationService;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @MessageMapping("/register")
    public void registerUser(@Payload String message,
            SimpMessageHeaderAccessor headerAccessor,
            Authentication authentication) {
        String sessionId = headerAccessor.getSessionId();
        UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();
        notificationService.registerUserSession(currentUser.getId(), sessionId);
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        UserPrincipal currentUser = (UserPrincipal) headerAccessor.getUser();
        if (currentUser != null) {
            notificationService.removeUserSession(currentUser.getId(), event.getSessionId());
        }
    }
}
