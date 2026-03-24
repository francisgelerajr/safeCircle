package com.safecircle.api.repository;

import com.safecircle.common.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * SPRING DATA JPA REPOSITORY CONCEPT:
 *
 * This interface has NO implementation class. Spring Data JPA generates the
 * implementation automatically at startup by reading the method names.
 *
 * JpaRepository<User, UUID> gives you these for free, no code required:
 *   save(user)            → INSERT or UPDATE
 *   findById(id)          → SELECT WHERE id = ?
 *   findAll()             → SELECT *
 *   delete(user)          → DELETE
 *   count()               → SELECT COUNT(*)
 *   existsById(id)        → SELECT EXISTS(...)
 *
 * For custom queries, Spring Data parses the METHOD NAME:
 *   findByEmail(email)    → SELECT * FROM users WHERE email = ?
 *   findByCognitoSub(sub) → SELECT * FROM users WHERE cognito_sub = ?
 *
 * This is called "query derivation" — the method name IS the query.
 * For complex queries, you can use @Query("SELECT u FROM User u WHERE ...")
 * with JPQL (Java Persistence Query Language), which looks like SQL but
 * operates on your Java class names and field names, not table names.
 *
 * WHY Optional<User> instead of User?
 * Optional forces the caller to handle the "not found" case explicitly.
 * If findByEmail returned User directly and nothing was found, it would return
 * null — and the caller might forget to null-check, causing a NullPointerException.
 * Optional.orElseThrow() makes the intent explicit and the code safer.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByCognitoSub(String cognitoSub);

    Optional<User> findByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);
}