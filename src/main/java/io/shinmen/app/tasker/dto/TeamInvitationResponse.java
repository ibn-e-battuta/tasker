package io.shinmen.app.tasker.dto;

import io.shinmen.app.tasker.model.TeamInvitation.InvitationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;


@Data
@Builder
public class TeamInvitationResponse {
    private Long id;
    private String email;
    private TeamResponse team;
    private InvitationStatus status;
    private LocalDateTime expiryDate;
    private LocalDateTime createdAt;
}
