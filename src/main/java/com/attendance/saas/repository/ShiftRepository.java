package com.attendance.saas.repository;

import com.attendance.saas.entity.Shift;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ShiftRepository extends JpaRepository<Shift, Long> {

    Optional<Shift> findByIdAndCompany_Id(Long id, Long companyId);

    boolean existsByCompany_IdAndNameIgnoreCase(Long companyId, String name);

    boolean existsByCompany_IdAndNameIgnoreCaseAndIdNot(Long companyId, String name, Long id);

    List<Shift> findByCompany_IdOrderByStartTimeAsc(Long companyId);

    Optional<Shift> findFirstByCompany_IdAndDefaultShiftTrue(Long companyId);

    /** Clears the default flag so at most one shift per company carries it. */
    @Modifying
    @Query("UPDATE Shift s SET s.defaultShift = false WHERE s.company.id = :companyId AND s.id <> :keepId")
    int clearDefaultExcept(@Param("companyId") Long companyId, @Param("keepId") Long keepId);

    @Query("""
            SELECT s FROM Shift s
            WHERE s.company.id = :companyId
              AND (:keyword IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<Shift> search(@Param("companyId") Long companyId,
                       @Param("keyword") String keyword,
                       Pageable pageable);
}
