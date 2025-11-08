create or replace function memor_chess.insert_user_positions(
    user_id_input uuid,
    stored_nodes jsonb
)
    returns void
    language plpgsql
    set search_path to memor_chess
as
$$

declare
    node_item         jsonb;
    position_id_input bigint;
begin
    for node_item in select * from jsonb_array_elements(stored_nodes)
        loop
            -- 1. Ensure position exists
            insert into positions(fen_representation)
            values (node_item ->> 'positionIdentifier')
            on conflict(fen_representation) do nothing;

            select id
            into position_id_input
            from positions
            where fen_representation = node_item ->> 'positionIdentifier';

            -- 2. Upsert UserPosition
            insert into user_positions(user_id,
                                       position_id,
                                       depth,
                                       last_training_date,
                                       next_training_date,
                                       created_at,
                                       updated_at,
                                       is_deleted)
            values (user_id_input,
                    position_id_input,
                    (node_item ->> 'depth')::int,
                    (node_item ->> 'last_training_date')::date,
                    (node_item ->> 'next_training_date')::date,
                    now(),
                    (node_item ->> 'updated_at')::timestamp,
                    (node_item ->> 'is_deleted')::boolean)
            on conflict(user_id, position_id)
                do update set last_training_date = excluded.last_training_date,
                              next_training_date = excluded.next_training_date,
                              updated_at         = excluded.updated_at,
                              depth              = excluded.depth,
                              is_deleted         = excluded.is_deleted;

            -- 3. Insert previous and next moves
            perform insert_user_moves(
                    user_id_input,
                    to_jsonb(node_item -> 'linked_moves')
                    );
        end loop;
end;
$$;