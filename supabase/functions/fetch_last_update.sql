create or replace function fetch_last_update(
    user_id_input uuid
)
    returns timestamp with time zone
    language plpgsql
    set search_path to public
as
$$
declare
    last_move_update     timestamp with time zone;
    last_position_update timestamp with time zone;
    latest_update        timestamp with time zone;
begin
    -- Get the latest update from user_moves
    select max(updated_at)
    into last_move_update
    from user_moves
    where user_id = user_id_input;

    -- Get the latest update from user_positions
    select max(updated_at)
    into last_position_update
    from user_positions
    where user_id = user_id_input;

    -- Return the latest update between the two tables
    if last_move_update is not null and last_position_update is not null then
        latest_update := greatest(last_move_update, last_position_update);
    elsif last_move_update is not null then
        latest_update := last_move_update;
    elsif last_position_update is not null then
        latest_update := last_position_update;
    else
        latest_update := null;
    end if;

    return latest_update;
end;
$$;
