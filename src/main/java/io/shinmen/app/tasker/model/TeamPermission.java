package io.shinmen.app.tasker.model;

import lombok.Getter;

@Getter
public enum TeamPermission {

    CREATE_TASK("Create new tasks"),
    VIEW_TASK("View tasks"),
    EDIT_TASK("Edit tasks"),
    DELETE_TASK("Delete tasks"),
    ASSIGN_TASK("Assign tasks to team members"),
    CHANGE_TASK_STATUS("Change task status"),

    ADD_COMMENT("Add comments to tasks"),
    EDIT_COMMENT("Edit own comments"),
    DELETE_COMMENT("Delete comments"),
    VIEW_COMMENTS("View task comments"),

    ADD_ATTACHMENT("Add attachments to tasks"),
    DELETE_ATTACHMENT("Delete attachments"),
    VIEW_ATTACHMENTS("View task attachments"),

    ADD_WATCHER("Add watchers to tasks"),
    REMOVE_WATCHER("Remove watchers from tasks"),

    MANAGE_LABELS("Manage task labels"),

    VIEW_MEMBERS("View team members"),
    MANAGE_MEMBERS("Manage team members"),
    MANAGE_MEMBER_ROLES("Manage team member roles"),

    VIEW_TEAM_SETTINGS("View team settings"),
    EDIT_TEAM_SETTINGS("Edit team settings"),
    MANAGE_CUSTOM_FIELDS("Manage custom fields"),
    MANAGE_WORKFLOW("Manage workflow and status transitions"),

    MANAGE_WATCHERS("Manage task watchers");

    private final String description;

TeamPermission(String description) {
        this.description = description;
    }
}
