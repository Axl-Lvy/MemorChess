# Database Migrations

This directory contains Flyway database migration scripts for the MemorChess backend.

## Migration Naming Convention

Flyway migrations follow this naming pattern:
- `V{version}__{description}.sql` - Versioned migrations (e.g., `V1__initial_schema.sql`)
- `R__{description}.sql` - Repeatable migrations (run whenever they change)

## Current Schema

### Tables

1. **positions** - Stores unique chess positions using FEN notation
2. **moves** - Stores chess moves connecting two positions
3. **user_positions** - Tracks user-specific position data including training schedule
4. **user_moves** - Tracks user-specific move preferences and deletion status
5. **book** - Stores opening repertoire books
6. **move_cross_book** - Links moves to books they belong to
7. **downloaded_books** - Tracks book downloads by users
8. **user_permissions** - Manages user permissions like book creation rights
9. **users** - Reference table for user data (Supabase auth integration)

## Running Migrations

Migrations are automatically executed when the application starts via the `configureFlyway()` function.

## Creating New Migrations

1. Create a new SQL file in this directory following the naming convention
2. Increment the version number (e.g., `V2__add_new_table.sql`)
3. Write your migration SQL
4. Restart the application to apply the migration

## Notes

- All tables are created in the `memor_chess` schema
- The schema supports Supabase authentication via `auth.uid()`
- Indexes are created for optimal query performance
- Foreign keys use CASCADE deletion where appropriate

