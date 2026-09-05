package com.attendance.saas.repository;

import com.attendance.saas.entity.AttendanceCorrection;
import com.attendance.saas.entity.enums.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.Optional;

public interface AttendanceCorrectionRepository extends JpaRepository<AttendanceCorrection, Long>,
        JpaSpecificationExecutor<AttendanceCorrection> {

    Optional<AttendanceCorrection> findByIdAndCompany_Id(Long id, Long companyId);

    boolean existsByCompany_IdAndEmployee_IdAndAttendanceDateAndStatus(
            Long companyId, Long employeeId, LocalDate attendanceDate, RequestStatus status);

    long countByCompany_IdAndStatus(Long companyId, RequestStatus status);
}
