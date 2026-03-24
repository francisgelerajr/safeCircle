package com.safecircle.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * THE ENTRY POINT of the Spring Boot API application.
 *
 * @SpringBootApplication is a shortcut for three annotations:
 *   @Configuration       — this class can define Spring beans
 *   @EnableAutoConfiguration — Spring Boot auto-configures everything it finds
 *                             (DataSource from application.yml, Redis, etc.)
 *   @ComponentScan       — scans this package and sub-packages for @Component,
 *                          @Service, @Repository, @Controller, @RestController
 *
 * SPRING BOOT AUTO-CONFIGURATION CONCEPT:
 * When Spring Boot starts, it looks at what's on the classpath. It sees:
 *  - spring-boot-starter-web → auto-configures an embedded Tomcat server
 *  - spring-boot-starter-data-jpa → auto-configures a DataSource and EntityManager
 *  - spring-boot-starter-data-redis → auto-configures a Redis connection pool
 * You get all of this for free, configured from application.yml.
 * Without Spring Boot, you would write hundreds of lines of XML or Java config.
 *
 * @EntityScan tells JPA where to find @Entity classes — they live in common,
 * not in this module, so we have to tell Spring where to look.
 *
 * @EnableJpaRepositories similarly tells Spring where to find @Repository interfaces.
 *
 * @EnableScheduling activates the @Scheduled annotation on InactivityCheckerJob.
 * Without this annotation, @Scheduled methods silently do nothing.
 */
@SpringBootApplication
@EntityScan(basePackages = "com.safecircle.common.model")
@EnableJpaRepositories(basePackages = "com.safecircle.api.repository")
@EnableScheduling
public class SafeCircleApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SafeCircleApiApplication.class, args);
    }
}