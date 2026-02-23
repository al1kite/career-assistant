CREATE TABLE job_postings (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    url             TEXT NOT NULL,
    company_name    VARCHAR(100),
    company_type    VARCHAR(20),
    job_description TEXT,
    requirements    TEXT,
    status          VARCHAR(20) DEFAULT 'FETCHED',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_url (url(500))
);

CREATE TABLE cover_letters (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_posting_id BIGINT NOT NULL,
    ai_model       VARCHAR(50),
    content        TEXT,
    version        INT DEFAULT 1,
    feedback       TEXT,
    created_at     DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (job_posting_id) REFERENCES job_postings(id)
);

CREATE TABLE user_experiences (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    category    VARCHAR(20),
    title       VARCHAR(200),
    description TEXT,
    skills      VARCHAR(500),
    period      VARCHAR(50)
);

CREATE TABLE pipeline_log (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id     BIGINT,
    stage      VARCHAR(20),
    status     VARCHAR(20),
    error_msg  TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (job_id) REFERENCES job_postings(id)
);
