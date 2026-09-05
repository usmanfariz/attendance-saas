package com.attendance.saas.repository;

import com.attendance.saas.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Platform catalogue: not tenant-scoped, so finders take no company id.
 */
public interface PlanRepository extends JpaRepository<Plan, Long> {

    Optional<Plan> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    List<Plan> findAllByOrderBySortOrderAscNameAsc();

    List<Plan> findByActiveTrueOrderBySortOrderAscNameAsc();
}
