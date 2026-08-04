package com.pms.registration.service;

import com.pms.common.EntityNotFoundException;
import com.pms.common.FileStorageService;
import com.pms.registration.dto.PatientReportAuditLogRowDto;
import com.pms.registration.dto.PatientReportDto;
import com.pms.registration.dto.ShareLinkDto;
import com.pms.registration.entity.Patient;
import com.pms.registration.entity.PatientReport;
import com.pms.registration.repository.AppointmentRepository;
import com.pms.registration.repository.PatientRepository;
import com.pms.registration.repository.PatientReportRepository;
import com.pms.security.JwtService;
import com.pms.tenant.TenantContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Upload Reports / View Files module: attach a document (JPEG/PNG/PDF) to a
 * Patient, list it, soft-delete it (captures a reason, moves it to the
 * "Deleted Files" table), or permanently delete it (removes the DB row and
 * the file on disk). Also backs the Audit Log screens, a historical record
 * of every upload regardless of later deletion.
 */
@Service
@Transactional(readOnly = true)
public class PatientReportService {

    private static final String UPLOAD_OPERATION = "Admin - File Upload";

    private final PatientReportRepository repository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final FileStorageService fileStorageService;
    private final JwtService jwtService;

    public PatientReportService(
            PatientReportRepository repository,
            PatientRepository patientRepository,
            AppointmentRepository appointmentRepository,
            FileStorageService fileStorageService,
            JwtService jwtService) {
        this.repository = repository;
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.fileStorageService = fileStorageService;
        this.jwtService = jwtService;
    }

    @Transactional
    public PatientReportDto upload(Long patientId, String comments, MultipartFile file) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new EntityNotFoundException("Patient not found: " + patientId));
        String path = fileStorageService.storePrivateDocument(file, "patient-reports");

        PatientReport report = new PatientReport();
        report.setPatient(patient);
        report.setComments(comments);
        report.setFilePath(path);
        report.setOriginalFileName(file.getOriginalFilename());
        report.setUploadedBy(currentUsername());
        report.setUploadedAt(LocalDateTime.now());
        return toDto(repository.save(report));
    }

    public PatientReportDto getById(Long id) {
        return repository.findById(id).map(this::toDto).orElseThrow(() -> new EntityNotFoundException("Patient report not found: " + id));
    }

    public List<PatientReportDto> getActiveFiles(Long patientId) {
        return repository.findByPatientIdAndDeletedAtIsNullOrderByUploadedAtDesc(patientId).stream().map(this::toDto).toList();
    }

    public List<PatientReportDto> getDeletedFiles(Long patientId) {
        return repository.findByPatientIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(patientId).stream().map(this::toDto).toList();
    }

    @Transactional
    public void softDelete(Long id, String reason) {
        PatientReport report = getOrThrow(id);
        report.setDeletedAt(LocalDateTime.now());
        report.setDeletedBy(currentUsername());
        report.setDeleteReason(reason);
        repository.save(report);
    }

    @Transactional
    public void permanentDelete(Long id) {
        PatientReport report = getOrThrow(id);
        fileStorageService.delete(report.getFilePath());
        repository.delete(report);
    }

    private PatientReport getOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Patient report not found: " + id));
    }

    /**
     * Mints a time-limited link for sharing this one report with someone
     * outside the system (e.g. the patient via WhatsApp - see
     * ReportsUploadFormComponent / patient-files.component's
     * shareToWhatsApp()). The report itself stays private (see
     * FileStorageService.PRIVATE_CATEGORIES) - this is the one deliberate,
     * narrow, time-boxed exception, not a reopening of public access.
     */
    public ShareLinkDto createShareLink(Long id) {
        getOrThrow(id);
        Long clientId = TenantContext.get();
        String token = jwtService.issueReportShareToken(id, clientId);
        return new ShareLinkDto("/api/public/patient-reports/" + id + "/" + token);
    }

    /**
     * Resolves the file for PublicPatientReportController - called from
     * within TenantContext.runAs(tokenClientId, ...), after the caller has
     * already validated the share token, so getOrThrow()/FileStorageService
     * both correctly resolve against the SHARED report's own tenant, not
     * whatever (if anything) TenantContext happened to hold before.
     */
    public Resource resolveFileForSharing(Long id) {
        PatientReport report = getOrThrow(id);
        String filePath = report.getFilePath();
        String filename = filePath.substring(filePath.lastIndexOf('/') + 1);
        Path file = fileStorageService.resolvePrivateFile("patient-reports", filename);
        if (!Files.exists(file)) {
            throw new EntityNotFoundException("Report file not found: " + id);
        }
        return new FileSystemResource(file);
    }

    private PatientReportDto toDto(PatientReport report) {
        Patient patient = report.getPatient();
        return new PatientReportDto(
                report.getId(),
                patient.getId(),
                displayName(patient),
                patient.getRegistrationNumber(),
                patient.getMobileNumber(),
                report.getComments(),
                report.getFilePath(),
                report.getOriginalFileName(),
                fileType(report.getOriginalFileName()),
                report.getUploadedBy(),
                report.getUploadedAt(),
                report.getDeletedAt(),
                report.getDeletedBy(),
                report.getDeleteReason());
    }

    /** Derived from the original filename's extension - there's no separate stored "file type" field. */
    private String fileType(String originalFileName) {
        if (originalFileName == null || !originalFileName.contains(".")) {
            return "—";
        }
        String extension = originalFileName.substring(originalFileName.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);
        return extension.isBlank() ? "—" : extension;
    }

    public List<PatientReportAuditLogRowDto> getPatientAuditLog() {
        return repository.findAllByOrderByUploadedAtDesc().stream().map(r -> toAuditRow(r, null)).toList();
    }

    /**
     * Filtered to uploads for patients who have a resolvable treating
     * doctor (via their most recent Appointment) - there's no per-upload
     * doctor tag in this schema, so a row with no such Appointment has
     * nothing genuine to show here and is left out rather than showing a
     * fabricated or blank Doctor Name.
     */
    public List<PatientReportAuditLogRowDto> getDoctorAuditLog() {
        return repository.findAllByOrderByUploadedAtDesc().stream()
                .map(r -> toAuditRow(r, resolveDoctorName(r.getPatient())))
                .filter(row -> row.doctorName() != null)
                .toList();
    }

    private String resolveDoctorName(Patient patient) {
        return appointmentRepository
                .findFirstByPatientIdOrderByAppointmentDateDescSlotTimeDesc(patient.getId())
                .map(appointment -> appointment.getConsultant().getName())
                .orElse(null);
    }

    private PatientReportAuditLogRowDto toAuditRow(PatientReport report, String doctorName) {
        Patient patient = report.getPatient();
        return new PatientReportAuditLogRowDto(
                report.getId(),
                UPLOAD_OPERATION,
                patient.getRegistrationNumber(),
                displayName(patient),
                doctorName,
                report.getFilePath(),
                report.getOriginalFileName(),
                fileType(report.getOriginalFileName()),
                report.getUploadedAt(),
                report.getUploadedBy());
    }

    private String displayName(Patient patient) {
        return (patient.getFirstName() + " " + (patient.getLastName() != null ? patient.getLastName() : "")).trim();
    }

    private String currentUsername() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }
}
