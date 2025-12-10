create or replace function memor_chess.check_user_permission(
    user_id_input uuid,
    permission_input text
)
    returns boolean
    language plpgsql
    SECURITY DEFINER
    set search_path to memor_chess
as
$$
declare
    has_permission boolean;
begin
    select exists(
                   select 1
                   from user_permissions up
                   where up.user_id = user_id_input
               )
    into has_permission;

    return has_permission;
end;
$$;
