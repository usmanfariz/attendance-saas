package com.attendance.saas.service;

import com.attendance.saas.dto.common.PageResponse;
import com.attendance.saas.dto.location.CompanySettingsRequest;
import com.attendance.saas.dto.location.CompanySettingsResponse;
import com.attendance.saas.dto.location.LocationRequest;
import com.attendance.saas.dto.location.LocationResponse;
import com.attendance.saas.entity.CompanySettings;
import com.attendance.saas.entity.Location;
import com.attendance.saas.entity.enums.AuditAction;
import com.attendance.saas.exception.ConflictException;
import com.attendance.saas.exception.ErrorCode;
import com.attendance.saas.exception.ResourceNotFoundException;
import com.attendance.saas.mapper.LocationMapper;
import com.attendance.saas.repository.CompanyRepository;
import com.attendance.saas.repository.CompanySettingsRepository;
import com.attendance.saas.repository.LocationRepository;
import com.attendance.saas.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository locationRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final CompanyRepository companyRepository;
    private final LocationMapper locationMapper;
    private final AuditService auditService;
    private final PlanLimitService planLimitService;

    @Transactional(readOnly = true)
    public PageResponse<LocationResponse> list(Pageable pageable) {
        return PageResponse.of(
                locationRepository.findByCompany_Id(SecurityUtils.requireCompanyId(), pageable),
                locationMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public LocationResponse getById(Long locationId) {
        return locationMapper.toResponse(load(locationId, SecurityUtils.requireCompanyId()));
    }

    @Transactional
    public LocationResponse create(LocationRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        String name = request.name().trim();

        planLimitService.assertCanAddLocation(companyId);

        if (locationRepository.existsByCompany_IdAndNameIgnoreCase(companyId, name)) {
            throw new ConflictException(
                    ErrorCode.LOCATION_NAME_ALREADY_EXISTS, "Nama lokasi sudah digunakan");
        }

        Location location = Location.builder()
                .company(companyRepository.getReferenceById(companyId))
                .name(name)
                .address(trimToNull(request.address()))
                .latitude(request.latitude())
                .longitude(request.longitude())
                .radiusMeter(request.radiusMeter())
                .active(request.active() == null || request.active())
                .build();
        locationRepository.save(location);

        auditService.record(AuditAction.CREATE, "Location", location.getId(),
                "Menambah lokasi %s radius %d m".formatted(name, location.getRadiusMeter()));
        log.info("Location {} created in company {} (radius {} m)",
                location.getId(), companyId, location.getRadiusMeter());
        return locationMapper.toResponse(location);
    }

    @Transactional
    public LocationResponse update(Long locationId, LocationRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        Location location = load(locationId, companyId);
        String name = request.name().trim();

        if (locationRepository.existsByCompany_IdAndNameIgnoreCaseAndIdNot(companyId, name, locationId)) {
            throw new ConflictException(
                    ErrorCode.LOCATION_NAME_ALREADY_EXISTS, "Nama lokasi sudah digunakan");
        }

        location.setName(name);
        location.setAddress(trimToNull(request.address()));
        location.setLatitude(request.latitude());
        location.setLongitude(request.longitude());
        location.setRadiusMeter(request.radiusMeter());
        location.setActive(request.active() == null || request.active());

        auditService.record(AuditAction.UPDATE, "Location", locationId, "Mengubah lokasi " + name);
        log.info("Location {} updated in company {}", locationId, companyId);
        return locationMapper.toResponse(location);
    }

    @Transactional
    public void delete(Long locationId) {
        Long companyId = SecurityUtils.requireCompanyId();
        Location location = load(locationId, companyId);
        locationRepository.delete(location);
        auditService.record(AuditAction.DELETE, "Location", locationId,
                "Menghapus lokasi " + location.getName());
        log.info("Location {} deleted from company {}", locationId, companyId);
    }

    @Transactional(readOnly = true)
    public CompanySettingsResponse getSettings() {
        Long companyId = SecurityUtils.requireCompanyId();
        boolean enabled = companySettingsRepository.findByCompany_Id(companyId)
                .map(CompanySettings::isGeofenceEnabled)
                .orElse(false);
        return new CompanySettingsResponse(
                enabled, locationRepository.findByCompany_IdAndActiveTrue(companyId).size());
    }

    @Transactional
    public CompanySettingsResponse updateSettings(CompanySettingsRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();

        // Tenants created before this feature existed have no settings row yet.
        CompanySettings settings = companySettingsRepository.findByCompany_Id(companyId)
                .orElseGet(() -> CompanySettings.builder()
                        .company(companyRepository.getReferenceById(companyId))
                        .build());
        if (Boolean.TRUE.equals(request.geofenceEnabled())) {
            // Geofencing is a paid feature; switching it on requires the entitlement.
            planLimitService.assertGeofenceIncluded(companyId);
        }
        settings.setGeofenceEnabled(Boolean.TRUE.equals(request.geofenceEnabled()));
        companySettingsRepository.save(settings);

        auditService.record(AuditAction.UPDATE, "CompanySettings", settings.getId(),
                "Geofence " + (settings.isGeofenceEnabled() ? "diaktifkan" : "dinonaktifkan"));
        log.info("Company {} geofence enabled = {}", companyId, settings.isGeofenceEnabled());
        return new CompanySettingsResponse(
                settings.isGeofenceEnabled(),
                locationRepository.findByCompany_IdAndActiveTrue(companyId).size());
    }

    private Location load(Long locationId, Long companyId) {
        return locationRepository.findByIdAndCompany_Id(locationId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.LOCATION_NOT_FOUND, "Lokasi tidak ditemukan"));
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
