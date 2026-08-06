package com.pms.contact;

import com.pms.tenant.entity.ContactEnquiry;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * Sends the two contact-form emails. Templates are plain HTML resource
 * files with {{token}} placeholders (see src/main/resources/templates/email)
 * rather than a templating engine - Thymeleaf isn't already on the
 * classpath, and pulling it in for two static emails isn't justified.
 *
 * Both send methods are called from ContactEnquiryService AFTER the enquiry
 * is already committed to the database - deliberately not @Transactional
 * itself, and called as a separate bean method (not self-invoked) so @Async
 * proxying works correctly. A failed send is logged, never rethrown -
 * there's no caller left to catch it once the request has already
 * succeeded from the visitor's point of view.
 */
@Service
public class ContactMailService {

    private static final Logger log = LoggerFactory.getLogger(ContactMailService.class);
    private static final DateTimeFormatter SUBMITTED_AT_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm 'UTC'").withZone(ZoneId.of("UTC"));

    private final JavaMailSender mailSender;
    private final String fromEmail;
    private final String recipientEmail;
    private final String ccEmail;
    private final String notificationTemplate;
    private final String acknowledgementTemplate;

    public ContactMailService(
            JavaMailSender mailSender,
            @Value("${app.contact.from-email}") String fromEmail,
            @Value("${app.contact.recipient-email}") String recipientEmail,
            @Value("${app.contact.cc-email}") String ccEmail) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
        this.recipientEmail = recipientEmail;
        this.ccEmail = ccEmail;
        this.notificationTemplate = readTemplate("templates/email/contact-notification.html");
        this.acknowledgementTemplate = readTemplate("templates/email/contact-acknowledgement.html");
    }

    @Async
    public void sendEnquiryEmails(ContactEnquiry enquiry) {
        sendNotification(enquiry);
        sendAcknowledgement(enquiry);
    }

    private void sendNotification(ContactEnquiry enquiry) {
        try {
            String html = notificationTemplate
                    .replace("{{name}}", escape(enquiry.getName()))
                    .replace("{{email}}", escape(enquiry.getEmail()))
                    .replace("{{hospitalName}}", escape(enquiry.getHospitalName()))
                    .replace(
                            "{{message}}",
                            escape(blankToPlaceholder(enquiry.getMessage(), "(No additional message)")))
                    .replace("{{submittedAt}}", SUBMITTED_AT_FORMAT.format(enquiry.getCreatedAt()))
                    .replace("{{ipAddress}}", escape(enquiry.getIpAddress()))
                    .replace("{{userAgent}}", escape(blankToPlaceholder(enquiry.getUserAgent(), "(unknown)")));

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(fromEmail);
            helper.setTo(recipientEmail);
            helper.setCc(ccEmail);
            helper.setSubject("New Demo Request - " + enquiry.getHospitalName());
            helper.setText(html, true);
            mailSender.send(message);
        } catch (Exception ex) {
            log.error("Failed to send contact notification email for enquiry id={}", enquiry.getId(), ex);
        }
    }

    private void sendAcknowledgement(ContactEnquiry enquiry) {
        try {
            String html = acknowledgementTemplate
                    .replace("{{name}}", escape(enquiry.getName()))
                    .replace("{{hospitalName}}", escape(enquiry.getHospitalName()));

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(fromEmail);
            helper.setTo(enquiry.getEmail());
            helper.setSubject("We've received your request - CareOwn HMS");
            helper.setText(html, true);
            mailSender.send(message);
        } catch (Exception ex) {
            log.error("Failed to send contact acknowledgement email for enquiry id={}", enquiry.getId(), ex);
        }
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value, StandardCharsets.UTF_8.name());
    }

    private static String blankToPlaceholder(String value, String placeholder) {
        return (value == null || value.isBlank()) ? placeholder : value;
    }

    private static String readTemplate(String classpathLocation) {
        try {
            return new ClassPathResource(classpathLocation).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Missing email template: " + classpathLocation, ex);
        }
    }
}
