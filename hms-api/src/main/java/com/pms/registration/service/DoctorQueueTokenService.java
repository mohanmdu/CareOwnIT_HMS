package com.pms.registration.service;

import com.pms.registration.entity.DoctorQueueCounter;
import com.pms.registration.repository.DoctorQueueCounterRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Safe, per-(consultant, date) token issuance for a busy reception desk.
 * MANDATORY propagation is deliberate, not defensive-only: the
 * PESSIMISTIC_WRITE row lock acquired here is only actually useful for as
 * long as the CALLER's transaction holds it - if this method opened its own
 * short-lived transaction, the lock would release the instant the counter
 * row is saved, well before the DoctorQueueEntry insert (and its audit-log
 * write) that the lock is meant to protect actually commits. Calling this
 * outside an existing transaction is therefore a bug, not a valid usage -
 * MANDATORY makes Spring fail loudly (IllegalTransactionStateException)
 * instead of silently doing the wrong thing.
 */
@Service
public class DoctorQueueTokenService {

    private final DoctorQueueCounterRepository counterRepository;

    public DoctorQueueTokenService(DoctorQueueCounterRepository counterRepository) {
        this.counterRepository = counterRepository;
    }

    /**
     * Acquires (and releases only at the caller's transaction boundary) the
     * exclusive per-(consultant, date) lock without changing the sequence -
     * used by DoctorQueueService.nextPatient() to serialize "who gets called
     * next" the same way token issuance is serialized, closing a race that
     * @Version alone cannot (two concurrent "Next Patient" clicks picking
     * two DIFFERENT waiting rows before either commits).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockDailyQueue(Long consultantId, LocalDate queueDate) {
        counterRepository.ensureRowExists(consultantId, queueDate);
        counterRepository
                .lockForUpdate(consultantId, queueDate)
                .orElseThrow(() -> new IllegalStateException(
                        "doctor_queue_counter row missing immediately after ensureRowExists for consultant "
                                + consultantId + " on " + queueDate));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int nextTokenNumber(Long consultantId, LocalDate queueDate) {
        counterRepository.ensureRowExists(consultantId, queueDate);
        DoctorQueueCounter counter = counterRepository
                .lockForUpdate(consultantId, queueDate)
                .orElseThrow(() -> new IllegalStateException(
                        "doctor_queue_counter row missing immediately after ensureRowExists for consultant "
                                + consultantId + " on " + queueDate));
        int next = counter.getLastTokenNumber() + 1;
        counter.setLastTokenNumber(next);
        counterRepository.save(counter);
        return next;
    }
}
