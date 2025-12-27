-- Create ENUM types
CREATE TYPE permission AS ENUM ('BOOK_CREATION');

-- Users table (Supabase auth integration)
-- Note: This assumes Supabase auth.users table exists
-- We're just creating a reference table for our schema if needed
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email_verified BOOLEAN DEFAULT FALSE,
    verification_token VARCHAR(255),
    reset_token VARCHAR(255),
    reset_token_expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Positions table (stores FEN representations of chess positions)
CREATE TABLE positions (
    id BIGSERIAL PRIMARY KEY,
    fen_representation TEXT NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Create index on FEN for faster lookups
CREATE INDEX idx_positions_fen ON positions(fen_representation);

-- Moves table (stores chess moves between positions)
CREATE TABLE moves (
    id BIGSERIAL PRIMARY KEY,
    origin BIGINT NOT NULL REFERENCES positions(id) ON DELETE CASCADE,
    destination BIGINT NOT NULL REFERENCES positions(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(origin, destination, name)
);

-- Create indexes for moves
CREATE INDEX idx_moves_origin ON moves(origin);
CREATE INDEX idx_moves_destination ON moves(destination);

-- User positions table (tracks user-specific position data)
CREATE TABLE user_positions (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    position_id BIGINT NOT NULL REFERENCES positions(id) ON DELETE CASCADE,
    depth INT NOT NULL DEFAULT 0,
    last_training_date DATE,
    next_training_date DATE,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id, position_id)
);

-- Create indexes for user positions
CREATE INDEX idx_user_positions_user_id ON user_positions(user_id);
CREATE INDEX idx_user_positions_position_id ON user_positions(position_id);
CREATE INDEX idx_user_positions_next_training ON user_positions(next_training_date) WHERE NOT is_deleted;

-- User moves table (tracks user-specific move data)
CREATE TABLE user_moves (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    move_id BIGINT NOT NULL REFERENCES moves(id) ON DELETE CASCADE,
    is_good BOOLEAN DEFAULT TRUE,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id, move_id)
);

-- Create indexes for user moves
CREATE INDEX idx_user_moves_user_id ON user_moves(user_id);
CREATE INDEX idx_user_moves_move_id ON user_moves(move_id);

-- Books table (stores opening repertoire books)
CREATE TABLE book (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    downloads BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Create index on book name
CREATE INDEX idx_book_name ON book(name);
CREATE INDEX idx_book_downloads ON book(downloads DESC);

-- Move cross book table (many-to-many relationship between moves and books)
CREATE TABLE move_cross_book (
    id BIGSERIAL PRIMARY KEY,
    move_id BIGINT NOT NULL REFERENCES moves(id) ON DELETE CASCADE,
    book_id BIGINT NOT NULL REFERENCES book(id) ON DELETE CASCADE,
    is_good BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(move_id, book_id)
);

-- Create indexes for move_cross_book
CREATE INDEX idx_move_cross_book_move_id ON move_cross_book(move_id);
CREATE INDEX idx_move_cross_book_book_id ON move_cross_book(book_id);

-- Downloaded books table (tracks which users downloaded which books)
CREATE TABLE downloaded_books (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    book_id BIGINT NOT NULL REFERENCES book(id) ON DELETE CASCADE,
    downloaded_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id, book_id)
);

-- Create indexes for downloaded_books
CREATE INDEX idx_downloaded_books_user_id ON downloaded_books(user_id);
CREATE INDEX idx_downloaded_books_book_id ON downloaded_books(book_id);

-- User permissions table (manages user permissions)
CREATE TABLE user_permissions (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    permission permission NOT NULL,
    granted_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id, permission)
);

-- Create index on user_permissions
CREATE INDEX idx_user_permissions_user_id ON user_permissions(user_id);

-- Add comments for documentation
COMMENT ON TABLE positions IS 'Stores unique chess positions using FEN notation';
COMMENT ON TABLE moves IS 'Stores chess moves connecting two positions';
COMMENT ON TABLE user_positions IS 'Tracks user-specific position data including training schedule';
COMMENT ON TABLE user_moves IS 'Tracks user-specific move preferences and deletion status';
COMMENT ON TABLE book IS 'Stores opening repertoire books';
COMMENT ON TABLE move_cross_book IS 'Links moves to books they belong to';
COMMENT ON TABLE downloaded_books IS 'Tracks book downloads by users';
COMMENT ON TABLE user_permissions IS 'Manages user permissions like book creation rights';

