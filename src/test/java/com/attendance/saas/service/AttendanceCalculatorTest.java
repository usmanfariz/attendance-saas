package com.attendance.saas.service;

import com.attendance.saas.entity.Shift;
import com.attendance.saas.entity.enums.AttendanceStatus;
import com.attendance.saas.util.ShiftWindow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Section 24 of the specification, expressed as tests. Jakarta is UTC+7 with no
 * daylight saving, so every instant below is stated as its UTC equivalent.
 */
class AttendanceCalculatorTest {

    private static final ZoneId JAKARTA = ZoneId.of("Asia/Jakarta");
    private static final LocalDate DATE = LocalDate.of(2026, 9, 5);

    private final AttendanceCalculator calculator = new AttendanceCalculator();

    private static Shift shift(LocalTime start, LocalTime end, int lateTolerance, int earlyTolerance) {
        return Shift.builder()
                .name("test")
                .startTime(start)
                .endTime(end)
                .lateToleranceMinutes(lateTolerance)
                .earlyLeaveToleranceMinutes(earlyTolerance)
                .build();
    }

    /** Wall-clock time in Jakarta on the given date, as an instant. */
    private static Instant jakarta(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(JAKARTA).toInstant();
    }

    @Nested
    @DisplayName("Shift siang 08:00-17:00, toleransi telat 15 menit")
    class DayShift {

        private final ShiftWindow window = new ShiftWindow(
                DATE, shift(LocalTime.of(8, 0), LocalTime.of(17, 0), 15, 10), JAKARTA);

        @Test
        @DisplayName("datang lebih awal tidak dihitung terlambat")
        void earlyArrivalIsNotLate() {
            assertThat(calculator.lateMinutes(window, jakarta(DATE, 7, 45))).isZero();
        }

        @Test
        @DisplayName("datang tepat waktu tidak terlambat")
        void onTimeIsNotLate() {
            assertThat(calculator.lateMinutes(window, jakarta(DATE, 8, 0))).isZero();
        }

        @Test
        @DisplayName("datang dalam toleransi tidak terlambat")
        void withinToleranceIsNotLate() {
            assertThat(calculator.lateMinutes(window, jakarta(DATE, 8, 14))).isZero();
        }

        @Test
        @DisplayName("tepat pada batas toleransi belum terlambat")
        void exactlyAtToleranceBoundaryIsNotLate() {
            assertThat(calculator.lateMinutes(window, jakarta(DATE, 8, 15))).isZero();
        }

        @Test
        @DisplayName("melewati toleransi dihitung penuh dari jam mulai shift")
        void beyondToleranceCountsFromShiftStart() {
            // 08:20 with a 15-minute tolerance is 20 minutes late, not 5.
            assertThat(calculator.lateMinutes(window, jakarta(DATE, 8, 20))).isEqualTo(20);
            assertThat(calculator.lateMinutes(window, jakarta(DATE, 10, 0))).isEqualTo(120);
        }

        @Test
        @DisplayName("pulang tepat waktu atau lembur tidak dihitung pulang cepat")
        void leavingOnTimeOrLateIsNotEarly() {
            assertThat(calculator.earlyLeaveMinutes(window, jakarta(DATE, 17, 0))).isZero();
            assertThat(calculator.earlyLeaveMinutes(window, jakarta(DATE, 18, 30))).isZero();
        }

        @Test
        @DisplayName("pulang dalam toleransi tidak dihitung pulang cepat")
        void leavingWithinToleranceIsNotEarly() {
            assertThat(calculator.earlyLeaveMinutes(window, jakarta(DATE, 16, 55))).isZero();
            assertThat(calculator.earlyLeaveMinutes(window, jakarta(DATE, 16, 50))).isZero();
        }

        @Test
        @DisplayName("pulang melewati toleransi dihitung penuh dari jam selesai shift")
        void leavingBeyondToleranceCountsFromShiftEnd() {
            assertThat(calculator.earlyLeaveMinutes(window, jakarta(DATE, 16, 30))).isEqualTo(30);
            assertThat(calculator.earlyLeaveMinutes(window, jakarta(DATE, 15, 0))).isEqualTo(120);
        }

        @Test
        @DisplayName("work_minutes adalah selisih check-in dan check-out")
        void workMinutesIsElapsedTime() {
            assertThat(calculator.workMinutes(jakarta(DATE, 8, 0), jakarta(DATE, 17, 0)))
                    .isEqualTo(540);
        }
    }

    @Nested
    @DisplayName("Shift malam 22:00-07:00 melewati tengah malam")
    class NightShift {

        private final ShiftWindow window = new ShiftWindow(
                DATE, shift(LocalTime.of(22, 0), LocalTime.of(7, 0), 15, 10), JAKARTA);

        @Test
        @DisplayName("shift ditandai melewati tengah malam dan berakhir esok hari")
        void windowEndsOnTheNextDay() {
            assertThat(window.shift().crossesMidnight()).isTrue();
            assertThat(window.start().toLocalDate()).isEqualTo(DATE);
            assertThat(window.end().toLocalDate()).isEqualTo(DATE.plusDays(1));
            assertThat(window.end().toLocalTime()).isEqualTo(LocalTime.of(7, 0));
            assertThat(window.shift().scheduledMinutes()).isEqualTo(540);
        }

        @Test
        @DisplayName("check-in 22:10 masih dalam toleransi")
        void checkInWithinToleranceIsNotLate() {
            assertThat(calculator.lateMinutes(window, jakarta(DATE, 22, 10))).isZero();
        }

        @Test
        @DisplayName("check-in 23:00 terlambat 60 menit")
        void lateCheckInOnStartingDay() {
            assertThat(calculator.lateMinutes(window, jakarta(DATE, 23, 0))).isEqualTo(60);
        }

        @Test
        @DisplayName("check-in 00:30 esok hari terlambat 150 menit, bukan negatif")
        void lateCheckInAfterMidnight() {
            Instant afterMidnight = jakarta(DATE.plusDays(1), 0, 30);
            assertThat(calculator.lateMinutes(window, afterMidnight)).isEqualTo(150);
        }

        @Test
        @DisplayName("check-out 07:00 esok hari tepat waktu")
        void checkOutNextMorningIsOnTime() {
            assertThat(calculator.earlyLeaveMinutes(window, jakarta(DATE.plusDays(1), 7, 0))).isZero();
        }

        @Test
        @DisplayName("check-out 06:00 esok hari pulang cepat 60 menit")
        void earlyCheckOutNextMorning() {
            assertThat(calculator.earlyLeaveMinutes(window, jakarta(DATE.plusDays(1), 6, 0)))
                    .isEqualTo(60);
        }

        @Test
        @DisplayName("work_minutes shift malam menghitung lintas hari dengan benar")
        void workMinutesSpansMidnight() {
            int minutes = calculator.workMinutes(
                    jakarta(DATE, 22, 0), jakarta(DATE.plusDays(1), 7, 0));
            assertThat(minutes).isEqualTo(540);
        }
    }

    @Nested
    @DisplayName("Perilaku umum")
    class General {

        private final ShiftWindow window = new ShiftWindow(
                DATE, shift(LocalTime.of(8, 0), LocalTime.of(17, 0), 0, 0), JAKARTA);

        @Test
        @DisplayName("status PRESENT bila tidak terlambat, LATE bila terlambat")
        void statusFollowsLateness() {
            assertThat(calculator.statusFor(0)).isEqualTo(AttendanceStatus.PRESENT);
            assertThat(calculator.statusFor(1)).isEqualTo(AttendanceStatus.LATE);
        }

        @Test
        @DisplayName("tanpa toleransi, terlambat satu menit tetap terlambat")
        void zeroToleranceIsStrict() {
            assertThat(calculator.lateMinutes(window, jakarta(DATE, 8, 1))).isEqualTo(1);
        }

        @Test
        @DisplayName("work_minutes nol bila data tidak lengkap atau terbalik")
        void workMinutesIsSafeOnBadInput() {
            assertThat(calculator.workMinutes(null, jakarta(DATE, 17, 0))).isZero();
            assertThat(calculator.workMinutes(jakarta(DATE, 8, 0), null)).isZero();
            assertThat(calculator.workMinutes(jakarta(DATE, 17, 0), jakarta(DATE, 8, 0))).isZero();
        }

        @Test
        @DisplayName("timezone perusahaan dipakai, bukan timezone server")
        void companyTimezoneDrivesTheWindow() {
            ShiftWindow jayapura = new ShiftWindow(
                    DATE, shift(LocalTime.of(8, 0), LocalTime.of(17, 0), 0, 0), ZoneId.of("Asia/Jayapura"));

            // 08:00 in Jayapura (UTC+9) is 06:00 in Jakarta (UTC+7): two hours earlier.
            assertThat(jayapura.startInstant())
                    .isEqualTo(window.startInstant().minus(java.time.Duration.ofHours(2)));
            assertThat(calculator.lateMinutes(jayapura, jakarta(DATE, 6, 0))).isZero();
        }
    }
}
