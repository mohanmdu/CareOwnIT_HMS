package com.pms.reporting.service;

import com.pms.insurance.entity.PreAuthorizationStatus;
import com.pms.insurance.repository.PreAuthorizationRequestRepository;
import com.pms.ipadmission.entity.AdmissionStatus;
import com.pms.ipadmission.repository.AdmissionRepository;
import com.pms.reporting.dto.DashboardDto;
import com.pms.registration.repository.AppointmentRepository;
import com.pms.registration.repository.PatientRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Replaces AdminServiceImpl/AdminDaoImpl's dashboard aggregation methods
 * (DashBoardDetailsBean - migration doc §4.6) with cross-module repository
 * queries instead of the legacy's ad hoc per-screen SQL. No server-side
 * report engine exists yet (see migration doc §4.6/§6.2 recommendation to
 * add POI/OpenPDF) - this is a read-only aggregate, not a print/export view.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final PatientRepository patientRepository;
    private final AdmissionRepository admissionRepository;
    private final AppointmentRepository appointmentRepository;
    private final CeoOpRevenueService ceoOpRevenueService;
    private final PreAuthorizationRequestRepository preAuthorizationRequestRepository;

    public DashboardService(
            PatientRepository patientRepository,
            AdmissionRepository admissionRepository,
            AppointmentRepository appointmentRepository,
            CeoOpRevenueService ceoOpRevenueService,
            PreAuthorizationRequestRepository preAuthorizationRequestRepository) {
        this.patientRepository = patientRepository;
        this.admissionRepository = admissionRepository;
        this.appointmentRepository = appointmentRepository;
        this.ceoOpRevenueService = ceoOpRevenueService;
        this.preAuthorizationRequestRepository = preAuthorizationRequestRepository;
    }

    public DashboardDto summary() {
        return new DashboardDto(
                patientRepository.count(),
                admissionRepository.countByStatus(AdmissionStatus.ADMITTED),
                appointmentRepository.findByAppointmentDate(LocalDate.now()).size(),
                ceoOpRevenueService.revenue(null, null).total(),
                preAuthorizationRequestRepository.findByStatus(PreAuthorizationStatus.PENDING).size());
    }
}
