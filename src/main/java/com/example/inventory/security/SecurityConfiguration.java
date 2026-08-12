package com.example.inventory.security;

import com.example.inventory.users.RoleName;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfiguration {

    private static final Set<String> ALLOWED_ROLES = EnumSet.allOf(RoleName.class).stream()
            .map(Enum::name)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProperties properties,
                                             SecurityErrorHandler errorHandler,
                                             JwtAuthenticationConverter jwtConverter,
                                             CorsConfigurationSource corsSource) throws Exception {
        http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                // Bearer access tokens and JSON refresh credentials are not ambient browser credentials.
                // If refresh tokens move to cookies, CSRF protection must be enabled and tested again.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsSource))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)))
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers(HttpMethod.POST,
                            "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout")
                            .permitAll();
                    if (properties.swaggerEnabled()) {
                        authorize.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                                .hasRole(RoleName.ADMIN.name());
                    } else {
                        authorize.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                                .denyAll();
                    }
                    authorize.requestMatchers("/api/v1/users", "/api/v1/users/**")
                            .hasRole(RoleName.ADMIN.name());
                    authorize.requestMatchers(HttpMethod.GET, "/api/products", "/api/products/*")
                            .authenticated();
                    authorize.requestMatchers(HttpMethod.POST, "/api/products")
                            .hasAnyRole(RoleName.ADMIN.name(), RoleName.INVENTORY_MANAGER.name());
                    authorize.requestMatchers(HttpMethod.PUT, "/api/products/*")
                            .hasAnyRole(RoleName.ADMIN.name(), RoleName.INVENTORY_MANAGER.name());
                    authorize.requestMatchers(HttpMethod.DELETE, "/api/products/*")
                            .hasAnyRole(RoleName.ADMIN.name(), RoleName.INVENTORY_MANAGER.name());
                    authorize.requestMatchers(HttpMethod.GET, "/api/inventory/*/movements")
                            .hasAnyRole(RoleName.ADMIN.name(), RoleName.INVENTORY_MANAGER.name());
                    authorize.requestMatchers(HttpMethod.GET, "/api/inventory/*")
                            .authenticated();
                    authorize.requestMatchers(HttpMethod.PATCH, "/api/inventory/*/adjustments")
                            .hasAnyRole(RoleName.ADMIN.name(), RoleName.INVENTORY_MANAGER.name());
                    authorize.requestMatchers("/api/orders", "/api/orders/**")
                            .hasAnyRole(RoleName.ADMIN.name(), RoleName.SALES.name());
                    authorize.anyRequest().authenticated();
                });
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    AuthenticationManager authenticationManager(InventoryUserDetailsService userDetailsService,
                                                PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    JwtEncoder jwtEncoder(SecurityProperties properties, ResourceLoader resourceLoader) {
        RSAPublicKey publicKey = readPublicKey(properties.jwt().publicKeyLocation(), resourceLoader);
        RSAPrivateKey privateKey = readPrivateKey(properties.jwt().privateKeyLocation(), resourceLoader);
        return NimbusJwtEncoder.withKeyPair(publicKey, privateKey)
                .algorithm(SignatureAlgorithm.RS256)
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(SecurityProperties properties, ResourceLoader resourceLoader, Clock clock) {
        RSAPublicKey publicKey = readPublicKey(properties.jwt().publicKeyLocation(), resourceLoader);
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        OAuth2TokenValidator<Jwt> defaults = JwtValidators.createDefaultWithIssuer(properties.jwt().issuer());
        OAuth2TokenValidator<Jwt> applicationClaims = token ->
                validateApplicationClaims(token, properties, clock.instant());
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(defaults, applicationClaims));
        return decoder;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("sub");
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    @Primary
    CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = properties.cors() == null || properties.cors().allowedOrigins() == null
                ? List.of()
                : properties.cors().allowedOrigins().stream().filter(origin -> !origin.isBlank()).toList();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setExposedHeaders(List.of("Location"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private static OAuth2TokenValidatorResult validateApplicationClaims(Jwt token,
                                                                         SecurityProperties properties,
                                                                         java.time.Instant now) {
        boolean validAudience = token.getAudience() != null
                && token.getAudience().contains(properties.jwt().audience());
        boolean validSubject = isUuid(token.getSubject());
        boolean validJti = isUuid(token.getId());
        java.time.Instant issuedAt = token.getIssuedAt();
        java.time.Instant expiresAt = token.getExpiresAt();
        boolean validIssuedAt = issuedAt != null && !issuedAt.isAfter(now.plusSeconds(60));
        boolean validLifetime = issuedAt != null && expiresAt != null
                && expiresAt.isAfter(issuedAt)
                && java.time.Duration.between(issuedAt, expiresAt)
                        .compareTo(properties.jwt().accessTokenTtl()) <= 0;
        Object rolesClaim = token.getClaims().get("roles");
        boolean validRoles = rolesClaim instanceof Collection<?> roles
                && !roles.isEmpty()
                && roles.stream().allMatch(role -> role instanceof String value && ALLOWED_ROLES.contains(value));
        if (validAudience && validSubject && validJti && validIssuedAt && validLifetime && validRoles) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Required JWT claims are invalid", null));
    }

    private static boolean isUuid(String value) {
        if (value == null) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static RSAPublicKey readPublicKey(String location, ResourceLoader loader) {
        Resource resource = requiredResource(location, "JWT public key", loader);
        try (InputStream input = resource.getInputStream()) {
            return (RSAPublicKey) RsaKeyConverters.x509().convert(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read JWT public key", exception);
        }
    }

    private static RSAPrivateKey readPrivateKey(String location, ResourceLoader loader) {
        Resource resource = requiredResource(location, "JWT private key", loader);
        try (InputStream input = resource.getInputStream()) {
            return (RSAPrivateKey) RsaKeyConverters.pkcs8().convert(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read JWT private key", exception);
        }
    }

    private static Resource requiredResource(String location, String description, ResourceLoader loader) {
        if (location == null || location.isBlank()) {
            throw new IllegalStateException(description + " location must be configured externally");
        }
        Resource resource = loader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException(description + " does not exist");
        }
        return resource;
    }
}
