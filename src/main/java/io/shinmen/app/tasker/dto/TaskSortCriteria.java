package io.shinmen.app.tasker.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaskSortCriteria {
    private String field;
    private SortDirection direction;
    private int order;

    public enum SortDirection {
        ASC, DESC
    }

    public static class Fields {
        public static final String NAME = "name";
        public static final String STATUS = "status";
        public static final String PRIORITY = "priority";
        public static final String DUE_DATE = "dueDate";
        public static final String CREATED_AT = "createdAt";
        public static final String UPDATED_AT = "updatedAt";
        public static final String ASSIGNED_USER = "assignedUser";
        public static final String ESTIMATED_EFFORT = "estimatedEffort";
    }
}
