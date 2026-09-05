package com.attendance.saas.repository;

import com.attendance.saas.entity.Company;
import com.attendance.saas.entity.enums.CompanyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long>, JpaSpecificationExecutor<Company> {

    Optional<Company> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    @Query("""
            SELECT c FROM Company c
            WHERE (:status IS NULL OR c.status = :status)
              AND (:keyword IS NULL
                   OR LOWER(c.name)  LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(c.code)  LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(c.email) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<Company> search(@Param("keyword") String keyword,
                         @Param("status") CompanyStatus status,
                         Pageable pageable);
}
