package com.attendance.saas.service;

import com.attendance.saas.util.GeoUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Haversine distance")
class GeoUtilsTest {

    @Test
    @DisplayName("titik yang sama berjarak nol")
    void samePointIsZero() {
        assertThat(GeoUtils.distanceMeters(-6.2, 106.816666, -6.2, 106.816666)).isZero();
    }

    @Test
    @DisplayName("satu derajat lintang kira-kira 111 km")
    void oneDegreeOfLatitude() {
        double distance = GeoUtils.distanceMeters(0.0, 0.0, 1.0, 0.0);
        assertThat(distance).isCloseTo(111_195, org.assertj.core.data.Offset.offset(200.0));
    }

    @Test
    @DisplayName("pergeseran kecil di Jakarta menghasilkan jarak dalam skala meter")
    void shortDistanceInJakarta() {
        // ~0.0009 degrees of latitude is roughly 100 metres.
        double distance = GeoUtils.distanceMeters(-6.200000, 106.816666, -6.200900, 106.816666);
        assertThat(distance).isBetween(95.0, 105.0);
    }

    @Test
    @DisplayName("jarak simetris ke dua arah")
    void distanceIsSymmetric() {
        double forward = GeoUtils.distanceMeters(-6.2, 106.8, -6.3, 106.9);
        double backward = GeoUtils.distanceMeters(-6.3, 106.9, -6.2, 106.8);
        assertThat(forward).isEqualTo(backward);
    }

    @Test
    @DisplayName("menerima BigDecimal seperti yang disimpan di database")
    void acceptsBigDecimal() {
        double distance = GeoUtils.distanceMeters(
                new BigDecimal("-6.2000000"), new BigDecimal("106.8166660"),
                new BigDecimal("-6.2009000"), new BigDecimal("106.8166660"));
        assertThat(distance).isBetween(95.0, 105.0);
    }

    @Test
    @DisplayName("Jakarta ke Bandung sekitar 120 km")
    void jakartaToBandung() {
        double distance = GeoUtils.distanceMeters(-6.200000, 106.816666, -6.914744, 107.609810);
        assertThat(distance / 1000).isBetween(110.0, 130.0);
    }
}
