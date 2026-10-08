package com.sebu.backend.global.auth;

import com.sebu.backend.user.domain.AppUser;
import com.sebu.backend.user.repository.AppUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityContextCurrentUserProviderTest {

    @Mock
    AppUserRepository appUserRepository;

    @Mock
    AppUser user;

    @InjectMocks
    SecurityContextCurrentUserProvider provider;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsAuthenticatedUserIdFromJwtSubject() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("17")
                .claim("role", "USER")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(1800))
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(
                        jwt,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );

        when(appUserRepository.findById(17L))
                .thenReturn(Optional.of(user));
        when(user.isDeleted()).thenReturn(false);
        when(user.getId()).thenReturn(17L);

        assertThat(provider.currentUserId()).contains(17L);
    }

    @Test
    void returnsEmptyForAnonymousRequest() {
        assertThat(provider.currentUserId()).isEmpty();
    }

    @Test
    void returnsEmptyForDeletedUser() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("17")
                .claim("role", "USER")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(1800))
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(
                        jwt,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );

        when(appUserRepository.findById(17L))
                .thenReturn(Optional.of(user));
        when(user.isDeleted()).thenReturn(true);

        assertThat(provider.currentUserId()).isEmpty();
    }
}
