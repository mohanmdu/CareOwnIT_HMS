package com.pms.contact;

import com.pms.tenant.entity.ContactEnquiry;
import com.pms.tenant.repository.ContactEnquiryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Master-bound (see com.pms.tenant.repository.ContactEnquiryRepository's own
 * doc comment, and SuperAdminLoginService for the same transactionManager
 * qualifier pattern this class copies).
 */
@Service
public class ContactEnquiryService {

    private final ContactEnquiryRepository repository;
    private final ContactRateLimiter rateLimiter;
    private final RecaptchaVerificationService recaptchaService;
    private final ContactMailService mailService;

    public ContactEnquiryService(
            ContactEnquiryRepository repository,
            ContactRateLimiter rateLimiter,
            RecaptchaVerificationService recaptchaService,
            ContactMailService mailService) {
        this.repository = repository;
        this.rateLimiter = rateLimiter;
        this.recaptchaService = recaptchaService;
        this.mailService = mailService;
    }

    @Transactional(transactionManager = "masterTransactionManager")
    public ContactEnquiry submit(ContactRequest request, String ipAddress, String userAgent) {
        rateLimiter.checkAndRecord(ipAddress);
        if (!recaptchaService.verify(request.recaptchaToken())) {
            throw new IllegalArgumentException("reCAPTCHA verification failed. Please try again.");
        }

        ContactEnquiry enquiry = new ContactEnquiry();
        enquiry.setName(request.name().trim());
        enquiry.setEmail(request.email().trim());
        enquiry.setHospitalName(request.hospitalName().trim());
        enquiry.setMessage(request.message() == null ? null : request.message().trim());
        enquiry.setIpAddress(ipAddress);
        enquiry.setUserAgent(userAgent);
        enquiry = repository.save(enquiry);

        // Fires on a separate thread (see ContactMailService's own doc comment) -
        // the enquiry is already safely stored by this point, so a slow/failed
        // SMTP send never affects the response the visitor already got.
        mailService.sendEnquiryEmails(enquiry);
        return enquiry;
    }
}
