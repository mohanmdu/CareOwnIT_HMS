package com.pms.registration.service;

import com.pms.common.EntityNotFoundException;
import com.pms.masters.entity.Consultant;
import com.pms.masters.entity.ConsultantTiming;
import com.pms.masters.entity.Session;
import com.pms.masters.repository.ConsultantRepository;
import com.pms.masters.repository.ConsultantTimingRepository;
import com.pms.registration.dto.AppointmentSlotDto;
import com.pms.registration.dto.DailyAvailabilityDto;
import com.pms.registration.dto.SessionSummaryDto;
import com.pms.registration.dto.SlotStatus;
import com.pms.registration.entity.Appointment;
import com.pms.registration.entity.AppointmentStatus;
import com.pms.registration.repository.AppointmentRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates bookable time slots on the fly from a consultant's weekly
 * ConsultantTiming rows + slotsPerHour, cross-referenced against existing
 * Appointment rows - the integration point ConsultantTiming's Javadoc has
 * been waiting for since it was first introduced. No slots are persisted;
 * this is a pure read-time projection, kept separate from AppointmentService
 * so slot math doesn't bloat the CRUD/status-transition service.
 *
 * Every session is a same-day range (see Session) - a slot generated for a
 * queried date always carries that same date.
 */
@Service
@Transactional(readOnly = true)
public class AppointmentAvailabilityService {

    private final ConsultantRepository consultantRepository;
    private final ConsultantTimingRepository timingRepository;
    private final AppointmentRepository appointmentRepository;

    public AppointmentAvailabilityService(
            ConsultantRepository consultantRepository,
            ConsultantTimingRepository timingRepository,
            AppointmentRepository appointmentRepository) {
        this.consultantRepository = consultantRepository;
        this.timingRepository = timingRepository;
        this.appointmentRepository = appointmentRepository;
    }

    public List<DailyAvailabilityDto> summary(Long consultantId, LocalDate fromDate, LocalDate toDate) {
        Consultant consultant = getConsultant(consultantId);
        List<ConsultantTiming> timings = timingRepository.findByConsultantIdOrderByDayOfWeekAscSessionAsc(consultantId);
        LocalDateTime now = LocalDateTime.now();

        List<DailyAvailabilityDto> result = new ArrayList<>();
        for (LocalDate cursor = fromDate; !cursor.isAfter(toDate); cursor = cursor.plusDays(1)) {
            final LocalDate date = cursor;
            List<ConsultantTiming> dayTimings =
                    timings.stream().filter(t -> t.getDayOfWeek() == date.getDayOfWeek()).toList();
            List<Appointment> relevantAppointments = appointmentRepository
                    .findByConsultantIdAndAppointmentDateInAndStatusNot(
                            consultantId, List.of(date, date.plusDays(1)), AppointmentStatus.CANCELLED);

            List<SessionSummaryDto> sessions = new ArrayList<>();
            for (Session session : Session.values()) {
                Optional<ConsultantTiming> timing =
                        dayTimings.stream().filter(t -> t.getSession() == session).findFirst();
                if (timing.isEmpty()) {
                    sessions.add(new SessionSummaryDto(session, false, 0, 0));
                    continue;
                }
                List<TimeSlot> slots = generateSlots(date, timing.get(), consultant.getSlotsPerHour());
                int bookedCount = (int)
                        slots.stream().filter(s -> isBooked(relevantAppointments, s)).count();
                int availableCount = (int) slots.stream()
                        .filter(s -> !isBooked(relevantAppointments, s) && !isPast(s, now))
                        .count();
                sessions.add(new SessionSummaryDto(session, true, availableCount, bookedCount));
            }
            result.add(new DailyAvailabilityDto(date, date.getDayOfWeek(), sessions));
        }
        return result;
    }

    public List<AppointmentSlotDto> slots(Long consultantId, LocalDate date) {
        Consultant consultant = getConsultant(consultantId);
        List<ConsultantTiming> dayTimings = timingRepository
                .findByConsultantIdOrderByDayOfWeekAscSessionAsc(consultantId).stream()
                .filter(t -> t.getDayOfWeek() == date.getDayOfWeek())
                .toList();
        List<Appointment> relevantAppointments = appointmentRepository
                .findByConsultantIdAndAppointmentDateInAndStatusNot(
                        consultantId, List.of(date, date.plusDays(1)), AppointmentStatus.CANCELLED);
        LocalDateTime now = LocalDateTime.now();

        List<AppointmentSlotDto> result = new ArrayList<>();
        for (ConsultantTiming timing : dayTimings) {
            for (TimeSlot slot : generateSlots(date, timing, consultant.getSlotsPerHour())) {
                Optional<Appointment> booked = relevantAppointments.stream()
                        .filter(a -> a.getAppointmentDate().equals(slot.date()) && a.getSlotTime().equals(slot.time()))
                        .findFirst();
                if (booked.isPresent()) {
                    Appointment appointment = booked.get();
                    result.add(new AppointmentSlotDto(
                            slot.date(),
                            slot.time(),
                            timing.getSession(),
                            SlotStatus.BOOKED,
                            appointment.getId(),
                            appointment.getPatient().getId(),
                            displayName(appointment)));
                } else if (isPast(slot, now)) {
                    result.add(new AppointmentSlotDto(
                            slot.date(), slot.time(), timing.getSession(), SlotStatus.PAST, null, null, null));
                } else {
                    result.add(new AppointmentSlotDto(
                            slot.date(), slot.time(), timing.getSession(), SlotStatus.AVAILABLE, null, null, null));
                }
            }
        }
        return result;
    }

    private record TimeSlot(LocalDate date, LocalTime time) {
    }

    /** Every session is a same-day range (see Session) - start and end both fall on baseDate. */
    private List<TimeSlot> generateSlots(LocalDate baseDate, ConsultantTiming timing, int slotsPerHour) {
        int minutesPerSlot = Math.max(1, 60 / slotsPerHour);
        LocalDateTime cursor = LocalDateTime.of(baseDate, timing.getStartTime());
        LocalDateTime end = LocalDateTime.of(baseDate, timing.getEndTime());

        List<TimeSlot> result = new ArrayList<>();
        while (cursor.isBefore(end)) {
            result.add(new TimeSlot(cursor.toLocalDate(), cursor.toLocalTime()));
            cursor = cursor.plusMinutes(minutesPerSlot);
        }
        return result;
    }

    private boolean isBooked(List<Appointment> appointments, TimeSlot slot) {
        return appointments.stream()
                .anyMatch(a -> a.getAppointmentDate().equals(slot.date()) && a.getSlotTime().equals(slot.time()));
    }

    private boolean isPast(TimeSlot slot, LocalDateTime now) {
        return LocalDateTime.of(slot.date(), slot.time()).isBefore(now);
    }

    private String displayName(Appointment appointment) {
        var patient = appointment.getPatient();
        return (patient.getLastName() == null || patient.getLastName().isBlank())
                ? patient.getFirstName()
                : patient.getFirstName() + " " + patient.getLastName();
    }

    private Consultant getConsultant(Long consultantId) {
        return consultantRepository.findById(consultantId)
                .orElseThrow(() -> new EntityNotFoundException("Consultant not found: " + consultantId));
    }
}
