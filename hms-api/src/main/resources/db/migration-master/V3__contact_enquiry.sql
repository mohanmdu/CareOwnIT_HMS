-- "Request Demo" form submissions from the public marketing site
-- (careownitsolutions.com) - see com.pms.tenant.entity.ContactEnquiry.
-- Company-wide, non-clinical data, so it lives here rather than in any
-- tenant schema.
CREATE TABLE contact_enquiry (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    email VARCHAR(255) NOT NULL,
    hospital_name VARCHAR(200) NOT NULL,
    message TEXT NULL,
    status ENUM('NEW','CONTACTED','CLOSED') NOT NULL DEFAULT 'NEW',
    ip_address VARCHAR(45) NOT NULL,
    user_agent VARCHAR(500) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);
