-- General community posts are separated from laboratory reviews.
CREATE TABLE community_post (
    id BIGINT AUTO_INCREMENT,
    author_id BIGINT NOT NULL,
    category VARCHAR(20) NOT NULL,
    title VARCHAR(100) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    view_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT pk_community_post PRIMARY KEY (id),
    CONSTRAINT fk_community_post_author
        FOREIGN KEY (author_id) REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT ck_community_post_category
        CHECK (category IN ('FREE', 'QUESTION')),
    CONSTRAINT ck_community_post_view_count
        CHECK (view_count >= 0)
);

CREATE INDEX idx_community_post_active_created
    ON community_post (deleted_at, created_at DESC, id DESC);
CREATE INDEX idx_community_post_category_active_created
    ON community_post (category, deleted_at, created_at DESC, id DESC);
CREATE INDEX idx_community_post_author_active_created
    ON community_post (author_id, deleted_at, created_at DESC, id DESC);

CREATE TABLE community_comment (
    id BIGINT AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    content VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT pk_community_comment PRIMARY KEY (id),
    CONSTRAINT fk_community_comment_post
        FOREIGN KEY (post_id) REFERENCES community_post(id) ON DELETE CASCADE,
    CONSTRAINT fk_community_comment_author
        FOREIGN KEY (author_id) REFERENCES app_user(id) ON DELETE RESTRICT
);

CREATE INDEX idx_community_comment_post_active_created
    ON community_comment (post_id, deleted_at, created_at, id);
CREATE INDEX idx_community_comment_author_active
    ON community_comment (author_id, deleted_at);

CREATE TABLE community_post_like (
    user_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_community_post_like PRIMARY KEY (user_id, post_id),
    CONSTRAINT fk_community_post_like_user
        FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_community_post_like_post
        FOREIGN KEY (post_id) REFERENCES community_post(id) ON DELETE CASCADE
);

CREATE INDEX idx_community_post_like_post ON community_post_like (post_id);

CREATE TABLE community_post_bookmark (
    user_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_community_post_bookmark PRIMARY KEY (user_id, post_id),
    CONSTRAINT fk_community_post_bookmark_user
        FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_community_post_bookmark_post
        FOREIGN KEY (post_id) REFERENCES community_post(id) ON DELETE CASCADE
);

CREATE INDEX idx_community_post_bookmark_post ON community_post_bookmark (post_id);
