package com.attendance.saas.repository;

import com.attendance.saas.entity.Employee;
import com.attendance.saas.entity.enums.EmployeeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long>,
        JpaSpecificationExecutor<Employee> {

    Optional<Employee> findByIdAndCompany_Id(Long id, Long companyId);

    boolean existsByCompany_IdAndEmployeeCodeIgnoreCase(Long companyId, String employeeCode);

    boolean existsByCompany_IdAndEmailIgnoreCase(Long companyId, String email);

    boolean existsByCompany_IdAndEmailIgnoreCaseAndIdNot(Long companyId, String email, Long id);

    long countByCompany_Id(Long companyId);

    long countByCompany_IdAndStatus(Long companyId, EmployeeStatus status);

    long countByDepartment_Id(Long departmentId);

    long countByPosition_Id(Long positionId);
}
