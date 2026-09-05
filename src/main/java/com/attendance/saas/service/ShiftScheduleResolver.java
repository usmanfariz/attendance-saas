package com.attendance.saas.service;

import com.attendance.saas.entity.Company;
import com.attendance.saas.entity.Employee;
import com.attendance.saas.entity.EmployeeShift;
import com.attendance.saas.entity.Shift;
import com.attendance.saas.exception.BusinessException;
import com.attendance.saas.exception.ErrorCode;
import com.attendance.saas.repository.EmployeeShiftRepository;
import com.attendance.saas.repository.ShiftRepository;
import com.attendance.saas.util.ShiftWindow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Decides which shift occurrence a check-in belongs to.
 *
 * <p>The hard case is the night shift. At 00:30 on 6 Sep an employee rostered
 * 22:00 → 07:00 is working the shift that <em>started on 5 Sep</em>, so the
 * attendance must be dated 5 Sep. This resolver therefore considers both today
 * and yesterday in the company timezone and picks the occurrence whose window
 * actually contains the moment of the check-in — preferring the one that
 * started most recently when several match.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftScheduleResolver {

    /** How early a check-in may be attributed to an upcoming shift. */
    private static final Duration CHECK_IN_OPENS_BEFORE = Duration.ofHours(6);

    private final EmployeeShiftRepository employeeShiftRepository;
    private final ShiftRepository shiftRepository;

    /**
     * @throws BusinessException when the employee has neither a roster entry
     *                           nor a company default shift.
     */
    @Transactional(readOnly = true)
    public ShiftWindow resolveForCheckIn(Company company, Employee employee, Instant now) {
        ZoneId zone = company.zoneId();
        LocalDate today = now.atZone(zone).toLocalDate();

        List<ShiftWindow> candidates = new ArrayList<>();
        addCandidate(candidates, company, employee, today, zone);
        addCandidate(candidates, company, employee, today.minusDays(1), zone);

        if (candidates.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.NO_SHIFT_ASSIGNED,
                    "Belum ada shift yang ditetapkan untuk Anda pada tanggal ini");
        }

        return candidates.stream()
                .filter(window -> window.acceptsCheckInAt(now, CHECK_IN_OPENS_BEFORE))
                // Several windows can overlap; the one that started last is the
                // shift the employee is actually turning up for.
                .max(Comparator.comparing(ShiftWindow::startInstant))
                // Checking in far outside any window still belongs to today,
                // and will be recorded as heavily late rather than rejected.
                .orElseGet(() -> candidates.get(0));
    }

    /** Shift occurrence for a known business date, used by check-out and reports. */
    @Transactional(readOnly = true)
    public Optional<ShiftWindow> resolveForDate(Company company, Employee employee, LocalDate businessDate) {
        return findShift(company, employee, businessDate)
                .map(shift -> new ShiftWindow(businessDate, shift, company.zoneId()));
    }

    public ShiftWindow windowFor(Company company, Shift shift, LocalDate businessDate) {
        return new ShiftWindow(businessDate, shift, company.zoneId());
    }

    private void addCandidate(List<ShiftWindow> candidates,
                              Company company,
                              Employee employee,
                              LocalDate date,
                              ZoneId zone) {
        findShift(company, employee, date)
                .ifPresent(shift -> candidates.add(new ShiftWindow(date, shift, zone)));
    }

    /** Roster entry first, company default shift as the fallback. */
    private Optional<Shift> findShift(Company company, Employee employee, LocalDate date) {
        Optional<Shift> rostered = employeeShiftRepository
                .findAssignment(company.getId(), employee.getId(), date)
                .map(EmployeeShift::getShift);
        if (rostered.isPresent()) {
            return rostered;
        }
        return shiftRepository.findFirstByCompany_IdAndDefaultShiftTrue(company.getId());
    }
}
