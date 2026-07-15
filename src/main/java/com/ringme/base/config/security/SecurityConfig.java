package com.ringme.base.config.security;

import com.ringme.base.iam.config.RbacAccessDeniedHandler;
import com.ringme.base.iam.filter.DynamicPermissionFilter;
import com.ringme.base.filter.JwtAuthenticationFilter;
import com.ringme.base.security.JwtProcessor;
import com.ringme.base.security.RsaKeyLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.List;

@RequiredArgsConstructor
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(jsr250Enabled = true)
public class SecurityConfig {
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RbacAccessDeniedHandler rbacAccessDeniedHandler;
    // DynamicPermissionFilter chỉ tồn tại khi app.rbac.enabled=true (@ConditionalOnProperty) -> phải
    // dùng ObjectProvider thay vì inject thẳng, nếu không context sẽ lỗi NoSuchBeanDefinition khi tắt RBAC.
    private final ObjectProvider<DynamicPermissionFilter> dynamicPermissionFilterProvider;

    @Value("${app.jwt.public-key}")
    private String publicKeyPath;
    @Value("${app.jwt.private-key}")
    private String privateKeyPath;
    @Value("${app.jwt.access-ttl:900}")
    private Long accessTtl;
    @Value("${app.jwt.refresh-ttl:86400}")
    private Long refreshTtl;
    @Value("${app.cors.allowed-origins:*}")
    private String[] allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
                )
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers(
                            "/v1/auth/**",
                            "/v2/auth/**",
                            "/troubleshoot/**",
                            "/actuator/health/**",
                            "/v3/api-docs/**",
                            "/swagger-ui/**",
                            "/swagger-ui.html",
                            "/v1/core/bonus-turn"
                    ).permitAll();
                    authorize.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    authorize.anyRequest().authenticated();
                })
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(rbacAccessDeniedHandler)
                );
        http.addFilterBefore(jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class);
        // Gắn TƯỜNG MINH sau AuthorizationFilter (thay vì để Spring Boot tự đăng ký như 1 servlet
        // filter rời rạc) để đảm bảo AccessDeniedException của nó luôn được ExceptionTranslationFilter
        // của CHÍNH security chain này bắt và giao cho rbacAccessDeniedHandler (403), không lẫn với
        // authenticationEntryPoint (401). Không có gì để thêm khi app.rbac.enabled=false (bean rỗng).
        DynamicPermissionFilter dynamicPermissionFilter = dynamicPermissionFilterProvider.getIfAvailable();
        if (dynamicPermissionFilter != null) {
            http.addFilterAfter(dynamicPermissionFilter, AuthorizationFilter.class);
        }

        return http.build();
    }

    /**
     * DynamicPermissionFilter vẫn là {@code @Component} (implements Filter) nên Spring Boot sẽ TỰ
     * đăng ký thêm nó như 1 servlet filter độc lập (ngoài lần đã addFilterAfter ở trên) — request nào
     * cũng bị kiểm tra permission 2 LẦN. Tắt đường tự đăng ký này, chỉ giữ lại bản đã gắn tường minh
     * vào security chain. Chỉ tồn tại khi app.rbac.enabled=true (@ConditionalOnBean theo bean gốc).
     */
    @Bean
    @ConditionalOnBean(DynamicPermissionFilter.class)
    public FilterRegistrationBean<DynamicPermissionFilter> dynamicPermissionFilterRegistration(
            DynamicPermissionFilter dynamicPermissionFilter) {
        FilterRegistrationBean<DynamicPermissionFilter> registration =
                new FilterRegistrationBean<>(dynamicPermissionFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.asList(allowedOrigins);
        if (origins.contains("*")) {
            // allowedOriginPatterns hỗ trợ "*" đi kèm credentials, còn allowedOrigins thì không.
            config.setAllowedOriginPatterns(List.of("*"));
        } else {
            config.setAllowedOrigins(origins);
        }
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public JwtProcessor jwtProcessor() {
        PrivateKey privateKey = RsaKeyLoader.loadPrivateKey(privateKeyPath);
        PublicKey publicKey = RsaKeyLoader.loadPublicKey(publicKeyPath);

        return new JwtProcessor(
                privateKey,
                publicKey,
                accessTtl,
                refreshTtl
        );
    }
}
