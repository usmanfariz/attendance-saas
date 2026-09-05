package com.attendance.saas.repository;

import com.attendance.saas.dto.report.AttendanceStatusCount;
import com.attendance.saas.dto.report.EmployeeAttendanceSummary;
import com.attendance.saas.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long>,
        JpaSpecificationExecutor<Attendance> {

    Optional<Attendance> findByIdAndCompany_Id(Long id, Long companyId);

    Optional<Attendance> findByCompany_IdAndEmployee_IdAndAttendanceDate(
            Long companyId, Long employeeId, LocalDate attendanceDate);

    long countByShift_Id(Long shiftId);

    /**
     * Open check-ins ordered newest first. A night shift started yesterday is
     * still open this morning, so check-out looks back over a small window
     * rather than at today alone.
     */
    @Query("""
            SELECT a FROM Attendance a
            LEFT JOIN FETCH a.shift
            WHERE a.company.id = :companyId
              AND a.employee.id = :employeeId
              AND a.checkIn IS NOT NULL
              AND a.checkOut IS NULL
              AND a.attendanceDate BETWEEN :fromDate AND :toDate
            ORDER BY a.attendanceDate DESC
            """)
    List<Attendance> findOpenAttendances(@Param("companyId") Long companyId,
                                         @Param("employeeId") Long employeeId,
                                         @Param("fromDate") LocalDate fromDate,
                                         @Param("toDate") LocalDate toDate);

    /** Rows for one business date, grouped by status — the company dashboard. */
    @Query("""
            SELECT new com.attendance.saas.dto.report.AttendanceStatusCount(a.status, COUNT(a))
            FROM Attendance a
            WHERE a.company.id = :companyId AND a.attendanceDate = :date
            GROUP BY a.status
            """)
    List<AttendanceStatusCount> countByStatusOn(@Param("companyId") Long companyId,
                                                @Param("date") LocalDate date);

    /** Same grouping for one employee over a range — the employee dashboard. */
    @Query("""
            SELECT new com.attendance.saas.dto.report.AttendanceStatusCount(a.status, COUNT(a))
            FROM Attendance a
            WHERE a.company.id = :companyId
              AND a.employee.id = :employeeId
              AND a.attendanceDate BETWEEN :startDate AND :endDate
            GROUP BY a.status
            """)
    List<AttendanceStatusCount> countByStatusForEmployee(@Param("companyId") Long companyId,
                                                         @Param("employeeId") Long employeeId,
                                                         @Param("startDate") LocalDate startDate,
                                                         @Param("endDate") LocalDate endDate);

    @Query("""
            SELECT COALESCE(SUM(a.lateMinutes), 0L), COALESCE(SUM(a.workMinutes), 0L)
            FROM Attendance a
            WHERE a.company.id = :companyId
              AND a.employee.id = :employeeId
              AND a.attendanceDate BETWEEN :startDate AND :endDate
            """)
    List<Object[]> sumMinutesForEmployee(@Param("companyId") Long companyId,
                                         @Param("employeeId") Long employeeId,
                                         @Param("startDate") LocalDate startDate,
                                         @Param("endDate") LocalDate endDate);

    /**
     * Per-employee totals for a range, aggregated by the database so a monthly
     * report never pulls every attendance row into memory.
     */
    @Query("""
            SELECT new com.attendance.saas.dto.report.EmployeeAttendanceSummary(
                e.id, e.employeeCode, e.name, d.name,
                COUNT(a),
                SUM(CASE WHEN a.status = com.attendance.saas.entity.enums.AttendanceStatus.PRESENT
                         THEN 1L ELSE 0L END),
                SUM(CASE WHEN a.status = com.attendance.saas.entity.enums.AttendanceStatus.LATE
                         THEN 1L ELSE 0L END),
                SUM(CASE WHEN a.status IN (
                             com.attendance.saas.entity.enums.AttendanceStatus.LEAVE,
                             com.attendance.saas.entity.enums.AttendanceStatus.SICK,
                             com.attendance.saas.entity.enums.AttendanceStatus.PERMIT)
                         THEN 1L ELSE 0L END),
                SUM(CASE WHEN a.status = com.attendance.saas.entity.enums.AttendanceStatus.ABSENT
                         THEN 1L ELSE 0L END),
                COALESCE(SUM(a.lateMinutes), 0L),
                COALESCE(SUM(a.earlyLeaveMinutes), 0L),
                COALESCE(SUM(a.workMinutes), 0L))
            FROM Attendance a
            JOIN a.employee e
            LEFT JOIN e.department d
            WHERE a.company.id = :companyId
              AND a.attendanceDate BETWEEN :startDate AND :endDate
              AND (:departmentId IS NULL OR d.id = :departmentId)
              AND (:employeeId IS NULL OR e.id = :employeeId)
            GROUP BY e.id, e.employeeCode, e.name, d.name
            ORDER BY e.name
            """)
    List<EmployeeAttendanceSummary> summariseByEmployee(@Param("companyId") Long companyId,
                                                        @Param("startDate") LocalDate startDate,
                                                        @Param("endDate") LocalDate endDate,
                                                        @Param("departmentId") Long departmentId,
                                                        @Param("employeeId") Long employeeId);

    /** Rows for one date with employee and shift loaded — the daily report. */
    @Query("""
            SELECT a FROM Attendance a
            JOIN FETCH a.employee e
            LEFT JOIN FETCH e.department
            LEFT JOIN FETCH a.shift
            WHERE a.company.id = :companyId
              AND a.attendanceDate = :date
              AND (:departmentId IS NULL OR e.department.id = :departmentId)
              AND (:shiftId IS NULL OR a.shift.id = :shiftId)
            ORDER BY e.name
            """)
    List<Attendance> findForDailyReport(@Param("companyId") Long companyId,
                                        @Param("date") LocalDate date,
                                        @Param("departmentId") Long departmentId,
                                        @Param("shiftId") Long shiftId);

    /** One employee's rows over a range, for the per-employee report. */
    @Query("""
            SELECT a FROM Attendance a
            JOIN FETCH a.employee e
            LEFT JOIN FETCH a.shift
            WHERE a.company.id = :companyId
              AND e.id = :employeeId
              AND a.attendanceDate BETWEEN :startDate AND :endDate
            ORDER BY a.attendanceDate
            """)
    List<Attendance> findForEmployeeReport(@Param("companyId") Long companyId,
                                           @Param("employeeId") Long employeeId,
                                           @Param("startDate") LocalDate startDate,
                                           @Param("endDate") LocalDate endDate);
}
