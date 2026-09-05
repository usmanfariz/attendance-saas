package com.attendance.saas.repository;

import com.attendance.saas.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /**
     * Tenant-safe lookup: an id alone is never enough to reach another tenant's
     * row.
     */
    Optional<User> findByIdAndCompany_Id(Long id, Long companyId);

    long countByCompany_Id(Long companyId);

    Optional<User> findByEmployee_Id(Long employeeId);

    boolean existsByEmployee_Id(Long employeeId);

    /**
     * Bulk lookup so rendering a page of employees costs one extra query rather
     * than one per row.
     */
    @Query("SELECT u.employee.id FROM User u WHERE u.employee.id IN :employeeIds")
    Set<Long> findEmployeeIdsWithAccount(@Param("employeeIds") Collection<Long> employeeIds);

    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = :loginAt WHERE u.id = :userId")
    void touchLastLogin(@Param("userId") Long userId, @Param("loginAt") Instant loginAt);
}
