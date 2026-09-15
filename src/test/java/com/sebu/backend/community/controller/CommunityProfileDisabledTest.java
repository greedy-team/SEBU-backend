package com.sebu.backend.community.controller;

import com.sebu.backend.community.profile.controller.CommunityProfileController;
import com.sebu.backend.user.exception.UserNotFoundException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommunityProfileDisabledTest {
    @Test
    void publicProfileEndpointNeverQueriesOrReturnsAProfile() {
        var controller = new CommunityProfileController();
        assertThatThrownBy(() -> controller.findProfile(1L, 0, 10))
            .isInstanceOf(UserNotFoundException.class);
    }
}
