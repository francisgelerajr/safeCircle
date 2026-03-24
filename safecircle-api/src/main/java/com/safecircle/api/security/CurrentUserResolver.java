package com.safecircle.api.security;

import com.safecircle.api.exception.UnauthorizedException;
import com.safecircle.api.repository.UserRepository;
import com.safecircle.common.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * A helper component that converts the raw cognitoSub string in the SecurityContext
 * into a fully-loaded User entity from the database.
 *
 * WITHOUT this helper, every service method would need to repeat:
 *   String sub = SecurityContextHolder.getContext().getAuthentication().getPrincipal()
 *   User user = userRepository.findByCognitoSub(sub).orElseThrow(...)
 *
 * WITH this helper, service methods call:
 *   User user = currentUserResolver.getCurrentUser();
 *
 * It's a small abstraction but it removes a lot of repetition and makes
 * service methods cleaner and easier to test.
 *
 * @Component marks this as a Spring-managed bean — Spring creates one instance
 * and injects it wherever it's needed via @RequiredArgsConstructor.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserResolver {

    private final UserRepository userRepository;

    /**
     * Returns the fully-loaded User entity for the currently authenticated caller.
     * Throws UnauthorizedException (→ 401) if no authenticated user is present.
     * Throws UnauthorizedException (→ 401) if the cognitoSub doesn't match any user
     * (shouldn't happen in normal flow but protects against race conditions).
     */
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("No authenticated user found in security context");
        }

        String cognitoSub = (String) authentication.getPrincipal();

        return userRepository.findByCognitoSub(cognitoSub)
            .orElseThrow(() ->
                new UnauthorizedException("No user found for cognitoSub: " + cognitoSub)
            );
    }

    /**
     * Returns just the cognitoSub string without a database lookup.
     * Use this when you only need the ID, not the full User object.
     */
    public String getCurrentCognitoSub() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new UnauthorizedException("No authenticated user");
        }
        return (String) authentication.getPrincipal();
    }
}