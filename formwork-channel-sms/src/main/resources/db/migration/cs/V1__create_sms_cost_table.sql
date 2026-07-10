-- V1: Create sms_cost_record table with TenantScopedEntity columns
CREATE TABLE IF NOT EXISTS sms_cost_record (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    org_path VARCHAR(512),
    version BIGINT DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    message_id VARCHAR(200),
    provider VARCHAR(50) NOT NULL,
    recipient VARCHAR(30) NOT NULL,
    segment_count INT NOT NULL,
    cost_per_segment NUMERIC(10, 6) NOT NULL,
    total_cost NUMERIC(10, 6) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    country_code VARCHAR(5),
    sent_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sms_cost_tenant ON sms_cost_record(tenant_id);
CREATE INDEX IF NOT EXISTS idx_sms_cost_sent ON sms_cost_record(sent_at);
