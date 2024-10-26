package io.shinmen.app.tasker.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.shinmen.app.tasker.dto.TeamLabelRequest;
import io.shinmen.app.tasker.dto.TeamLabelResponse;
import io.shinmen.app.tasker.exception.CustomException;
import io.shinmen.app.tasker.model.Team;
import io.shinmen.app.tasker.model.TeamLabel;
import io.shinmen.app.tasker.model.TeamPermission;
import io.shinmen.app.tasker.repository.TeamLabelRepository;
import io.shinmen.app.tasker.repository.TeamRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LabelService {

    private final TeamLabelRepository labelRepository;
    private final TeamRepository teamRepository;
    private final TaskService taskService;

    @Transactional
    public void initializeDefaultLabels(Team team) {
        List<TeamLabel> defaultLabels = List.of(
            TeamLabel.builder()
                .team(team)
                .name("Bug")
                .description("Issues that need to be fixed")
                .colorCode("#FF0000")
                .isDefault(true)
                .build(),
            TeamLabel.builder()
                .team(team)
                .name("Feature")
                .description("New functionality to be added")
                .colorCode("#00FF00")
                .isDefault(true)
                .build(),
            TeamLabel.builder()
                .team(team)
                .name("Enhancement")
                .description("Improvements to existing features")
                .colorCode("#0000FF")
                .isDefault(true)
                .build(),
            TeamLabel.builder()
                .team(team)
                .name("Documentation")
                .description("Documentation related tasks")
                .colorCode("#800080")
                .isDefault(true)
                .build(),
            TeamLabel.builder()
                .team(team)
                .name("High Priority")
                .description("Tasks that need immediate attention")
                .colorCode("#FF4500")
                .isDefault(true)
                .build()
        );

        labelRepository.saveAll(defaultLabels);
    }

    @Transactional
    public TeamLabelResponse createLabel(Long teamId, Long userId, TeamLabelRequest request) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new CustomException("Team not found", HttpStatus.NOT_FOUND));

        taskService.validateTeamPermission(teamId, userId, TeamPermission.MANAGE_LABELS);

        if (labelRepository.existsByTeamIdAndNameIgnoreCase(teamId, request.getName())) {
            throw new CustomException("Label name already exists in team", HttpStatus.BAD_REQUEST);
        }

        validateColorCode(request.getColorCode());

        TeamLabel label = TeamLabel.builder()
                .team(team)
                .name(request.getName())
                .description(request.getDescription())
                .colorCode(request.getColorCode())
                .isDefault(false)
                .build();

        label = labelRepository.save(label);

        return mapLabelToResponse(label);
    }

    @Transactional
    public TeamLabelResponse updateLabel(Long teamId, Long labelId, Long userId, TeamLabelRequest request) {
        TeamLabel label = labelRepository.findById(labelId)
                .orElseThrow(() -> new CustomException("Label not found", HttpStatus.NOT_FOUND));

        if (!label.getTeam().getId().equals(teamId)) {
            throw new CustomException("Label does not belong to team", HttpStatus.BAD_REQUEST);
        }

        taskService.validateTeamPermission(teamId, userId, TeamPermission.MANAGE_LABELS);

        if (label.isDefault()) {
            throw new CustomException("Cannot modify default labels", HttpStatus.BAD_REQUEST);
        }

        if (!label.getName().equalsIgnoreCase(request.getName()) &&
            labelRepository.existsByTeamIdAndNameIgnoreCase(teamId, request.getName())) {
            throw new CustomException("Label name already exists in team", HttpStatus.BAD_REQUEST);
        }

        validateColorCode(request.getColorCode());

        label.setName(request.getName());
        label.setDescription(request.getDescription());
        label.setColorCode(request.getColorCode());

        label = labelRepository.save(label);

        return mapLabelToResponse(label);
    }

    @Transactional
    public void deleteLabel(Long teamId, Long labelId, Long userId) {
        TeamLabel label = labelRepository.findById(labelId)
                .orElseThrow(() -> new CustomException("Label not found", HttpStatus.NOT_FOUND));

        if (!label.getTeam().getId().equals(teamId)) {
            throw new CustomException("Label does not belong to team", HttpStatus.BAD_REQUEST);
        }

        taskService.validateTeamPermission(teamId, userId, TeamPermission.MANAGE_LABELS);

        if (label.isDefault()) {
            throw new CustomException("Cannot delete default labels", HttpStatus.BAD_REQUEST);
        }

        labelRepository.delete(label);
    }

    @Transactional(readOnly = true)
    public List<TeamLabelResponse> getTeamLabels(Long teamId, Long userId) {
        taskService.validateTeamPermission(teamId, userId, TeamPermission.VIEW_TASK);

        List<TeamLabel> labels = labelRepository.findByTeamId(teamId);
        return labels.stream()
                .map(this::mapLabelToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public void validateAndNormalizeLabels(Long teamId, Set<String> labels) {
        if (labels == null || labels.isEmpty()) {
            return;
        }

        Set<String> validLabels = labelRepository.findByTeamId(teamId).stream()
                .map(TeamLabel::getName)
                .collect(Collectors.toSet());

        Set<String> invalidLabels = labels.stream()
                .filter(label -> !validLabels.contains(label))
                .collect(Collectors.toSet());

        if (!invalidLabels.isEmpty()) {
            throw new CustomException(
                "Invalid labels: " + String.join(", ", invalidLabels),
                HttpStatus.BAD_REQUEST
            );
        }
    }

    @Transactional(readOnly = true)
    public Set<String> getAvailableLabelColors(Long teamId) {
        return new HashSet<>(List.of(
            "#FF0000", // Red
            "#00FF00", // Green
            "#0000FF", // Blue
            "#FFFF00", // Yellow
            "#FF00FF", // Magenta
            "#00FFFF", // Cyan
            "#FFA500", // Orange
            "#800080", // Purple
            "#008000", // Dark Green
            "#FFC0CB", // Pink
            "#A52A2A", // Brown
            "#808080"  // Gray
        ));
    }

    private void validateColorCode(String colorCode) {
        if (colorCode == null || !colorCode.matches("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$")) {
            throw new CustomException(
                "Invalid color code format. Must be a valid hex color (e.g., #FF0000)",
                HttpStatus.BAD_REQUEST
            );
        }
    }

    private TeamLabelResponse mapLabelToResponse(TeamLabel label) {
        return TeamLabelResponse.builder()
                .id(label.getId())
                .name(label.getName())
                .description(label.getDescription())
                .colorCode(label.getColorCode())
                .isDefault(label.isDefault())
                .createdAt(label.getCreatedAt())
                .updatedAt(label.getUpdatedAt())
                .build();
    }
}
