package io.shinmen.app.tasker.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.shinmen.app.tasker.dto.UserResponse;
import io.shinmen.app.tasker.dto.UserUpdateRequest;
import io.shinmen.app.tasker.exception.CustomException;
import io.shinmen.app.tasker.model.Role;
import io.shinmen.app.tasker.model.User;
import io.shinmen.app.tasker.repository.UserRepository;
import io.shinmen.app.tasker.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
        return UserPrincipal.create(user);
    }

    public UserDetails loadUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + id));
        return UserPrincipal.create(user);
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + id));
    }

    @Transactional
    public User updateUser(Long userId, UserUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());

        user = userRepository.save(user);

        auditService.auditUserAction(userId, "USER_UPDATE", "User profile updated");

        return user;
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
        auditService.auditUserAction(user.getId(), "EMAIL_VERIFIED", "User email verified");
    }

    @Transactional
    public void requestEmailVerification(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        if (user.isEmailVerified()) {
            throw new CustomException("Email already verified", HttpStatus.BAD_REQUEST);
        }

        user.setEmailVerificationToken(UUID.randomUUID().toString());
        user.setEmailVerificationTokenExpiryDate(LocalDateTime.now().plusHours(24));
        userRepository.save(user);

        emailService.sendVerificationEmail(user);

        auditService.auditUserAction(user.getId(), "VERIFICATION_REQUESTED",
            "User requested email verification");
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        if (isTeamOwner(user)) {
            throw new CustomException(
                "Cannot delete account while owning teams. Transfer ownership first.",
                HttpStatus.BAD_REQUEST
            );
        }

        anonymizeUser(user);

        tokenService.invalidateTokens(userId);

        auditService.auditUserAction(
            userId,
            "USER_DELETED",
            "User account anonymized and marked as deleted"
        );
    }

    private void anonymizeUser(User user) {
        String anonymousIdentifier = "DELETED_" + UUID.randomUUID().toString();

        user.setFirstName("Deleted");
        user.setLastName("User");
        user.setEmail(anonymousIdentifier + "@anonymous.com");
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setEmailVerified(false);
        user.setEnabled(false);
        user.setDeleted(true);
        user.setEmailVerificationToken(null);
        user.setEmailVerificationTokenExpiryDate(null);
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiryDate(null);
        user.setAccountLockedUntil(null);
        user.setFailedLoginAttempts(0);
        user.setLastModifiedBy("SYSTEM");
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.save(user);
    }

    private boolean isTeamOwner(User user) {
        return false;
    }

    @Transactional(readOnly = true)
    public UserResponse getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));
        return mapUserToResponse(user);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(String search, Pageable pageable) {
        Page<User> users = userRepository.findAllUsers(search, pageable);
        return users.map(this::mapUserToResponse);
    }

    @Transactional
    public void lockUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        if (user.getRoles().stream().anyMatch(role -> role.getName().equals("ROLE_ADMIN"))) {
            throw new CustomException("Cannot lock admin users", HttpStatus.BAD_REQUEST);
        }

        user.setAccountLockedUntil(LocalDateTime.now().plusDays(1));
        userRepository.save(user);
        tokenService.invalidateTokens(userId);

        emailService.sendAccountLockedEmail(user);

        auditService.auditUserAction(userId, "ACCOUNT_LOCKED", "Account locked by admin");
    }

    @Transactional
    public void unlockUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        user.setAccountLockedUntil(null);
        user.setFailedLoginAttempts(0);
        userRepository.save(user);

        auditService.auditUserAction(userId, "ACCOUNT_UNLOCKED", "Account unlocked by admin");
    }

    private UserResponse mapUserToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .roles(user.getRoles().stream()
                        .map(Role::getName)
                        .collect(Collectors.toSet()))
                .emailVerified(user.isEmailVerified())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
