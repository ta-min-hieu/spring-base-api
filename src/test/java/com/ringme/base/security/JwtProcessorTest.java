package com.ringme.base.security;

import com.ringme.base.enums.TokenType;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test thuần cho JwtProcessor (không cần Spring context): tự sinh cặp khoá RSA và kiểm tra
 * ký/giải mã, claim type, hết hạn, và chữ ký sai.
 */
class JwtProcessorTest {

    private static KeyPair keyPair;
    private static KeyPair otherKeyPair;

    @BeforeAll
    static void genKeys() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keyPair = gen.generateKeyPair();
        otherKeyPair = gen.generateKeyPair();
    }

    private JwtProcessor processor(long accessTtl, long refreshTtl) {
        return new JwtProcessor(keyPair.getPrivate(), keyPair.getPublic(), accessTtl, refreshTtl);
    }

    @Test
    void accessToken_hasSubjectRolesAndAccessType() {
        JwtProcessor jwt = processor(900, 86400);
        String token = jwt.generateAccessToken("user01", Map.of("roles", List.of("USER", "ADMIN")));

        Claims claims = jwt.parseClaims(token);
        assertEquals("user01", claims.getSubject());
        assertEquals(TokenType.ACCESS.getValue(), claims.get(JwtProcessor.TYPE_CLAIM, String.class));
        assertEquals(List.of("USER", "ADMIN"), claims.get("roles"));
        assertTrue(jwt.validate(token));
    }

    @Test
    void refreshToken_hasRefreshType() {
        JwtProcessor jwt = processor(900, 86400);
        String token = jwt.generateRefreshToken("user01", Map.of("roles", List.of("USER")));

        Claims claims = jwt.parseClaims(token);
        assertEquals(TokenType.REFRESH.getValue(), claims.get(JwtProcessor.TYPE_CLAIM, String.class));
    }

    @Test
    void validate_returnsFalseForExpiredToken() {
        // accessTtl âm -> token hết hạn ngay khi tạo.
        JwtProcessor jwt = processor(-10, 86400);
        String token = jwt.generateAccessToken("user01", Map.of());
        assertFalse(jwt.validate(token));
    }

    @Test
    void validate_returnsFalseForWrongSignature() {
        // Token ký bằng khoá khác -> public key của processor không verify được.
        JwtProcessor signer = new JwtProcessor(
                otherKeyPair.getPrivate(), otherKeyPair.getPublic(), 900, 86400);
        String foreignToken = signer.generateAccessToken("attacker", Map.of());

        JwtProcessor verifier = processor(900, 86400);
        assertFalse(verifier.validate(foreignToken));
    }

    @Test
    void validate_returnsFalseForGarbage() {
        assertFalse(processor(900, 86400).validate("not-a-jwt"));
    }
}
