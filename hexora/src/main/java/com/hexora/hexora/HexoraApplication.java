package com.hexora.hexora;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import com.hexora.hexora.security.TinyHatCapabilityProperties;
import com.hexora.hexora.security.TinyHatSecurityProperties;

@SpringBootApplication(scanBasePackages = {"com.hexora.hexora", "com.hexora.network"})
@EntityScan(basePackages = {"com.hexora.hexora", "com.hexora.network"})
@EnableJpaRepositories(basePackages = {"com.hexora.hexora", "com.hexora.network"})
@EnableConfigurationProperties({TinyHatSecurityProperties.class, TinyHatCapabilityProperties.class})
public class HexoraApplication {

	public static void main(String[] args) {
		SpringApplication.run(HexoraApplication.class, args);
	}

}
