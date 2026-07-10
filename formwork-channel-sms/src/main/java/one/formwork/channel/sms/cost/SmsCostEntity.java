package one.formwork.channel.sms.cost;

import jakarta.persistence.*;
import one.formwork.base.tenant.filter.TenantScopedEntity;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "sms_cost_record")
public class SmsCostEntity extends TenantScopedEntity {

    @Column(name = "message_id", length = 200)
    private String messageId;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(name = "recipient", nullable = false, length = 30)
    private String recipient;

    @Column(name = "segment_count", nullable = false)
    private int segmentCount;

    @Column(name = "cost_per_segment", nullable = false, precision = 10, scale = 6)
    private BigDecimal costPerSegment;

    @Column(name = "total_cost", nullable = false, precision = 10, scale = 6)
    private BigDecimal totalCost;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "country_code", length = 5)
    private String countryCode;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Override
    @PrePersist
    protected void onCreate() {
        super.onCreate();
        if (sentAt == null) sentAt = Instant.now();
    }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public int getSegmentCount() { return segmentCount; }
    public void setSegmentCount(int segmentCount) { this.segmentCount = segmentCount; }
    public BigDecimal getCostPerSegment() { return costPerSegment; }
    public void setCostPerSegment(BigDecimal costPerSegment) { this.costPerSegment = costPerSegment; }
    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
}
