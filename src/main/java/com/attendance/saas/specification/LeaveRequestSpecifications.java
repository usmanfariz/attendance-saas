package com.attendance.saas.specification;

import com.attendance.saas.entity.LeaveRequest;
import com.attendance.saas.entity.enums.LeaveType;
import com.attendance.saas.entity.enums.RequestStatus;
import jakarta.persistence.criteria.JoinType;
import lombok.experimental.UtilityClass;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

@UtilityClass
public class LeaveRequestSpecifications {

    public Specification<LeaveRequest> ofCompany(Long companyId) {
        return (root, query, cb) -> cb.equal(root.get("company").get("id"), companyId);
    }

    public Specification<LeaveRequest> ofEmployee(Long employeeId) {
        if (employeeId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("employee").get("id"), employeeId);
    }

    public Specification<LeaveRequest> ofDepartment(Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        return (root, query, cb) ->
                cb.equal(root.get("employee").get("department").get("id"), departmentId);
    }

    public Specification<LeaveRequest> hasStatus(RequestStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public Specification<LeaveRequest> hasType(LeaveType leaveType) {
        if (leaveType == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("leaveType"), leaveType);
    }

    /** Requests overlapping the given range, not merely contained by it. */
    public Specification<LeaveRequest> overlapping(LocalDate startDate, LocalDate endDate) {
        return (root, query, cb) -> {
            if (startDate == null && endDate == null) {
                return null;
            }
            if (startDate == null) {
                return cb.lessThanOrEqualTo(root.get("startDate"), endDate);
            }
            if (endDate == null) {
                return cb.greaterThanOrEqualTo(root.get("endDate"), startDate);
            }
            return cb.and(
                    cb.lessThanOrEqualTo(root.get("startDate"), endDate),
                    cb.greaterThanOrEqualTo(root.get("endDate"), startDate));
        };
    }

    public Specification<LeaveRequest> withAssociationsFetched() {
        return (root, query, cb) -> {
            if (query != null && Long.class != query.getResultType()) {
                root.fetch("employee", JoinType.LEFT);
                root.fetch("reviewedBy", JoinType.LEFT);
                query.distinct(true);
            }
            return null;
        };
    }
}
