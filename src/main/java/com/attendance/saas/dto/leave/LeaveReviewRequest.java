package com.attendance.saas.dto.leave;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Approval or rejection note. Optional when approving, but a rejection without
 * a reason is unhelpful, so the service requires one there.
 */
@Schema(name = "ReviewRequest")
public record LeaveReviewRequest(

        @Size(max = 500)
        @Schema(example = "Disetujui, pekerjaan sudah didelegasikan")
        String note
) {
}
