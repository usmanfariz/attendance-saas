package com.attendance.saas.util;

import lombok.experimental.UtilityClass;

import java.math.BigDecimal;

/**
 * Great-circle distance on a spherical Earth (haversine).
 *
 * <p>At geofence scale — tens to hundreds of metres — the error from ignoring
 * the Earth's flattening is well under a metre, so the extra complexity of an
 * ellipsoidal formula buys nothing here.
 */
@UtilityClass
public class GeoUtils {

    /** IUGG mean Earth radius in metres. */
    private static final double EARTH_RADIUS_METERS = 6_371_008.8;

    public double distanceMeters(BigDecimal latitude1,
                                 BigDecimal longitude1,
                                 BigDecimal latitude2,
                                 BigDecimal longitude2) {
        return distanceMeters(
                latitude1.doubleValue(), longitude1.doubleValue(),
                latitude2.doubleValue(), longitude2.doubleValue());
    }

    public double distanceMeters(double latitude1,
                                 double longitude1,
                                 double latitude2,
                                 double longitude2) {
        double lat1Rad = Math.toRadians(latitude1);
        double lat2Rad = Math.toRadians(latitude2);
        double deltaLat = Math.toRadians(latitude2 - latitude1);
        double deltaLon = Math.toRadians(longitude2 - longitude1);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }
}
