create or replace function memor_chess.delete_all(
    user_id_input uuid,
    hard_from_input timestamp with time zone default null
)
    returns void
    language plpgsql
    set search_path to memor_chess
as
$$
begin
    -- If hard_from is provided, permanently delete records after that date
    if hard_from_input is not null then
        -- Hard delete user moves updated after the specified date
        delete
        from user_moves
        where user_id = user_id_input
          and updated_at > hard_from_input;

        -- Hard delete user positions updated after the specified date
        delete
        from user_positions
        where user_id = user_id_input
          and updated_at > hard_from_input;
    end if;

    -- Mark all user moves as deleted (soft delete)
    update user_moves
    set is_deleted = true,
        updated_at = now()
    where user_id = user_id_input;

    -- Mark all user positions as deleted (soft delete)
    update user_positions
    set is_deleted = true,
        updated_at = now()
    where user_id = user_id_input;

    return;
end;
$$;
