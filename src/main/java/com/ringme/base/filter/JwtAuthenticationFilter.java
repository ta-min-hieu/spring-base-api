package com.ringme.base.filter;

import com.ringme.base.iam.security.PermissionEnrichmentService;
import com.ringme.base.service.JwtAuthenticationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtAuthenticationService jwtAuthenticationService;
    private final PermissionEnrichmentService permissionEnrichmentService;

    public JwtAuthenticationFilter(JwtAuthenticationService jwtAuthenticationService,
                                    PermissionEnrichmentService permissionEnrichmentService) {
        this.jwtAuthenticationService = jwtAuthenticationService;
        this.permissionEnrichmentService = permissionEnrichmentService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {

            String token = header.substring(7);

            UsernamePasswordAuthenticationToken auth = jwtAuthenticationService.authenticate(token);

            if (auth != null) {
                SecurityContextHolder.getContext().setAuthentication(permissionEnrichmentService.enrich(auth));
            }
        }

        filterChain.doFilter(request, response);
    }
}