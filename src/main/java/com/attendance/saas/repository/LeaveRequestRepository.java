package com.attendance.saas.repository;

import com.attendance.saas.entity.LeaveRequest;
import com.attendance.saas.entity.enums.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long>,
        JpaSpecificationExecutor<LeaveRequest> {

    Optional<LeaveRequest> findByIdAndCompany_Id(Long id, Long companyId);

    /**
     * Requests whose range overlaps the given one. Two ranges overlap when each
     * starts on or before the other ends.
     */
    @Query("""
            SELECT lr FROM LeaveRequest lr
            WHERE lr.company.id = :companyId
              AND lr.employee.id = :employeeId
              AND lr.status IN :statuses
              AND lr.startDate <= :endDate
              AND lr.endDate   >= :startDate
            """)
    List<LeaveRequest> findOverlapping(@Param("companyId") Long companyId,
                                       @Param("employeeId") Long employeeId,
                                       @Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate,
                                       @Param("statuses") Collection<RequestStatus> statuses);

    long countByCompany_IdAndStatus(Long companyId, RequestStatus status);

    /**
     * Days of one leave type taken in a calendar year, attributed to the year
     * the leave started in so a request spanning New Year is counted once.
     */
    @Query("""
            SELECT COALESCE(SUM(lr.totalDays), 0L) FROM LeaveRequest lr
            WHERE lr.company.id = :companyId
              AND lr.employee.id = :employeeId
              AND lr.leaveType = :leaveType
              AND lr.status = :status
              AND lr.startDate BETWEEN :yearStart AND :yearEnd
            """)
    long sumDaysByTypeAndStatus(@Param("companyId") Long companyId,
                                @Param("employeeId") Long employeeId,
                                @Param("leaveType") com.attendance.saas.entity.enums.LeaveType leaveType,
                                @Param("status") RequestStatus status,
                                @Param("yearStart") LocalDate yearStart,
                                @Param("yearEnd") LocalDate yearEnd);

    /** Approved leave covering a single date, used when marking attendance. */
    @Query("""
            SELECT lr FROM LeaveRequest lr
            WHERE lr.company.id = :companyId
              AND lr.employee.id = :employeeId
              AND lr.status = com.attendance.saas.entity.enums.RequestStatus.APPROVED
              AND lr.startDate <= :date
              AND lr.endDate   >= :date
            """)
    List<LeaveRequest> findApprovedCovering(@Param("companyId") Long companyId,
                                            @Param("employeeId") Long employeeId,
                                            @Param("date") LocalDate date);
}
