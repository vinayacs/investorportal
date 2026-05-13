package com.melissafieldstone.portal.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class JwtUtilTest {

    // 32+ character secret required for HS256
    private static final String SECRET = "TestSecretKeyForJwtUtilTests12345678";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET);
    }

    @Test
    void generateToken_returns_non_null_token() {
        String token = jwtUtil.generateToken("user@example.com", "INVESTOR");
        assertThat(token).isNotBlank();
    }

    @Test
    void extractUsername_returns_correct_subject() {
        String token = jwtUtil.generateToken("user@example.com", "INVESTOR");
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("user@example.com");
    }

    @Test
    void extractRole_returns_correct_role() {
        String token = jwtUtil.generateToken("admin@example.com", "ADMIN");
        assertThat(jwtUtil.extractRole(token)).isEqualTo("ADMIN");
    }

    @Test
    void isTokenValid_returns_true_for_valid_token() {
        String token = jwtUtil.generateToken("user@example.com", "INVESTOR");
        assertThat(jwtUtil.isTokenValid(token)).isTrue();
    }

    @Test
    void isTokenValid_returns_false_for_tampered_token() {
        String token = jwtUtil.generateToken("user@example.com", "INVESTOR");
        String tampered = token.substring(0, token.length() - 4) + "xxxx";
        assertThat(jwtUtil.isTokenValid(tampered)).isFalse();
    }

    @Test
    void isTokenValid_returns_false_for_garbage_string() {
        assertThat(jwtUtil.isTokenValid("not.a.token")).isFalse();
    }

    @Test
    void investor_and_admin_tokens_have_different_roles() {
        String investorToken = jwtUtil.generateToken("jane@example.com", "INVESTOR");
        String adminToken = jwtUtil.generateToken("admin@example.com", "ADMIN");

        assertThat(jwtUtil.extractRole(investorToken)).isEqualTo("INVESTOR");
        assertThat(jwtUtil.extractRole(adminToken)).isEqualTo("ADMIN");
    }
}
