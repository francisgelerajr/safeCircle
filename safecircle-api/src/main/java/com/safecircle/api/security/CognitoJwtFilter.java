package com.safecircle.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT VALIDATION CONCEPT:
 *
 * Every protected request from the mobile app includes a header:
 *   Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
 *
 * A JWT has three parts separated by dots:
 *   HEADER.PAYLOAD.SIGNATURE
 *
 * The header says which signing algorithm was used (RS256 for Cognito).
 * The payload contains "claims" — JSON key-value pairs like:
 *   { "sub": "abc-123", "email": "juan@example.com", "exp": 1711267200 }
 * The signature proves the token was signed by Cognito's private key.
 *
 * To verify a token we:
 *   1. Fetch Cognito's public keys from the JWKS endpoint
 *   2. Use those public keys to verify the signature
 *   3. Check the token hasn't expired (exp claim)
 *   4. Extract the "sub" claim (Cognito's user ID)
 *
 * If any step fails, we return 401 Unauthorized. If all pass, we put the
 * user identity into the SecurityContext — Spring Security then knows who
 * this request is from for the rest of the request lifecycle.
 *
 * OncePerRequestFilter CONCEPT:
 * Guarantees this filter runs exactly once per HTTP request (not per forward/include).
 * It's the correct base class for security filters in Spring Boot.
 */
@Component
@Slf4j
public class CognitoJwtFilter extends OncePerRequestFilter {

    @Value("${safecircle.cognito.jwks-uri}")
    private String jwksUri;

    @Value("${safecircle.cognito.client-id}")
    private String clientId;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String token = extractBearerToken(request);

        if (token != null) {
            try {
                Claims claims = validateAndExtractClaims(token);
                String cognitoSub = claims.getSubject();

                /*
                 * SECURITY CONTEXT CONCEPT:
                 * SecurityContextHolder is a thread-local store — it holds
                 * the authenticated user for the current request thread.
                 * Setting authentication here tells Spring Security:
                 * "this request is authenticated as the user with this identity."
                 *
                 * The controller can then call SecurityContextHolder.getContext()
                 * .getAuthentication().getPrincipal() to get the cognitoSub.
                 * We wrap this in CurrentUserResolver to make it cleaner.
                 */
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                        cognitoSub,      // principal — who is this?
                        null,            // credentials — we don't store the token
                        Collections.emptyList()  // authorities/roles — add later for role-based access
                    );

                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (JwtException e) {
                // Token is invalid or expired — log at debug level (not warn/error)
                // because invalid tokens from expired sessions are expected and normal.
                log.debug("Invalid JWT token: {}", e.getMessage());
                // Don't return 401 here — let the authorization layer handle it.
                // If the endpoint is public, it proceeds. If protected, it will
                // fail at the .authenticated() check in SecurityConfig.
            }
        }

        // Always continue the filter chain — the authorization check happens downstream
        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the raw JWT string from the Authorization header.
     * Returns null if the header is absent or malformed.
     */
    private String extractBearerToken(HttpServletRequest request) {
        String headerValue = request.getHeader("Authorization");
        if (StringUtils.hasText(headerValue) && headerValue.startsWith("Bearer ")) {
            return headerValue.substring(7);  // strip "Bearer " prefix
        }
        return null;
    }

    /**
     * Validates the JWT signature using Cognito's public JWKS keys
     * and returns the claims payload.
     *
     * JWKS CONCEPT:
     * JWKS (JSON Web Key Set) is a public endpoint Cognito exposes:
     *   https://cognito-idp.ap-southeast-1.amazonaws.com/{poolId}/.well-known/jwks.json
     *
     * It contains the public keys Cognito uses to sign tokens.
     * JJWT fetches and caches these keys automatically when you provide the URI.
     * The private key (used to SIGN tokens) never leaves AWS — we only ever
     * see the public key (used to VERIFY signatures).
     */
    private Claims validateAndExtractClaims(String token) {
        return Jwts.parser()
            .keyLocator(new CognitoJwksKeyLocator(jwksUri))
            .requireAudience(clientId)   // ensures the token was issued FOR our app
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}