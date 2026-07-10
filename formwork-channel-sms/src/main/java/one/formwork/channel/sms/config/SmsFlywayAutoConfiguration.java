package one.formwork.channel.sms.config;

import one.formwork.base.tenant.config.TenantBaseAutoConfiguration;
import one.formwork.base.tenant.flyway.FlywayModuleSupport;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

@AutoConfiguration(after = TenantBaseAutoConfiguration.class)
public class SmsFlywayAutoConfiguration {

    @Bean(name = "csFlyway", initMethod = "migrate")
    @ConditionalOnBean(DataSource.class)
    public Flyway csFlyway(DataSource dataSource) {
        return FlywayModuleSupport.create(dataSource, "cs");
    }
}
