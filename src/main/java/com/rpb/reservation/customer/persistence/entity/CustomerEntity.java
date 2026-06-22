package com.rpb.reservation.customer.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "customers")
public class CustomerEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "customer_code", nullable = false)
    private String customerCode;

    @Column(name = "customer_type", nullable = false)
    private String customerType;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "nickname")
    private String nickname;

    @Column(name = "phone_e164")
    private String phoneE164;

    @Column(name = "email")
    private String email;

    @Column(name = "lookup_note")
    private String lookupNote;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "merged_into_customer_id")
    private UUID mergedIntoCustomerId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    protected CustomerEntity() {
    }

    public static CustomerEntity of(
        UUID id,
        UUID tenantId,
        String customerCode,
        String customerType,
        String displayName,
        String nickname,
        String phoneE164,
        String email,
        String lookupNote,
        String status,
        UUID mergedIntoCustomerId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime deletedAt,
        Integer version
    ) {
        CustomerEntity entity = new CustomerEntity();
        entity.id = id;
        entity.tenantId = tenantId;
        entity.customerCode = customerCode;
        entity.customerType = customerType;
        entity.displayName = displayName;
        entity.nickname = nickname;
        entity.phoneE164 = phoneE164;
        entity.email = email;
        entity.lookupNote = lookupNote;
        entity.status = status;
        entity.mergedIntoCustomerId = mergedIntoCustomerId;
        entity.createdAt = createdAt;
        entity.updatedAt = updatedAt;
        entity.deletedAt = deletedAt;
        entity.version = version;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getCustomerCode() { return customerCode; }
    public String getCustomerType() { return customerType; }
    public String getDisplayName() { return displayName; }
    public String getNickname() { return nickname; }
    public String getPhoneE164() { return phoneE164; }
    public String getEmail() { return email; }
    public String getLookupNote() { return lookupNote; }
    public String getStatus() { return status; }
    public UUID getMergedIntoCustomerId() { return mergedIntoCustomerId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public Integer getVersion() { return version; }
}
