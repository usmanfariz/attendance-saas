package com.attendance.saas.specification;

import com.attendance.saas.entity.Employee;
import com.attendance.saas.entity.enums.EmployeeStatus;
import jakarta.persistence.criteria.JoinType;
import lombok.experimental.UtilityClass;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/**
 * Composable filters for the employee list endpoint.
 *
 * <p>{@link #ofCompany(Long)} is mandatory and always combined first, so no
 * filter combination can ever widen the query beyond one tenant.
 */
@UtilityClass
public class EmployeeSpecifications {

    public Specification<Employee> ofCompany(Long companyId) {
        return (root, query, cb) -> cb.equal(root.get("company").get("id"), companyId);
    }

    /** Matches employee code, name, email or phone. */
    public Specification<Employee> keywordMatches(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String pattern = "%" + keyword.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("employeeCode")), pattern),
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(cb.coalesce(root.get("email"), "")), pattern),
                cb.like(cb.lower(cb.coalesce(root.get("phone"), "")), pattern));
    }

    public Specification<Employee> hasDepartment(Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("department").get("id"), departmentId);
    }

    public Specification<Employee> hasPosition(Long positionId) {
        if (positionId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("position").get("id"), positionId);
    }

    public Specification<Employee> hasStatus(EmployeeStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /**
     * Pulls department and position in the same select, so rendering a page of
     * employees does not fire one query per row.
     */
    public Specification<Employee> withAssociationsFetched() {
        return (root, query, cb) -> {
            if (query != null && Long.class != query.getResultType()) {
                root.fetch("department", JoinType.LEFT);
                root.fetch("position", JoinType.LEFT);
                query.distinct(true);
            }
            return null;
        };
    }
}
