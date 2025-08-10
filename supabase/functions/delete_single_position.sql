create or replace function delete_single_position(
    user_id_input uuid,
    fen_representation_input text
)
    returns boolean
    language plpgsql
    set search_path to public
as
$$
declare
    pos_record record;
begin
    -- Find the position for the user
    select up.position_id,
           p.fen_representation as fen
    into pos_record
    from user_positions up
             join positions p on up.position_id = p.id
    where up.user_id = user_id_input
      and p.fen_representation = fen_representation_input
      and not up.is_deleted;

    -- If position not found or already deleted, return false
    if pos_record is null then
        return false;
    end if;

    -- Mark the position as deleted for this user
    update user_positions
    set is_deleted = true,
        updated_at = now()
    where user_id = user_id_input
      and position_id = pos_record.position_id;

    -- Mark all moves associated with this position as deleted for this user
    update user_moves
    set is_deleted = true,
        updated_at = now()
    where user_id = user_id_input
      and move_id in (select m.id
                      from moves m
                      where m.origin = pos_record.position_id
                         or m.destination = pos_record.position_id);

    return true;
end;
$$;
