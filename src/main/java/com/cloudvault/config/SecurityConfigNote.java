package com.cloudvault.config;

/**
 * Security configuration note for the Share feature.
 *
 * In your existing SecurityConfig (or SecurityFilterChain bean), add
 * a permit rule for the public share-download endpoint.
 *
 * The share token itself is the credential — no JWT/session needed for that path.
 *
 * ─── Spring Security 6 / Boot 3 style ────────────────────────────────────────
 *
 * @Bean
 * public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
 *     http
 *         .authorizeHttpRequests(auth -> auth
 *             // ↓ ADD THIS — allows unauthenticated access to share downloads
 *             .requestMatchers(HttpMethod.GET, "/api/share/**").permitAll()
 *             // All other share endpoints (create, revoke) still require login
 *             .requestMatchers("/api/share/**").authenticated()
 *             // Your existing rules:
 *             .requestMatchers("/api/files/**").authenticated()
 *             .anyRequest().authenticated()
 *         )
 *         // ... rest of your config (jwt filter, cors, etc.)
 *     ;
 *     return http.build();
 * }
 *
 * ─── Also enable @Scheduled if not already done ───────────────────────────────
 *
 * Add @EnableScheduling to your main application class or any @Configuration class:
 *
 * @SpringBootApplication
 * @EnableScheduling          // ← add this
 * public class CloudVaultApplication { ... }
 *
 */
public class SecurityConfigNote {
    // This file is documentation only. Copy the snippets above into your
    // actual SecurityConfig.java and main application class.
}