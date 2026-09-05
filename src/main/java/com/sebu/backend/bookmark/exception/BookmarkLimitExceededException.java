package com.sebu.backend.bookmark.exception;

import com.sebu.backend.bookmark.domain.BookmarkType;

public class BookmarkLimitExceededException extends RuntimeException {
    private final BookmarkType bookmarkType;
    private final long limit;

    public BookmarkLimitExceededException(BookmarkType bookmarkType, long limit) {
        super("BOOKMARK_LIMIT_EXCEEDED");
        this.bookmarkType = bookmarkType;
        this.limit = limit;
    }

    public BookmarkType bookmarkType() {
        return bookmarkType;
    }

    public long limit() {
        return limit;
    }

    public String userMessage() {
        String target = bookmarkType == BookmarkType.LABORATORY ? "연구실" : "게시물";
        return target + " 북마크는 최대 " + limit + "개까지 저장할 수 있습니다.";
    }
}
