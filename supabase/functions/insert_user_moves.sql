create or replace function insert_user_moves(
    user_id_input uuid,
    stored_moves jsonb
)
    returns void
    language plpgsql
    set search_path to public
as
$$

declare
    move_item      jsonb;
    origin_id      bigint;
    destination_id bigint;
    move_id_input  bigint;
begin
    for move_item in select * from jsonb_array_elements(stored_moves)
        loop
            -- 1. Ensure origin exists
            insert into positions(fen_representation)
            values (move_item ->> 'origin')
            on conflict(fen_representation) do nothing;

            select id
            into origin_id
            from positions
            where fen_representation = move_item ->> 'origin';

            -- 2. Ensure destination exists
            insert into positions(fen_representation)
            values (move_item ->> 'destination')
            on conflict(fen_representation) do nothing;

            select id
            into destination_id
            from positions
            where fen_representation = move_item ->> 'destination';

            -- 3. Ensure move exists
            insert into moves(origin, destination, name)
            values (origin_id, destination_id, move_item ->> 'move')
            on conflict(origin, destination, name) do nothing;

            select id
            into move_id_input
            from moves
            where origin = origin_id
              and destination = destination_id
              and name = move_item ->> 'move';

            -- 4. Insert user move
            insert into user_moves(move_id, user_id, is_good, is_deleted, updated_at)
            values (move_id_input,
                    user_id_input,
                    (move_item ->> 'isGood')::boolean,
                    (move_item ->> 'isDeleted')::boolean,
                    (move_item ->> 'updatedAt')::timestamp)
            on conflict(user_id, move_id)
                do update set is_good    = excluded.is_good,
                              is_deleted = excluded.is_deleted,
                              updated_at = excluded.updated_at;
        end loop;
end;
$$;