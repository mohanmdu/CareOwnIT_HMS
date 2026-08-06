package com.pms.tenant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A "Request Demo" submission from the public marketing site
 * (careownitsolutions.com, a separate Angular app with no database of its
 * own - see com.pms.contact.ContactController). Company-wide, non-clinical
 * data, so this lives in the master DB alongside Client/SuperAdminUser
 * rather than any tenant schema.
 */
@Entity
@Table(name = "contact_enquiry")
@Getter
@Setter
@NoArgsConstructor
public class ContactEnquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(name = "hospital_name", nullable = false, length = 200)
    private String hospitalName;

    // Explicit columnDefinition rather than @Lob - an unbounded @Lob String
    // defaults to JPA's 255-char @Column length, which Hibernate's MySQL
    // dialect resolves to TINYTEXT (length-threshold based LOB-type
    // inference), not the plain TEXT column the migration actually creates.
    @Column(columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContactEnquiryStatus status = ContactEnquiryStatus.NEW;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
