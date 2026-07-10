package one.formwork.base.tenant.filter;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;

/**
 * Minimal harness stub of the platform's TenantScopedEntity (the real one lives in the
 * private formwork-base-tenant module). Only the members the sms module actually uses are
 * reproduced, so the cost package compiles and runs in CI without the private reactor.
 * Not part of the reviewed module.
 */
@MappedSuperclass
public abstract class TenantScopedEntity {

    @Id
    private UUID id;
    private UUID tenantId;

    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
}
