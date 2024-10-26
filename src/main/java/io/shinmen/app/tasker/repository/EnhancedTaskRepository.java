package io.shinmen.app.tasker.repository;

import io.shinmen.app.tasker.dto.TaskFilter;
import io.shinmen.app.tasker.dto.TaskSortCriteria;
import io.shinmen.app.tasker.model.Task;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class EnhancedTaskRepository {

    private final EntityManager entityManager;

    public Page<Task> findTasksWithFilters(Long teamId, TaskFilter filter,
                                           List<TaskSortCriteria> sortCriteria, Pageable pageable) {

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Task> query = cb.createQuery(Task.class);
        Root<Task> task = query.from(Task.class);

        Join<Object, Object> team = task.join("team");
        Join<Object, Object> assignedUser = task.join("assignedUser", JoinType.LEFT);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(team.get("id"), teamId));
        predicates.add(cb.equal(task.get("deleted"), false));

        applyFilters(filter, cb, task, predicates);

        query.where(cb.and(predicates.toArray(new Predicate[0])));

        if (sortCriteria != null && !sortCriteria.isEmpty()) {
            List<Order> orders = new ArrayList<>();
            for (TaskSortCriteria sort : sortCriteria) {
                orders.add(createOrder(sort, cb, task, assignedUser));
            }
            query.orderBy(orders);
        }

        List<Task> tasks = entityManager.createQuery(query)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Task> countRoot = countQuery.from(Task.class);
        countQuery.select(cb.count(countRoot))
                .where(predicates.toArray(new Predicate[0]));
        Long total = entityManager.createQuery(countQuery).getSingleResult();

        return new PageImpl<>(tasks, pageable, total);
    }

    private void applyFilters(TaskFilter filter, CriteriaBuilder cb,
                              Root<Task> task, List<Predicate> predicates) {

        if (filter.getStatuses() != null && !filter.getStatuses().isEmpty()) {
            predicates.add(task.get("status").in(filter.getStatuses()));
        }

        if (filter.getPriorities() != null && !filter.getPriorities().isEmpty()) {
            predicates.add(task.get("priority").in(filter.getPriorities()));
        }

        if (filter.getAssignedUserIds() != null && !filter.getAssignedUserIds().isEmpty()) {
            predicates.add(task.get("assignedUser").get("id").in(filter.getAssignedUserIds()));
        }

        if (filter.getLabels() != null && !filter.getLabels().isEmpty()) {
            filter.getLabels().forEach(label ->
                    predicates.add(cb.isMember(label, task.get("labels")))
            );
        }

        if (filter.getDueDateStart() != null) {
            predicates.add(cb.greaterThanOrEqualTo(
                    task.get("dueDate"),
                    filter.getDueDateStart()
            ));
        }

        if (filter.getDueDateEnd() != null) {
            predicates.add(cb.lessThanOrEqualTo(
                    task.get("dueDate"),
                    filter.getDueDateEnd()
            ));
        }

        if (filter.getMinEstimatedEffort() != null) {
            predicates.add(cb.greaterThanOrEqualTo(
                    task.get("estimatedEffort"),
                    filter.getMinEstimatedEffort()
            ));
        }

        if (filter.getMaxEstimatedEffort() != null) {
            predicates.add(cb.lessThanOrEqualTo(
                    task.get("estimatedEffort"),
                    filter.getMaxEstimatedEffort()
            ));
        }

        if (filter.getIsLocked() != null) {
            predicates.add(cb.equal(task.get("locked"), filter.getIsLocked()));
        }

        if (filter.getIsFinalStatus() != null) {
            predicates.add(cb.equal(task.get("finalStatus"), filter.getIsFinalStatus()));
        }

        if (filter.getCreatedAfter() != null) {
            predicates.add(cb.greaterThanOrEqualTo(
                    task.get("createdAt"),
                    filter.getCreatedAfter()
            ));
        }

        if (filter.getCreatedBefore() != null) {
            predicates.add(cb.lessThanOrEqualTo(
                    task.get("createdAt"),
                    filter.getCreatedBefore()
            ));
        }

        if (filter.getSearchTerm() != null && !filter.getSearchTerm().trim().isEmpty()) {
            String searchTerm = "%" + filter.getSearchTerm().toLowerCase() + "%";
            predicates.add(cb.or(
                    cb.like(cb.lower(task.get("name")), searchTerm),
                    cb.like(cb.lower(task.get("description")), searchTerm)
            ));
        }
    }

    private Order createOrder(TaskSortCriteria sort, CriteriaBuilder cb,
                              Root<Task> task, Join<Object, Object> assignedUser) {

        Expression<?> sortExpression;

        switch (sort.getField()) {
            case TaskSortCriteria.Fields.NAME:
                sortExpression = task.get("name");
                break;
            case TaskSortCriteria.Fields.STATUS:
                sortExpression = task.get("status");
                break;
            case TaskSortCriteria.Fields.PRIORITY:
                sortExpression = task.get("priority");
                break;
            case TaskSortCriteria.Fields.DUE_DATE:
                sortExpression = task.get("dueDate");
                break;
            case TaskSortCriteria.Fields.ASSIGNED_USER:
                sortExpression = assignedUser.get("firstName");
                break;
            case TaskSortCriteria.Fields.ESTIMATED_EFFORT:
                sortExpression = task.get("estimatedEffort");
                break;
            case TaskSortCriteria.Fields.CREATED_AT:
                sortExpression = task.get("createdAt");
                break;
            case TaskSortCriteria.Fields.UPDATED_AT:
                sortExpression = task.get("updatedAt");
                break;
            default:
                sortExpression = task.get("createdAt");
        }

        return sort.getDirection() == TaskSortCriteria.SortDirection.ASC ?
                cb.asc(sortExpression) : cb.desc(sortExpression);
    }
}
