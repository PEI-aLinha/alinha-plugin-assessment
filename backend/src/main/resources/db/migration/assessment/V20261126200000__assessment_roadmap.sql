-- The remediation roadmap planned on submit (assessment#6, FR13): one action per control that is not compliant, from
-- the deterministic rules in rules_version. The consultant reviews and validates it later (assessment#8), so the
-- actions are rows of their own, not a JSON blob.
create table assessment.roadmap (
    id            uuid primary key,
    tenant_id     uuid        not null,
    assessment_id uuid        not null references assessment.assessment (id),
    rules_version text        not null check (char_length(rules_version) <= 100),
    generated_at  timestamptz not null,
    constraint roadmap_assessment_uq unique (assessment_id)
);
create index roadmap_tenant_idx on assessment.roadmap (tenant_id);

create table assessment.roadmap_action (
    id               uuid primary key,
    tenant_id        uuid    not null,
    roadmap_id       uuid    not null references assessment.roadmap (id),
    -- Order in the roadmap: most urgent first, then catalogue order.
    position         integer not null check (position >= 0),
    control_id       text    not null check (char_length(control_id) <= 100),
    title            text    not null check (char_length(title) <= 1000),
    priority         text    not null check (priority in ('HIGH', 'MEDIUM', 'LOW')),
    owner            text    not null check (owner in ('RESPONSIBLE', 'TECHNICAL')),
    due_on           date    not null,
    -- ["Regulamento 756/2026, anexos I e III, GR.CO-3", ...]: the legal obligations behind the action.
    legal_references jsonb   not null,
    -- ["...", ...]: what proves the action is done (the control's verification criteria).
    evidence         jsonb   not null,
    constraint roadmap_action_position_uq unique (roadmap_id, position)
);
create index roadmap_action_tenant_idx on assessment.roadmap_action (tenant_id);
