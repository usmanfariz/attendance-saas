package com.attendance.saas.mapper;

import com.attendance.saas.dto.position.PositionResponse;
import com.attendance.saas.entity.Position;
import org.springframework.stereotype.Component;

@Component
public class PositionMapper {

    public PositionResponse toResponse(Position position, long employeeCount) {
        return new PositionResponse(
                position.getId(),
                position.getName(),
                position.getDescription(),
                employeeCount,
                position.getCreatedAt(),
                position.getUpdatedAt());
    }
}
