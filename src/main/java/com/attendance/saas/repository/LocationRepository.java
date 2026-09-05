package com.attendance.saas.repository;

import com.attendance.saas.entity.Location;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {

    Optional<Location> findByIdAndCompany_Id(Long id, Long companyId);

    boolean existsByCompany_IdAndNameIgnoreCase(Long companyId, String name);

    boolean existsByCompany_IdAndNameIgnoreCaseAndIdNot(Long companyId, String name, Long id);

    List<Location> findByCompany_IdAndActiveTrue(Long companyId);

    Page<Location> findByCompany_Id(Long companyId, Pageable pageable);
}
