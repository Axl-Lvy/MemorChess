create or replace function fetch_single_position(
    user_id_input uuid,
    fen_representation_input text
)
    returns jsonb
    language plpgsql
    set search_path to public
as
$$
declare
    pos_record     record;
    previous_moves jsonb;
    next_moves     jsonb;
    linked_moves   jsonb;
begin
    select up.position_id,
           p.fen_representation as fen,
           up.last_training_date,
           up.next_training_date,
           up.updated_at,
           up.is_deleted,
           up.depth
    into pos_record
    from user_positions up
             join positions p on up.position_id = p.id
    where up.user_id = user_id_input
      and p.fen_representation = fen_representation_input
      and not up.is_deleted;

    if pos_record is null then
        return null;
    end if;

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
    from moves m
             join positions po on po.id = m.origin
             join user_moves um on um.move_id = m.id
    where m.destination = pos_record.position_id
      and um.user_id = user_id_input
      and not um.is_deleted;

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
    from moves m
             join positions pd on pd.id = m.destination
             join user_moves um on um.move_id = m.id
    where m.origin = pos_record.position_id
      and um.user_id = user_id_input
      and not um.is_deleted;

    -- combine moves
    linked_moves := previous_moves || next_moves;

    -- return final object
    return jsonb_build_object(
            'positionIdentifier', pos_record.fen,
            'linked_moves', linked_moves,
            'last_training_date', pos_record.last_training_date,
            'next_training_date', pos_record.next_training_date,
            'updated_at', pos_record.updated_at,
            'is_deleted', pos_record.is_deleted,
            'depth', pos_record.depth
           );
end;
$$;