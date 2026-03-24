package com.safecircle.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * The worker is a completely separate Spring Boot application.
 * It shares the same database as the API (reads/writes escalation records)
 * but has NO HTTP server — it only listens to SQS.
 *
 * spring.main.web-application-type=none in application.yml suppresses
 * the embedded Tomcat server from starting. The process stays alive
 * because the SQS listener runs on a background thread.
 *
 * SEPARATION CONCEPT (revisited):
 * Why not put the SQS listener inside safecircle-api?
 * Because notification sending is slow (external HTTP calls to FCM, Twilio).
 * If a Twilio call hangs for 30 seconds, it blocks an API thread that could
 * be handling heartbeat pings. By separating them, a slow notification
 * never affects API response times. Each scales independently too:
 * during a mass alert event you scale the worker, not the API.
 */
@SpringBootApplication
@EntityScan(basePackages = "com.safecircle.common.model")
@EnableJpaRepositories(basePackages = "com.safecircle.worker.repository")
public class SafeCircleWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SafeCircleWorkerApplication.class, args);
    }
}