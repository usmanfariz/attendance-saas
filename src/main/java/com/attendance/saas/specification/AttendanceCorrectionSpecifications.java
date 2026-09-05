package com.attendance.saas.specification;

import com.attendance.saas.entity.AttendanceCorrection;
import com.attendance.saas.entity.enums.RequestStatus;
import jakarta.persistence.criteria.JoinType;
import lombok.experimental.UtilityClass;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

@UtilityClass
public class AttendanceCorrectionSpecifications {

    public Specification<AttendanceCorrection> ofCompany(Long companyId) {
        return (root, query, cb) -> cb.equal(root.get("company").get("id"), companyId);
    }

    public Specification<AttendanceCorrection> ofEmployee(Long employeeId) {
        if (employeeId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("employee").get("id"), employeeId);
    }

    public Specification<AttendanceCorrection> hasStatus(RequestStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public Specification<AttendanceCorrection> dateFrom(LocalDate startDate) {
        if (startDate == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("attendanceDate"), startDate);
    }

    public Specification<AttendanceCorrection> dateTo(LocalDate endDate) {
        if (endDate == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("attendanceDate"), endDate);
    }

    public Specification<AttendanceCorrection> withAssociationsFetched() {
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
