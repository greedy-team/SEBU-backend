package com.sebu.backend.auth.token;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenGeneratorTest {
    private final RefreshTokenGenerator generator = new RefreshTokenGenerator();

    @Test
    void generatesIndependent256BitTokensAndSha256Hashes() {
        RefreshTokenGenerator.RefreshTokenMaterial first = generator.generate();
        RefreshTokenGenerator.RefreshTokenMaterial second = generator.generate();

        assertThat(Base64.getUrlDecoder().decode(first.rawToken())).hasSize(32);
        assertThat(first.rawToken()).isNotEqualTo(second.rawToken());
        assertThat(first.tokenHash()).hasSize(64).isEqualTo(generator.hash(first.rawToken()));
        assertThat(first.tokenHash()).isNotEqualTo(first.rawToken());
        assertThat(first.toString()).doesNotContain(first.rawToken(), first.tokenHash());
    }
}
