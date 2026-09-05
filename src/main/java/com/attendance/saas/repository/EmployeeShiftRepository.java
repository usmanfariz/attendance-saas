package com.attendance.saas.repository;

import com.attendance.saas.entity.EmployeeShift;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeShiftRepository extends JpaRepository<EmployeeShift, Long> {

    Optional<EmployeeShift> findByIdAndCompany_Id(Long id, Long companyId);

    /**
     * Roster entry driving check-in. Scoped by company so a leaked employee id
     * from another tenant resolves to nothing.
     */
    @Query("""
            SELECT es FROM EmployeeShift es
            JOIN FETCH es.shift
            WHERE es.company.id = :companyId
              AND es.employee.id = :employeeId
              AND es.shiftDate = :shiftDate
            """)
    Optional<EmployeeShift> findAssignment(@Param("companyId") Long companyId,
                                           @Param("employeeId") Long employeeId,
                                           @Param("shiftDate") LocalDate shiftDate);

    @Query("""
            SELECT es FROM EmployeeShift es
            JOIN FETCH es.shift
            WHERE es.company.id = :companyId
              AND es.employee.id = :employeeId
              AND es.shiftDate BETWEEN :startDate AND :endDate
            ORDER BY es.shiftDate
            """)
    List<EmployeeShift> findAssignmentsInRange(@Param("companyId") Long companyId,
                                               @Param("employeeId") Long employeeId,
                                               @Param("startDate") LocalDate startDate,
                                               @Param("endDate") LocalDate endDate);

    @Query("""
            SELECT es FROM EmployeeShift es
            WHERE es.company.id = :companyId
              AND (:employeeId IS NULL OR es.employee.id = :employeeId)
              AND es.shiftDate BETWEEN :startDate AND :endDate
            """)
    Page<EmployeeShift> search(@Param("companyId") Long companyId,
                               @Param("employeeId") Long employeeId,
                               @Param("startDate") LocalDate startDate,
                               @Param("endDate") LocalDate endDate,
                               Pageable pageable);

    /** Distinct employees rostered on a date — the denominator for "absent". */
    @Query("""
            SELECT COUNT(DISTINCT es.employee.id) FROM EmployeeShift es
            WHERE es.company.id = :companyId AND es.shiftDate = :date
            """)
    long countRosteredEmployeesOn(@Param("companyId") Long companyId,
                                  @Param("date") LocalDate date);

    long countByShift_Id(Long shiftId);

    void deleteByEmployee_IdAndShiftDateBetween(Long employeeId, LocalDate startDate, LocalDate endDate);
}
