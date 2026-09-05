package com.attendance.saas.dto.billing;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What the tenant is on and how much of it is used — the "am I about to hit a
 * wall?" view for a company admin.
 */
@Schema(name = "SubscriptionUsageResponse")
public record SubscriptionUsageResponse(
        SubscriptionResponse subscription,
        Usage employees,
        Usage locations,
        Usage users,
        boolean geofenceAvailable
) {

    /**
     * @param limit null means unlimited
     * @param remaining null when unlimited
     */
    @Schema(name = "PlanUsage")
    public record Usage(long used, Integer limit, Integer remaining, boolean unlimited) {

        public static Usage of(long used, Integer limit) {
            if (limit == null) {
                return new Usage(used, null, null, true);
            }
            return new Usage(used, limit, Math.max(0, (int) (limit - used)), false);
        }
    }
}
