-- Assessments (assessment#2): one framework each, its catalogue version pinned (RNF05, RNF18).
create table assessment.assessment (
    id                  uuid primary key,
    tenant_id           uuid        not null,
    framework           text        not null check (framework ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    catalogue_version   integer     not null check (catalogue_version >= 1),
    max_level           text        not null check (max_level in ('BASIC', 'SUBSTANTIAL', 'HIGH')),
    nis2_classification text        not null
        check (nis2_classification in ('ESSENTIAL', 'IMPORTANT', 'NOT_COVERED', 'TO_BE_ASSESSED')),
    rules_version       text        not null check (char_length(rules_version) <= 100),
    -- What the organization declared on start (self-declared, checked by the consultant). Never updated.
    scope_input         jsonb       not null,
    status              text        not null check (status in ('DRAFT', 'SUBMITTED', 'EVALUATED', 'VALIDATED')),
    created_at          timestamptz not null,
    updated_at          timestamptz not null
);
create index assessment_tenant_idx on assessment.assessment (tenant_id);
-- At most one open assessment per framework; a validated one can be followed by a new one.
create unique index assessment_open_uq on assessment.assessment (tenant_id, framework) where status <> 'VALIDATED';

-- One current answer per question; its history is in audit.audit_event.
create table assessment.answer (
    id            uuid primary key,
    tenant_id     uuid        not null,
    assessment_id uuid        not null references assessment.assessment (id),
    question_id   text        not null check (char_length(question_id) <= 100),
    value         text        not null check (value in ('YES', 'PARTIAL', 'NO', 'UNKNOWN')),
    answered_at   timestamptz not null,
    constraint answer_question_uq unique (assessment_id, question_id)
);
create index answer_tenant_idx on assessment.answer (tenant_id);