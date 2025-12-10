create or replace function memor_chess.delete_book(
    user_id_input uuid,
    book_id_input bigint
)
    returns boolean
    language plpgsql
    set search_path to memor_chess
    SECURITY DEFINER
as
$$
declare
    has_permission boolean;
begin
    -- Check if user has BOOK_CREATION permission
    select check_user_permission(user_id_input, 'BOOK_CREATION')
    into has_permission;

    if not has_permission then
        raise exception 'User does not have BOOK_CREATION permission';
    end if;

    -- Delete all move_cross_book entries for this book
    delete from move_cross_book
    where book_id = book_id_input;

    -- Delete the book
    delete from book
    where id = book_id_input;

    return true;
end;
$$;
