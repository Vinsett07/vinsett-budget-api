
create table command_execution (
    id uuid primary key,
    account_id uuid not null,
    request_key uuid not null,
    request_hash varchar(64) not null,
    source varchar(30) not null,
    status varchar(20) not null,
    input_text text,
    response_text text,
    error_code varchar(80),
    http_status integer not null,
    created_at timestamp with time zone not null,
    completed_at timestamp with time zone,
    constraint uq_command_request unique (account_id, request_key),
    constraint ck_command_status check (status in ('RUNNING','SUCCEEDED','FAILED'))
);
create table ledger_entry (
    id uuid primary key,
    account_id uuid not null,
    entry_type varchar(10) not null,
    category varchar(30) not null,
    amount numeric(15,2) not null,
    description varchar(200) not null,
    occurred_on date not null,
    created_at timestamp with time zone not null,
    constraint ck_entry_amount check (amount > 0),
    constraint ck_entry_type check (entry_type in ('INCOME','EXPENSE'))
);
create index ix_entry_account_date on ledger_entry(account_id, occurred_on, id);
create table budget_limit (
    id uuid primary key,
    account_id uuid not null,
    month_start date not null,
    category varchar(30) not null,
    amount numeric(15,2) not null,
    version bigint not null default 0,
    constraint uq_budget_month_category unique (account_id, month_start, category),
    constraint ck_budget_amount check (amount > 0)
);
create table command_action (
    id uuid primary key,
    account_id uuid not null,
    command_id uuid not null references command_execution(id),
    fingerprint varchar(64) not null,
    kind varchar(30) not null,
    resource_id uuid not null,
    message varchar(600) not null,
    created_at timestamp with time zone not null,
    constraint uq_action_fingerprint unique (command_id, fingerprint)
);
create index ix_action_command on command_action(account_id, command_id, created_at);
