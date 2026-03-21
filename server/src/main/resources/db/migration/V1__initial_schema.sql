CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE book (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    downloads INT NOT NULL DEFAULT 0
);

CREATE TABLE positions (
    id BIGSERIAL PRIMARY KEY,
    fen_representation TEXT NOT NULL UNIQUE
);

CREATE TABLE moves (
    id BIGSERIAL PRIMARY KEY,
    origin BIGINT NOT NULL REFERENCES positions(id),
    destination BIGINT NOT NULL REFERENCES positions(id),
    name TEXT NOT NULL,
    UNIQUE(origin, destination, name)
);

CREATE TABLE move_cross_book (
    move_id BIGINT NOT NULL REFERENCES moves(id),
    book_id BIGINT NOT NULL REFERENCES book(id) ON DELETE CASCADE,
    is_good BOOLEAN NOT NULL,
    PRIMARY KEY(move_id, book_id)
);

CREATE TABLE downloaded_books (
    user_id UUID NOT NULL REFERENCES app_user(id),
    book_id BIGINT NOT NULL REFERENCES book(id) ON DELETE CASCADE,
    PRIMARY KEY(user_id, book_id)
);
