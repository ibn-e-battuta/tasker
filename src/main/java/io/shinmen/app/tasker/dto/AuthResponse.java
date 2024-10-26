package io.shinmen.app.tasker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AuthResponse {
    private final String accessToken;
    private final String refreshToken;
    @Builder.Default
    private String tokenType = "Bearer";
}
