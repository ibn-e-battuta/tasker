package io.shinmen.app.tasker.service;

import java.util.Set;

import io.shinmen.app.tasker.model.*;
import io.shinmen.app.tasker.repository.UserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.spring6.SpringTemplateEngine;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final UserRepository userRepository;

    @Value("${app.url}")
    private String appUrl;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Async
    public void sendVerificationEmail(User user) {
        Context context = new Context();
        context.setVariable("user", user);
        context.setVariable("verificationUrl",
            appUrl + "/verify-email?token=" + user.getEmailVerificationToken());

        String emailContent = templateEngine.process("email/verification", context);
        sendEmail(user.getEmail(), "Email Verification", emailContent);
    }

    @Async
    public void sendPasswordChangeNotification(User user) {
        Context context = new Context();
        context.setVariable("user", user);
        context.setVariable("loginUrl", appUrl + "/login");

        String emailContent = templateEngine.process("email/password-change", context);
        sendEmail(user.getEmail(), "Password Changed", emailContent);
    }

    @Async
    public void sendPasswordResetEmail(User user) {
        Context context = new Context();
        context.setVariable("user", user);
        context.setVariable("resetUrl",
            appUrl + "/reset-password?token=" + user.getPasswordResetToken());

        String emailContent = templateEngine.process("email/password-reset", context);
        sendEmail(user.getEmail(), "Password Reset Request", emailContent);
    }

    @Async
    public void sendAccountLockedEmail(User user) {
        Context context = new Context();
        context.setVariable("user", user);
        context.setVariable("unlockTime", user.getAccountLockedUntil());
        context.setVariable("resetPasswordUrl", appUrl + "/forgot-password");

        String emailContent = templateEngine.process("email/account-locked", context);
        sendEmail(user.getEmail(), "Account Locked", emailContent);
    }

    private void sendEmail(String to, String subject, String content) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true);

            mailSender.send(message);
            log.info("Email sent successfully to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send email to: {}", to, e);
        }
    }

    @Async
    public void sendTeamInvitation(TeamInvitation invitation, Set<TeamRole> roles) {
        Context context = new Context();
        context.setVariable("invitation", invitation);
        context.setVariable("roles", roles);
        context.setVariable("acceptUrl",
            appUrl + "/teams/invitations/" + invitation.getToken() + "/accept");
        context.setVariable("declineUrl",
            appUrl + "/teams/invitations/" + invitation.getToken() + "/decline");

        String emailContent = templateEngine.process("email/team-invitation", context);
        sendEmail(invitation.getEmail(),
            "Invitation to join team: " + invitation.getTeam().getName(),
            emailContent);
    }

    @Async
    public void sendTeamMembershipNotification(User user, Team team) {
        Context context = new Context();
        context.setVariable("user", user);
        context.setVariable("team", team);
        context.setVariable("teamUrl", appUrl + "/teams/" + team.getId());

        String emailContent = templateEngine.process("email/team-membership", context);
        sendEmail(user.getEmail(),
            "Welcome to team: " + team.getName(),
            emailContent);
    }

    @Async
    public void sendTeamOwnershipTransferNotification(User newOwner, Team team) {
        Context context = new Context();
        context.setVariable("user", newOwner);
        context.setVariable("team", team);
        context.setVariable("teamUrl", appUrl + "/teams/" + team.getId());

        String emailContent = templateEngine.process("email/team-ownership-transfer", context);
        sendEmail(newOwner.getEmail(),
            "Team Ownership Transfer: " + team.getName(),
            emailContent);
    }

    @Async
    public void sendTeamMemberRemovedNotification(User user, Team team) {
        Context context = new Context();
        context.setVariable("user", user);
        context.setVariable("team", team);

        String emailContent = templateEngine.process("email/team-member-removed", context);
        sendEmail(user.getEmail(),
            "Removed from team: " + team.getName(),
            emailContent);
    }

    @Async
    public void sendTaskAssignmentNotification(User user, Task task) {
        Context context = new Context();
        context.setVariable("user", user);
        context.setVariable("task", task);
        context.setVariable("taskUrl", appUrl + "/tasks/" + task.getId());

        String emailContent = templateEngine.process("email/task-assignment", context);
        sendEmail(user.getEmail(),
                "Task Assigned: " + task.getName(),
                emailContent);
    }

    @Async
    public void sendTaskUnassignmentNotification(User user, Task task) {
        Context context = new Context();
        context.setVariable("user", user);
        context.setVariable("task", task);
        context.setVariable("taskUrl", appUrl + "/tasks/" + task.getId());

        String emailContent = templateEngine.process("email/task-unassignment", context);
        sendEmail(user.getEmail(),
                "Task Unassigned: " + task.getName(),
                emailContent);
    }

    @Async
    public void sendTaskUpdateNotification(User user, Task task, String action) {
        Context context = new Context();
        context.setVariable("user", user);
        context.setVariable("task", task);
        context.setVariable("action", action);
        context.setVariable("taskUrl", appUrl + "/tasks/" + task.getId());

        String emailContent = templateEngine.process("email/task-update", context);
        sendEmail(user.getEmail(),
                "Task Updated: " + task.getName(),
                emailContent);
    }

    @Async
    public void sendTaskCommentNotification(User user, Task task, TaskComment comment) {
        Context context = new Context();
        context.setVariable("user", user);
        context.setVariable("task", task);
        context.setVariable("comment", comment);
        context.setVariable("taskUrl", appUrl + "/tasks/" + task.getId());

        String emailContent = templateEngine.process("email/task-comment", context);
        sendEmail(user.getEmail(),
                "New Comment on Task: " + task.getName(),
                emailContent);
    }

    @Async
    public void sendTaskCommentUpdateNotification(User user, Task task, TaskComment comment) {
        Context context = new Context();
        context.setVariable("user", user);
        context.setVariable("task", task);
        context.setVariable("comment", comment);
        context.setVariable("taskUrl", appUrl + "/tasks/" + task.getId());

        String emailContent = templateEngine.process("email/task-comment-update", context);
        sendEmail(user.getEmail(),
                "Comment Updated on Task: " + task.getName(),
                emailContent);
    }

    @Async
    public void sendTaskCommentDeletionNotification(User user, Task task, TaskComment comment) {
        Context context = new Context();
        context.setVariable("user", user);
        context.setVariable("task", task);
        context.setVariable("comment", comment);
        context.setVariable("taskUrl", appUrl + "/tasks/" + task.getId());

        String emailContent = templateEngine.process("email/task-comment-deletion", context);
        sendEmail(user.getEmail(),
                "Comment Deleted on Task: " + task.getName(),
                emailContent);
    }

    @Async
    public void sendTaskAttachmentNotification(User user, Task task, TaskAttachment attachment) {
        Context context = new Context();
        context.setVariable("user", user);
        context.setVariable("task", task);
        context.setVariable("attachment", attachment);
        context.setVariable("taskUrl", appUrl + "/tasks/" + task.getId());

        String emailContent = templateEngine.process("email/task-attachment", context);
        sendEmail(user.getEmail(),
                "New Attachment on Task: " + task.getName(),
                emailContent);
    }

    @Async
    public void sendTaskAttachmentDeletionNotification(User user, Task task, TaskAttachment attachment) {
        Context context = new Context();
        context.setVariable("user", user);
        context.setVariable("task", task);
        context.setVariable("attachment", attachment);
        context.setVariable("taskUrl", appUrl + "/tasks/" + task.getId());

        String emailContent = templateEngine.process("email/task-attachment-deletion", context);
        sendEmail(user.getEmail(),
                "Attachment Deleted from Task: " + task.getName(),
                emailContent);
    }


    @Async
    public void sendWatcherAddedNotification(User watcher, Task task, Long actorUserId) {
        Context context = new Context();
        context.setVariable("watcher", watcher);
        context.setVariable("task", task);
        context.setVariable("actor", userRepository.findById(actorUserId));
        context.setVariable("taskUrl", appUrl + "/tasks/" + task.getId());

        String emailContent = templateEngine.process("email/watcher-added", context);
        sendEmail(watcher.getEmail(), "You are now watching task: " + task.getName(), emailContent);
    }

    @Async
    public void sendTaskDeletedNotification(User user, Task task) {
        Context context = new Context();
        context.setVariable("user", user);
        context.setVariable("task", task);

        String emailContent = templateEngine.process("email/task-deleted", context);
        sendEmail(user.getEmail(), "Task Deleted: " + task.getName(), emailContent);
    }

    @Async
    public void sendMentionNotification(User user, Task task, TaskComment comment) {
        Context context = new Context();
        context.setVariable("user", user);
        context.setVariable("task", task);
        context.setVariable("comment", comment);
        context.setVariable("taskUrl", appUrl + "/tasks/" + task.getId());

        String emailContent = templateEngine.process("email/mention-notification", context);
        sendEmail(user.getEmail(),
            "You were mentioned in task: " + task.getName(),
            emailContent);
    }
}
