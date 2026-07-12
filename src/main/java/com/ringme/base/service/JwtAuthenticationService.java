package com.ringme.base.service;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

public interface JwtAuthenticationService {
    UsernamePasswordAuthenticationToken authenticate(String token);
}
