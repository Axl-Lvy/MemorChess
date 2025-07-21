create or replace function fetch_user_positions(
    user_id_input uuid
)
    returns jsonb
    language plpgsql
    set search_path to public
as
$$
declare
    result         jsonb := '[]'::jsonb;
    pos_record     record;
    previous_moves jsonb;
    next_moves     jsonb;
    linked_moves   jsonb;
begin
    for pos_record in
        select up.position_id,
               p.fen_representation as fen,
               up.last_training_date,
               up.next_training_date,
               up.updated_at,
               up.is_deleted,
               up.depth
        from "UserPosition" up
                 join "Position" p on up.position_id = p.id
        where up.user_id = user_id_input
        loop
            -- previousMoves
            select coalesce(jsonb_agg(jsonb_build_object(
                    'origin', po.fen_representation,
                    'destination', pos_record.fen,
                    'move', m.name,
                    'isGood', um.is_good,
                    'isDeleted', um.is_deleted,
                    'updatedAt', um.updated_at
                                      )), '[]'::jsonb)
            into previous_moves
            from "Move" m
                     join "Position" po on po.id = m.origin
                     join "UserMove" um on um.move_id = m.id
            where m.destination = pos_record.position_id
              and um.user_id = user_id_input;

            -- nextMoves
            select coalesce(jsonb_agg(jsonb_build_object(
                    'origin', pos_record.fen,
                    'destination', pd.fen_representation,
                    'move', m.name,
                    'isGood', um.is_good,
                    'isDeleted', um.is_deleted,
                    'updatedAt', um.updated_at
                                      )), '[]'::jsonb)
            into next_moves
            from "Move" m
                     join "Position" pd on pd.id = m.destination
                     join "UserMove" um on um.move_id = m.id
            where m.origin = pos_record.position_id
              and um.user_id = user_id_input;

            -- concat both into linkedMoves
            linked_moves := previous_moves || next_moves;

            -- build StoredNode JSON
            result := result || jsonb_build_object(
                    'positionIdentifier', pos_record.fen,
                    'linked_moves', linked_moves,
                    'last_training_date', pos_record.last_training_date,
                    'next_training_date', pos_record.next_training_date,
                    'updated_at', pos_record.updated_at,
                    'is_deleted', pos_record.is_deleted,
                    'depth', pos_record.depth
                                );
        end loop;

    return result;
end;
$$;