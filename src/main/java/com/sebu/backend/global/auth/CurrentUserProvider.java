package com.sebu.backend.global.auth;

import java.util.Optional;

public interface CurrentUserProvider {
    Optional<Long> currentUserId();
}
