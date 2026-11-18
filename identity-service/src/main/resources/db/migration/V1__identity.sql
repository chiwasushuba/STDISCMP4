create table account (
    id varchar(40) primary key,
    email varchar(200) unique not null,
    display_name varchar(150) not null,
    role varchar(16) not null check (role in ('STUDENT', 'FACULTY')),
    password_hash varchar(100) not null
);
create table login_session (
    id uuid primary key,
    account_id varchar(40) not null references account(id),
    refresh_hash varchar(64) unique not null,
    expires_at timestamptz not null,
    revoked boolean not null default false
);
