package com.safecircle.api.security;

import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.ProtectedHeader;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.Jwks;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.Key;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fetches and CACHES Cognito's public signing keys from the JWKS endpoint.
 *
 * CACHING CONCEPT:
 * Cognito's JWKS endpoint returns public keys. These keys rotate infrequently
 * (every ~1 year) but we don't want to make an HTTP request to AWS on EVERY
 * API call — that would add latency and create an outbound network dependency
 * on every single request.
 *
 * Instead, we cache the key set in memory using AtomicReference (thread-safe).
 * On the first request we fetch it. On subsequent requests we use the cached copy.
 *
 * If token validation fails with a key-not-found error, we refresh the cache
 * (in case Cognito rotated its keys) and try once more. This handles key rotation
 * transparently.
 *
 * LocatorAdapter<Key> is JJWT's interface for providing signing keys.
 * JJWT calls locate(header) with the JWT's header (which contains the key ID "kid").
 * We look up the matching key by kid from our cached key set.
 */
@Slf4j
public class CognitoJwksKeyLocator extends LocatorAdapter<Key> {

    private final String jwksUri;
    private final HttpClient httpClient;
    private final AtomicReference<JwkSet> cachedJwkSet = new AtomicReference<>();

    public CognitoJwksKeyLocator(String jwksUri) {
        this.jwksUri = jwksUri;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    @Override
    protected Key locate(ProtectedHeader header) {
        String kid = header.getKeyId();

        // Try cached keys first
        JwkSet jwkSet = cachedJwkSet.get();
        if (jwkSet == null) {
            jwkSet = fetchAndCacheJwkSet();
        }

        Key key = findKeyById(jwkSet, kid);

        // If key not found (possible rotation), refresh cache and try once more
        if (key == null) {
            log.info("Key {} not found in cache — refreshing JWKS from Cognito", kid);
            jwkSet = fetchAndCacheJwkSet();
            key = findKeyById(jwkSet, kid);
        }

        return key;
    }

    private JwkSet fetchAndCacheJwkSet() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(jwksUri))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();

            HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );

            JwkSet jwkSet = Jwks.setParser().build().parse(response.body());
            cachedJwkSet.set(jwkSet);
            log.debug("Refreshed JWKS cache from {}", jwksUri);
            return jwkSet;

        } catch (Exception e) {
            log.error("Failed to fetch JWKS from Cognito: {}", e.getMessage());
            throw new RuntimeException("Unable to fetch Cognito public keys", e);
        }
    }

    private Key findKeyById(JwkSet jwkSet, String kid) {
        return jwkSet.getKeys().stream()
            .filter(jwk -> kid.equals(jwk.getId()))
            .findFirst()
            .map(jwk -> jwk.toKey())
            .orElse(null);
    }
}