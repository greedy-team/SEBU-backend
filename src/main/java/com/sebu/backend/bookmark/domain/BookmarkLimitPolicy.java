package com.sebu.backend.bookmark.domain;

import com.sebu.backend.bookmark.exception.BookmarkLimitExceededException;
import org.springframework.stereotype.Component;

@Component
public class BookmarkLimitPolicy {
    public static final long MAX_BOOKMARKS_PER_TYPE = 50;

    public void validateNewBookmark(BookmarkType type, long currentCount) {
        if (currentCount >= MAX_BOOKMARKS_PER_TYPE) {
            throw new BookmarkLimitExceededException(type, MAX_BOOKMARKS_PER_TYPE);
        }
    }
}
