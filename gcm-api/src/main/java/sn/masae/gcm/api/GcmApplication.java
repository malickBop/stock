package sn.masae.gcm.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Point d'entrée de la plateforme GCM — Gestion de la Comptabilité des Matières du MASAE.
 */
@SpringBootApplication(scanBasePackages = "sn.masae.gcm")
@EntityScan("sn.masae.gcm.domain.entity")
@EnableJpaRepositories("sn.masae.gcm.domain.repository")
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
@ConfigurationPropertiesScan("sn.masae.gcm.api.config")
public class GcmApplication {

    public static void main(String[] args) {
        SpringApplication.run(GcmApplication.class, args);
    }
}
