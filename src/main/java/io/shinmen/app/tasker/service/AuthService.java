package io.shinmen.app.tasker.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.shinmen.app.tasker.dto.AuthResponse;
import io.shinmen.app.tasker.dto.LoginRequest;
import io.shinmen.app.tasker.dto.PasswordChangeRequest;
import io.shinmen.app.tasker.dto.PasswordResetRequest;
import io.shinmen.app.tasker.dto.UserRegistrationRequest;
import io.shinmen.app.tasker.exception.CustomException;
import io.shinmen.app.tasker.model.PasswordHistory;
import io.shinmen.app.tasker.model.Role;
import io.shinmen.app.tasker.model.User;
import io.shinmen.app.tasker.repository.PasswordHistoryRepository;
import io.shinmen.app.tasker.repository.RoleRepository;
import io.shinmen.app.tasker.repository.UserRepository;
import io.shinmen.app.tasker.security.JwtTokenProvider;
import io.shinmen.app.tasker.security.UserPrincipal;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final AuditService auditService;

    @Value("${app.account.max-failed-attempts}")
    private int maxFailedAttempts;

    @Value("${app.account.lock-duration}")
    private long lockDurationMs;

    @Transactional
    public User registerUser(UserRegistrationRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new CustomException("Email is already registered", HttpStatus.BAD_REQUEST);
        }

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .emailVerified(false)
                .emailVerificationToken(generateToken())
                .emailVerificationTokenExpiryDate(LocalDateTime.now().plusHours(24))
                .enabled(true)
                .build();

        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new CustomException("Default role not found", HttpStatus.INTERNAL_SERVER_ERROR));
        user.setRoles(Set.of(userRole));

        user = userRepository.save(user);

        savePasswordHistory(user, user.getPassword());

        emailService.sendVerificationEmail(user);


        auditService.auditUserAction(user.getId(), "USER_REGISTRATION", "User registered successfully");

        return user;
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        try {
            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new CustomException("Invalid credentials", HttpStatus.UNAUTHORIZED));

            if (user.getAccountLockedUntil() != null && LocalDateTime.now().isBefore(user.getAccountLockedUntil())) {
                throw new LockedException("Account is locked until " + user.getAccountLockedUntil());
            }
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );

            if (user.getFailedLoginAttempts() > 0) {
                user.setFailedLoginAttempts(0);
                user.setAccountLockedUntil(null);
                userRepository.save(user);
            }


            SecurityContextHolder.getContext().setAuthentication(authentication);
            String accessToken = tokenProvider.generateAccessToken(authentication);
            String refreshToken = tokenProvider.generateRefreshToken(authentication);

            tokenService.saveRefreshToken(user.getUsername(), refreshToken);


            auditService.auditUserAction(user.getId(), "USER_LOGIN", "User logged in successfully");

            return AuthResponse.builder().accessToken(accessToken).refreshToken(refreshToken).build();

        } catch (LockedException e) {
            throw e;
        } catch (Exception e) {

            handleFailedLogin(request.getEmail());
            throw new CustomException("Invalid credentials", HttpStatus.UNAUTHORIZED);
        }
    }

    private void handleFailedLogin(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);

            if (user.getFailedLoginAttempts() >= maxFailedAttempts) {
                user.setAccountLockedUntil(LocalDateTime.now().plusNanos(lockDurationMs * 1000000));
                auditService.auditUserAction(user.getId(), "ACCOUNT_LOCKED",
                    "Account locked due to multiple failed login attempts");
            }

            userRepository.save(user);
        });
    }

    @Transactional
    public void changePassword(Long userId, PasswordChangeRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new CustomException("Current password is incorrect", HttpStatus.BAD_REQUEST);
        }

        String newPasswordHash = passwordEncoder.encode(request.getNewPassword());
        checkPasswordHistory(user, request.getNewPassword());

        user.setPassword(newPasswordHash);
        userRepository.save(user);

        savePasswordHistory(user, newPasswordHash);

        tokenService.invalidateTokens(userId);

        emailService.sendPasswordChangeNotification(user);

        auditService.auditUserAction(userId, "PASSWORD_CHANGE", "User changed password");
    }

    private void checkPasswordHistory(User user, String newPassword) {
        List<PasswordHistory> passwordHistories = passwordHistoryRepository
                .findTop3ByUserOrderByChangeDateDesc(user);

        for (PasswordHistory history : passwordHistories) {
            if (passwordEncoder.matches(newPassword, history.getPassword())) {
                throw new CustomException("Password has been used recently", HttpStatus.BAD_REQUEST);
            }
        }
    }

    private void savePasswordHistory(User user, String passwordHash) {
        PasswordHistory history = PasswordHistory.builder()
                .user(user)
                .password(passwordHash)
                .changeDate(LocalDateTime.now())
                .build();
        passwordHistoryRepository.save(history);
    }

    private String generateToken() {
        return UUID.randomUUID().toString();
    }

    @Transactional
    public AuthResponse refreshToken(String refreshToken) {
        if (!tokenProvider.validateToken(refreshToken)) {
            throw new CustomException("Invalid refresh token", HttpStatus.UNAUTHORIZED);
        }

        String username = tokenProvider.getUsernameFromToken(refreshToken);
        String storedRefreshToken = tokenService.getRefreshToken(username);

        if (storedRefreshToken == null || !storedRefreshToken.equals(refreshToken)) {
            throw new CustomException("Refresh token is invalid or expired", HttpStatus.UNAUTHORIZED);
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                UserPrincipal.create(user),
                null,
                user.getAuthorities()
        );

        String newAccessToken = tokenProvider.generateAccessToken(authentication);
        String newRefreshToken = tokenProvider.generateRefreshToken(authentication);
        tokenService.saveRefreshToken(username, newRefreshToken);

        return AuthResponse.builder().accessToken(newAccessToken).refreshToken(newRefreshToken).build();
    }

    @Transactional
    public void logout() {
        UserPrincipal currentUser = (UserPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        tokenService.invalidateTokens(currentUser.getId());
        SecurityContextHolder.clearContext();
    }

    @Transactional
    public void verifyEmail(String token) {
        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new CustomException("Invalid verification token", HttpStatus.BAD_REQUEST));

        if (user.isEmailVerified()) {
            throw new CustomException("Email already verified", HttpStatus.BAD_REQUEST);
        }

        if (user.getEmailVerificationTokenExpiryDate().isBefore(LocalDateTime.now())) {
            throw new CustomException("Verification token has expired", HttpStatus.BAD_REQUEST);
        }

        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        user.setEmailVerificationTokenExpiryDate(null);
        userRepository.save(user);

        auditService.auditUserAction(user.getId(), "EMAIL_VERIFIED", "Email verified successfully");
    }

    @Transactional
    public void resendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        if (user.isEmailVerified()) {
            throw new CustomException("Email already verified", HttpStatus.BAD_REQUEST);
        }

        user.setEmailVerificationToken(UUID.randomUUID().toString());
        user.setEmailVerificationTokenExpiryDate(LocalDateTime.now().plusHours(24));
        userRepository.save(user);

        emailService.sendVerificationEmail(user);
        auditService.auditUserAction(user.getId(), "VERIFICATION_EMAIL_RESENT",
            "Verification email resent");
    }

    @Transactional
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        user.setPasswordResetToken(UUID.randomUUID().toString());
        user.setPasswordResetTokenExpiryDate(LocalDateTime.now().plusHours(1));
        userRepository.save(user);

        emailService.sendPasswordResetEmail(user);
        auditService.auditUserAction(user.getId(), "PASSWORD_RESET_REQUESTED",
            "Password reset requested");
    }

    @Transactional
    public void resetPassword(PasswordResetRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new CustomException("Passwords do not match", HttpStatus.BAD_REQUEST);
        }

        User user = userRepository.findByPasswordResetToken(request.getToken())
                .orElseThrow(() -> new CustomException("Invalid reset token", HttpStatus.BAD_REQUEST));

        if (user.getPasswordResetTokenExpiryDate().isBefore(LocalDateTime.now())) {
            throw new CustomException("Reset token has expired", HttpStatus.BAD_REQUEST);
        }

        checkPasswordHistory(user, request.getNewPassword());

        String newPasswordHash = passwordEncoder.encode(request.getNewPassword());
        user.setPassword(newPasswordHash);
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiryDate(null);
        userRepository.save(user);

        savePasswordHistory(user, newPasswordHash);

        tokenService.invalidateTokens(user.getId());

        emailService.sendPasswordChangeNotification(user);

        auditService.auditUserAction(user.getId(), "PASSWORD_RESET_COMPLETED",
            "Password reset completed");
    }
}
