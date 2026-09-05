package com.attendance.saas.repository;

import com.attendance.saas.entity.CompanySettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanySettingsRepository extends JpaRepository<CompanySettings, Long> {

    Optional<CompanySettings> findByCompany_Id(Long companyId);
}
