package com.attendance.saas.mapper;

import com.attendance.saas.dto.location.LocationResponse;
import com.attendance.saas.entity.Location;
import org.springframework.stereotype.Component;

@Component
public class LocationMapper {

    public LocationResponse toResponse(Location location) {
        return new LocationResponse(
                location.getId(),
                location.getName(),
                location.getAddress(),
                location.getLatitude(),
                location.getLongitude(),
                location.getRadiusMeter(),
                location.isActive(),
                location.getCreatedAt(),
                location.getUpdatedAt());
    }
}
