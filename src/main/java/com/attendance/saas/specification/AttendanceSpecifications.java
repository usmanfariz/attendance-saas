package com.attendance.saas.specification;

import com.attendance.saas.entity.Attendance;
import com.attendance.saas.entity.enums.AttendanceStatus;
import jakarta.persistence.criteria.JoinType;
import lombok.experimental.UtilityClass;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/**
 * Filters for the attendance list endpoint. {@link #ofCompany(Long)} is always
 * combined first, so no filter combination can widen the query past one tenant.
 */
@UtilityClass
public class AttendanceSpecifications {

    public Specification<Attendance> ofCompany(Long companyId) {
        return (root, query, cb) -> cb.equal(root.get("company").get("id"), companyId);
    }

    public Specification<Attendance> ofEmployee(Long employeeId) {
        if (employeeId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("employee").get("id"), employeeId);
    }

    public Specification<Attendance> ofDepartment(Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        return (root, query, cb) ->
                cb.equal(root.get("employee").get("department").get("id"), departmentId);
    }

    public Specification<Attendance> ofShift(Long shiftId) {
        if (shiftId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("shift").get("id"), shiftId);
    }

    public Specification<Attendance> hasStatus(AttendanceStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public Specification<Attendance> dateFrom(LocalDate startDate) {
        if (startDate == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("attendanceDate"), startDate);
    }

    public Specification<Attendance> dateTo(LocalDate endDate) {
        if (endDate == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("attendanceDate"), endDate);
    }

    /** Loads employee and shift in one select so a page costs a constant number of queries. */
    public Specification<Attendance> withAssociationsFetched() {
        return (root, query, cb) -> {
            if (query != null && Long.class != query.getResultType()) {
                root.fetch("employee", JoinType.LEFT);
                root.fetch("shift", JoinType.LEFT);
                query.distinct(true);
            }
            return null;
        };
    }
}
