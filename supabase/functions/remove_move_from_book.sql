create or replace function memor_chess.remove_move_from_book(
    user_id_input uuid,
    book_id_input bigint,
    origin_input text,
    move_input text
)
    returns boolean
    language plpgsql
    set search_path to memor_chess
    SECURITY DEFINER
as
$$
declare
    has_permission     boolean;
    origin_position_id bigint;
    move_id_to_delete  bigint;
    has_been_deleted   boolean;
begin
    -- Check if user has BOOK_CREATION permission
    select check_user_permission(user_id_input, 'BOOK_CREATION')
    into has_permission;

    if not has_permission then
        raise exception 'User does not have BOOK_CREATION permission';
    end if;

    -- Get origin position id
    select id
    into origin_position_id
    from positions
    where fen_representation = origin_input;

    if origin_position_id is null then
        return false;
    end if;

    -- Find the move id
    select m.id
    into move_id_to_delete
    from moves m
    where m.origin = origin_position_id
      and m.name = move_input;

    if move_id_to_delete is null then
        return false;
    end if;

    -- Delete the link from move_cross_book
    WITH deleted AS (
        DELETE FROM move_cross_book
            WHERE move_id = move_id_to_delete
                AND book_id = book_id_input
            RETURNING 1)
    SELECT EXISTS (SELECT 1 FROM deleted)
    INTO has_been_deleted;

    RETURN has_been_deleted;
end;
$$;

