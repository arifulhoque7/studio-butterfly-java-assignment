package one.formwork.channel.sms.cost;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * SK-11: Repository for SMS cost records.
 */
public interface SmsCostRepository extends JpaRepository<SmsCostEntity, UUID> {

    List<SmsCostEntity> findByTenantIdAndSentAtBetween(UUID tenantId, Instant from, Instant to);

    List<SmsCostEntity> findByTenantIdAndProvider(UUID tenantId, String provider);

    @Query("SELECT SUM(c.totalCost) FROM SmsCostEntity c WHERE c.tenantId = :tenantId AND c.sentAt BETWEEN :from AND :to")
    BigDecimal sumCostByTenantAndPeriod(UUID tenantId, Instant from, Instant to);

    @Query("SELECT c.provider, SUM(c.totalCost), COUNT(c) FROM SmsCostEntity c " +
           "WHERE c.tenantId = :tenantId AND c.sentAt BETWEEN :from AND :to GROUP BY c.provider")
    List<Object[]> costBreakdownByProvider(UUID tenantId, Instant from, Instant to);

    long countByTenantIdAndSentAtBetween(UUID tenantId, Instant from, Instant to);
}

