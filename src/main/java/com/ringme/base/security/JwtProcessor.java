package com.ringme.base.security;

import com.ringme.base.enums.TokenType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class JwtProcessor {

    /** Claim phân biệt loại token (access/refresh) để chống dùng refresh token gọi API. */
    public static final String TYPE_CLAIM = "type";

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final long accessTtl;
    private final long refreshTtl;

    public JwtProcessor(
            PrivateKey privateKey,
            PublicKey publicKey,
            long accessTtl,
            long refreshTtl
    ) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
    }

    public String generateAccessToken(
            String subject,
            Map<String, Object> claims
    ) {
        Instant now = Instant.now();

        // Đóng dấu type=access để filter phân biệt với refresh token.
        Map<String, Object> merged = new HashMap<>();
        if (claims != null) {
            merged.putAll(claims);
        }
        merged.put(TYPE_CLAIM, TokenType.ACCESS.getValue());

        return Jwts.builder()
                .subject(subject)
                .claims(merged)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTtl)))
                .signWith(privateKey, Jwts.SIG.RS512)
                .compact();
    }

    public String generateRefreshToken(String subject) {
        return generateRefreshToken(subject, null);
    }

    public String generateRefreshToken(String subject, Map<String, Object> claims) {
        Instant now = Instant.now();

        Map<String, Object> merged = new HashMap<>();
        if (claims != null) {
            merged.putAll(claims);
        }
        merged.put(TYPE_CLAIM, TokenType.REFRESH.getValue());

        return Jwts.builder()
                .subject(subject)
                .claims(merged)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(refreshTtl)))
                .signWith(privateKey, Jwts.SIG.RS512)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validate(String token) {
        try {
            Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token);

            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String getSubject(String token) {
        return parseClaims(token).getSubject();
    }
}