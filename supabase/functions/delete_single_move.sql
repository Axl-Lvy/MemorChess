create or replace function delete_single_move(
    user_id_input uuid,
    origin_input text,
    move_input text
)
    returns boolean
    language plpgsql
    set search_path to public
as
$$
declare
    origin_position_id      bigint;
    destination_position_id bigint;
    move_input_id           bigint;
    destination_move_count  int;
begin
    -- Find the origin position
    select p.id
    into origin_position_id
    from positions p
    where p.fen_representation = origin_input;

    -- If origin position not found, return false
    if origin_position_id is null then
        return false;
    end if;

    -- Find the move and destination position
    select m.id, m.destination
    into move_input_id, destination_position_id
    from moves m
    where m.origin = origin_position_id
      and m.name = move_input;

    -- If move not found, return false
    if move_input_id is null then
        return false;
    end if;

    -- Mark the user_move as deleted
    update user_moves
    set is_deleted = true,
        updated_at = now()
    where user_id = user_id_input
      and move_id = move_input_id;

    -- Check if the destination position has any other non-deleted moves for this user
    select count(*)
    into destination_move_count
    from user_moves um
             join moves m on um.move_id = m.id
    where um.user_id = user_id_input
      and m.origin = destination_position_id
      and um.is_deleted = false;

    -- If destination has no other moves, delete the destination position
    if destination_move_count = 0 then
        select p.fen_representation
        from positions p
        where p.id = destination_position_id
        into origin_input; -- Reuse variable for destination FEN

        perform delete_single_position(user_id_input, origin_input);
    end if;

    return true;
end;
$$;
