update account
set email = renamed.new_email
from (values
    ('2024-001', 'student@northstar.edu', 'student@animoroll.edu'),
    ('2024-002', 'student2@northstar.edu', 'student2@animoroll.edu'),
    ('FAC-001', 'faculty@northstar.edu', 'faculty@animoroll.edu')
) as renamed(id, old_email, new_email)
where account.id = renamed.id and account.email = renamed.old_email;
