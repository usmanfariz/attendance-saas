package com.attendance.saas.repository;

import com.attendance.saas.entity.Department;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Every finder takes the company id: an entity id alone must never be enough to
 * reach another tenant's row.
 */
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Optional<Department> findByIdAndCompany_Id(Long id, Long companyId);

    boolean existsByCompany_IdAndNameIgnoreCase(Long companyId, String name);

    boolean existsByCompany_IdAndNameIgnoreCaseAndIdNot(Long companyId, String name, Long id);

    List<Department> findByCompany_IdOrderByNameAsc(Long companyId);

    @Query("""
            SELECT d FROM Department d
            WHERE d.company.id = :companyId
              AND (:keyword IS NULL
                   OR LOWER(d.name)        LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(d.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<Department> search(@Param("companyId") Long companyId,
                            @Param("keyword") String keyword,
                            Pageable pageable);
}
