create or replace function memor_chess.check_user_permission(
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
    current_user_id uuid := auth.uid();
begin
    select exists(select 1
                  from user_permissions up
                  where up.user_id = current_user_id
                    and up.permission::text = permission_input)
    into has_permission;

    return has_permission;
end;
$$;
