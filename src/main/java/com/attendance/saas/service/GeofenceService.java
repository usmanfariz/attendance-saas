package com.attendance.saas.service;

import com.attendance.saas.entity.Location;
import com.attendance.saas.exception.BusinessException;
import com.attendance.saas.exception.ErrorCode;
import com.attendance.saas.repository.CompanySettingsRepository;
import com.attendance.saas.repository.LocationRepository;
import com.attendance.saas.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Enforces that a check-in happened near one of the company's offices.
 *
 * <p>Enforcement is opt-in per tenant: with {@code geofenceEnabled} off the
 * coordinates are still recorded but never rejected, so turning the feature on
 * is always a deliberate act.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeofenceService {

    private final CompanySettingsRepository companySettingsRepository;
    private final LocationRepository locationRepository;
    private final PlanLimitService planLimitService;

    /**
     * Enforcement needs both the tenant's own switch and the plan entitlement.
     * A tenant downgraded to a plan without geofencing simply stops having
     * check-ins rejected, rather than being locked out of attendance entirely.
     */
    @Transactional(readOnly = true)
    public boolean isEnabled(Long companyId) {
        boolean switchedOn = companySettingsRepository.findByCompany_Id(companyId)
                .map(settings -> settings.isGeofenceEnabled())
                .orElse(false);
        return switchedOn && planLimitService.isGeofenceIncluded(companyId);
    }

    /**
     * @throws BusinessException with {@code LOCATION_REQUIRED} when coordinates
     *                           are missing, {@code NO_ACTIVE_LOCATION} when the
     *                           tenant enabled geofencing but defined no office,
     *                           or {@code OUTSIDE_GEOFENCE} when too far away.
     */
    @Transactional(readOnly = true)
    public void assertWithinGeofence(Long companyId, BigDecimal latitude, BigDecimal longitude) {
        if (!isEnabled(companyId)) {
            return;
        }
        if (latitude == null || longitude == null) {
            throw new BusinessException(
                    ErrorCode.LOCATION_REQUIRED,
                    "Lokasi wajib dikirim karena perusahaan mengaktifkan validasi lokasi");
        }

        List<Location> offices = locationRepository.findByCompany_IdAndActiveTrue(companyId);
        if (offices.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.NO_ACTIVE_LOCATION,
                    "Validasi lokasi aktif tetapi perusahaan belum menetapkan lokasi kantor");
        }

        Optional<Location> nearest = offices.stream()
                .min(Comparator.comparingDouble(office -> distanceTo(office, latitude, longitude)));

        Location office = nearest.orElseThrow();
        double distance = distanceTo(office, latitude, longitude);

        if (distance > office.getRadiusMeter()) {
            log.info("Check-in rejected for company {}: {} m from '{}' (radius {} m)",
                    companyId, Math.round(distance), office.getName(), office.getRadiusMeter());
            throw new BusinessException(
                    ErrorCode.OUTSIDE_GEOFENCE,
                    "Anda berada %d meter dari %s, di luar radius %d meter yang diizinkan"
                            .formatted(Math.round(distance), office.getName(), office.getRadiusMeter()));
        }
    }

    private double distanceTo(Location office, BigDecimal latitude, BigDecimal longitude) {
        return GeoUtils.distanceMeters(
                office.getLatitude(), office.getLongitude(), latitude, longitude);
    }
}
