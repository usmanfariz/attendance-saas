package com.attendance.saas.repository;

import com.attendance.saas.entity.Position;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PositionRepository extends JpaRepository<Position, Long> {

    Optional<Position> findByIdAndCompany_Id(Long id, Long companyId);

    boolean existsByCompany_IdAndNameIgnoreCase(Long companyId, String name);

    boolean existsByCompany_IdAndNameIgnoreCaseAndIdNot(Long companyId, String name, Long id);

    List<Position> findByCompany_IdOrderByNameAsc(Long companyId);

    @Query("""
            SELECT p FROM Position p
            WHERE p.company.id = :companyId
              AND (:keyword IS NULL
                   OR LOWER(p.name)        LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<Position> search(@Param("companyId") Long companyId,
                          @Param("keyword") String keyword,
                          Pageable pageable);
}
