-- Schema initialization for MelissaFieldstone75LLC investor portal

CREATE TABLE IF NOT EXISTS investors (
    investor_id         INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    first_name          VARCHAR(255) NOT NULL,
    last_name           VARCHAR(255),
    email               VARCHAR(255) NOT NULL,
    email_address_2     VARCHAR(255),
    phone               VARCHAR(50),
    mailing_address     VARCHAR(500),
    beneficiary_name    VARCHAR(255),
    beneficiary_phone   VARCHAR(50),
    number_of_shares    INTEGER,
    investment_source   VARCHAR(255),
    llc_name            VARCHAR(255),
    major_id            VARCHAR(100),
    company_share_holding NUMERIC(10, 4),
    update_date_time    TIMESTAMP
);

CREATE TABLE IF NOT EXISTS investor_credentials (
    credential_id       INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    investor_id         INTEGER NOT NULL UNIQUE REFERENCES investors(investor_id),
    username            VARCHAR(255) NOT NULL UNIQUE,
    password_hash       VARCHAR(255) NOT NULL,
    role                VARCHAR(50)  NOT NULL DEFAULT 'INVESTOR',
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    reset_token         VARCHAR(255),
    reset_token_expiry  TIMESTAMP,
    mfa_enabled            BOOLEAN      NOT NULL DEFAULT FALSE,
    mfa_email_enabled      BOOLEAN      NOT NULL DEFAULT FALSE,
    mfa_totp_enabled       BOOLEAN      NOT NULL DEFAULT FALSE,
    mfa_selected_method    VARCHAR(10),
    totp_secret            VARCHAR(64),
    mfa_otp_code           VARCHAR(6),
    mfa_otp_expiry         TIMESTAMP,
    mfa_pending_token      VARCHAR(255),
    mfa_pending_expiry     TIMESTAMP,
    created_at             TIMESTAMP    NOT NULL,
    updated_at             TIMESTAMP    NOT NULL
);

CREATE TABLE IF NOT EXISTS investor_login_logs (
    log_id              INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    investor_id         INTEGER REFERENCES investors(investor_id),
    login_timestamp     TIMESTAMP    NOT NULL,
    ip_address          VARCHAR(100),
    status              VARCHAR(20)  NOT NULL,
    failure_reason      VARCHAR(255),
    action              VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS admin_users (
    admin_id            INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                VARCHAR(255) NOT NULL,
    email               VARCHAR(255) NOT NULL UNIQUE,
    password_hash       VARCHAR(255) NOT NULL,
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    reset_token         VARCHAR(255),
    reset_token_expiry  TIMESTAMP,
    mfa_enabled            BOOLEAN      NOT NULL DEFAULT FALSE,
    mfa_email_enabled      BOOLEAN      NOT NULL DEFAULT FALSE,
    mfa_totp_enabled       BOOLEAN      NOT NULL DEFAULT FALSE,
    mfa_selected_method    VARCHAR(10),
    totp_secret            VARCHAR(64),
    mfa_otp_code           VARCHAR(6),
    mfa_otp_expiry         TIMESTAMP,
    mfa_pending_token      VARCHAR(255),
    mfa_pending_expiry     TIMESTAMP,
    created_at             TIMESTAMP    NOT NULL
);

CREATE TABLE IF NOT EXISTS investments (
    investment_id           INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                    VARCHAR(255) NOT NULL,
    start_date              DATE NOT NULL,
    anticipated_end_date    DATE,
    end_date                DATE
);

CREATE TABLE IF NOT EXISTS investor_investments (
    investment_id   INTEGER NOT NULL REFERENCES investments(investment_id),
    investor_id     INTEGER NOT NULL REFERENCES investors(investor_id),
    PRIMARY KEY (investment_id, investor_id)
);

CREATE TABLE IF NOT EXISTS password_history (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_type       VARCHAR(20)  NOT NULL,
    user_id         INTEGER      NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP    NOT NULL
);

-- Seed admin user (password: TestApp#38899)
INSERT INTO admin_users (name, email, password_hash, is_active, created_at)
VALUES ('Admin', 'vinaycse@gmail.com', '$2b$10$1NMz0JywJIrn.uPTS92Rd.mnQl1rkyUt93sf7eX5ffVuZOZ43vJXi', TRUE, NOW())
ON CONFLICT (email) DO NOTHING;

-- Seed investor (password: TestApp#38899)
INSERT INTO investors (first_name, last_name, email, update_date_time)
VALUES ('Vinay', 'Murakonda', 'vinayatp@gmail.com', NOW())
ON CONFLICT DO NOTHING;

INSERT INTO investor_credentials (investor_id, username, password_hash, role, is_active, created_at, updated_at)
SELECT investor_id, 'vinayatp@gmail.com', '$2a$10$mWKLAW/Q7JIV6TbDAw/JJOLITvZUj74sXbt73nO2EAxrGkJiU4QMG', 'INVESTOR', TRUE, NOW(), NOW()
FROM investors WHERE email = 'vinayatp@gmail.com'
ON CONFLICT (username) DO NOTHING;
