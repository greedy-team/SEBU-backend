package com.sebu.backend.account.adapter;

import com.sebu.backend.account.port.ActivityCleanupPort;
import com.sebu.backend.bookmark.repository.BookmarkRepository;
import com.sebu.backend.community.bookmark.repository.CommunityPostBookmarkRepository;
import com.sebu.backend.community.like.repository.CommunityPostLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountActivityCleanupAdapter implements ActivityCleanupPort {
    private final BookmarkRepository bookmarkRepository;
    private final CommunityPostBookmarkRepository postBookmarkRepository;
    private final CommunityPostLikeRepository postLikeRepository;

    @Override
    public void deleteAllByUserId(Long userId) {
        bookmarkRepository.deleteAllByUserId(userId);
        postBookmarkRepository.deleteAllByUserId(userId);
        postLikeRepository.deleteAllByUserId(userId);
    }
}
