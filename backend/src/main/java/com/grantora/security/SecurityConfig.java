package com.grantora.security;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())

                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(contentType -> {}))

                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> response.setStatus(
                                HttpServletResponse.SC_UNAUTHORIZED)))

                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/").permitAll()
                        .requestMatchers(
                                "/api/auth/**",
                                "/error")
                        .permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/schemes", "/api/schemes/*").permitAll()

                        .requestMatchers("/api/users/**").hasRole("ADMIN")
                        .requestMatchers("/api/audit/**").hasRole("ADMIN")

                        .requestMatchers("/api/applications/me").hasRole("BENEFICIARY")
                        .requestMatchers("/api/applications/admin").hasRole("ADMIN")
                        .requestMatchers("/api/applications/disbursement/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/applications/*/submit")
                        .hasRole("BENEFICIARY")
                        .requestMatchers(HttpMethod.POST, "/api/applications/*/withdraw")
                        .hasRole("BENEFICIARY")
                        .requestMatchers(HttpMethod.DELETE, "/api/applications/*")
                        .hasRole("BENEFICIARY")
                        .requestMatchers(HttpMethod.POST, "/api/applications/*/decision")
                        .hasAnyRole("ADMIN", "OFFICER", "FIELD_OFFICER", "DISTRICT_OFFICER", "FINANCE_OFFICER")
                        .requestMatchers(HttpMethod.POST, "/api/applications/*/escalate")
                        .hasAnyRole("ADMIN", "OFFICER", "FIELD_OFFICER", "DISTRICT_OFFICER", "FINANCE_OFFICER")
                        .requestMatchers(HttpMethod.POST, "/api/applications/*/assignment")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/applications/*/documents")
                        .hasRole("BENEFICIARY")
                        .requestMatchers(HttpMethod.PATCH, "/api/applications/**")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/applications/**")
                        .hasRole("BENEFICIARY")
                        .requestMatchers(HttpMethod.PUT, "/api/applications/**")
                        .hasRole("BENEFICIARY")
                        .requestMatchers(HttpMethod.GET, "/api/applications")
                        .hasAnyRole("ADMIN", "OFFICER", "FIELD_OFFICER", "DISTRICT_OFFICER", "FINANCE_OFFICER")
                        .requestMatchers(HttpMethod.GET, "/api/applications/**")
                        .hasAnyRole("ADMIN", "OFFICER", "FIELD_OFFICER", "DISTRICT_OFFICER", "FINANCE_OFFICER", "BENEFICIARY")
                        .requestMatchers("/api/applications/**").hasRole("ADMIN")

                        .requestMatchers(HttpMethod.POST, "/api/schemes/*/check-eligibility", "/api/schemes/*/evaluate-eligibility")
                        .hasAnyRole("BENEFICIARY", "ADMIN", "OFFICER", "FIELD_OFFICER", "DISTRICT_OFFICER", "FINANCE_OFFICER")
                        .requestMatchers("/api/schemes/**").hasRole("ADMIN")

                        .requestMatchers(HttpMethod.GET, "/api/beneficiaries/me").hasRole("BENEFICIARY")
                        .requestMatchers(HttpMethod.POST, "/api/beneficiaries").hasRole("BENEFICIARY")
                        .requestMatchers(HttpMethod.PUT, "/api/beneficiaries/me").hasRole("BENEFICIARY")
                        .requestMatchers("/api/beneficiaries/**").hasRole("ADMIN")

                        .requestMatchers("/api/verification/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/workflow/deadlines").hasAnyRole("ADMIN", "OFFICER", "FIELD_OFFICER", "DISTRICT_OFFICER", "FINANCE_OFFICER")
                        .requestMatchers(HttpMethod.PUT, "/api/workflow/deadlines").hasRole("ADMIN")
                        .requestMatchers("/api/workflow/escalations/**").hasAnyRole("ADMIN", "OFFICER", "FIELD_OFFICER", "DISTRICT_OFFICER", "FINANCE_OFFICER")
                        .requestMatchers("/api/workflow/notifications/**").authenticated()

                        .requestMatchers(HttpMethod.GET, "/api/compliance/requirements").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/compliance/requirements").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/compliance/requirements/*/deactivate").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/compliance/mine").hasRole("BENEFICIARY")
                        .requestMatchers(HttpMethod.POST, "/api/compliance/submissions/*").hasRole("BENEFICIARY")
                        .requestMatchers(HttpMethod.GET, "/api/compliance/review-queue", "/api/compliance/non-compliances").hasAnyRole("ADMIN", "OFFICER")
                        .requestMatchers(HttpMethod.PATCH, "/api/compliance/submissions/*/review", "/api/compliance/non-compliances/*").hasAnyRole("ADMIN", "OFFICER")
                        .requestMatchers(HttpMethod.PATCH, "/api/compliance/submissions/*/waive").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/compliance/submissions/*/document").hasAnyRole("ADMIN", "OFFICER", "BENEFICIARY")
                        .requestMatchers("/api/compliance/**").authenticated()

                        .requestMatchers(HttpMethod.GET, "/api/fund-analytics/**").hasAnyRole("ADMIN", "FINANCE_OFFICER")
                        .requestMatchers(HttpMethod.GET, "/api/reports/milestone-3/**").hasAnyRole("ADMIN", "FINANCE_OFFICER")
                        .requestMatchers(HttpMethod.GET, "/api/milestone-3/admin-dashboard").hasAnyRole("ADMIN", "FINANCE_OFFICER")
                        .requestMatchers(HttpMethod.GET, "/api/milestone-3/assigned-milestones").hasAnyRole("ADMIN", "OFFICER")
                        .requestMatchers("/api/milestone-3/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/utilization/mine").hasRole("BENEFICIARY")
                        .requestMatchers(HttpMethod.POST, "/api/utilization").hasRole("BENEFICIARY")
                        .requestMatchers(HttpMethod.PUT, "/api/utilization/*").hasRole("BENEFICIARY")
                        .requestMatchers(HttpMethod.GET, "/api/utilization/review-queue").hasAnyRole("ADMIN", "OFFICER")
                        .requestMatchers(HttpMethod.PATCH, "/api/utilization/*/review").hasAnyRole("ADMIN", "OFFICER")
                        .requestMatchers("/api/utilization/**").authenticated()

                        .requestMatchers(HttpMethod.POST, "/api/disbursements/plans").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/disbursements/milestones/*/compliance").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/disbursements/plans/*/deactivate").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/disbursements/plans/mine").hasRole("BENEFICIARY")
                        .requestMatchers(HttpMethod.GET, "/api/disbursements/plans/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/disbursements/compliance/*/document").hasAnyRole("ADMIN", "OFFICER", "BENEFICIARY")
                        .requestMatchers(HttpMethod.GET, "/api/disbursements/compliances", "/api/disbursements/milestones", "/api/disbursements/milestones/*/compliances").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/disbursements/installments/*/disburse").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/disbursements/reminders/config").hasRole("ADMIN")
                        .requestMatchers("/api/disbursements/eligible-applications").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/disbursements/fund-utilization").hasAnyRole("ADMIN", "OFFICER")
                        .requestMatchers(HttpMethod.GET, "/api/disbursements/regional-analytics").hasAnyRole("ADMIN", "OFFICER")
                        .requestMatchers(HttpMethod.PATCH, "/api/disbursements/milestones/*/progress").hasAnyRole("ADMIN", "OFFICER")
                        .requestMatchers(HttpMethod.POST, "/api/disbursements/compliance/*/verify").hasAnyRole("ADMIN", "OFFICER")
                        .requestMatchers(HttpMethod.GET, "/api/disbursements/**").hasAnyRole("ADMIN", "OFFICER")
                        .requestMatchers("/api/disbursements/**").authenticated()

                        .anyRequest().authenticated())

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(
                List.of("http://localhost:5173"));

        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));

        configuration.setAllowedHeaders(
                List.of("Authorization", "Content-Type"));

        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {

        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);

        registration.setEnabled(false);

        return registration;
    }
}
