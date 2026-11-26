-- Results the Compliance Engine computed on submit (assessment#5). Written once and never updated, so they do not
-- change when the rules or the catalogue do (RNF05). Control titles come from the pinned catalogue version.
-- Levels are level ids of the catalogue's own scale (basic, substantial, high for nis2-qnrcs; ADR 0007), not enum
-- names, so a catalogue with another scale (SOC-CMM) fits without a new migration.
create table assessment.result (
    id             uuid primary key,
    tenant_id      uuid             not null,
    assessment_id  uuid             not null references assessment.assessment (id),
    engine_version text             not null check (char_length(engine_version) <= 100),
    current_level  text             check (current_level ~ '^[a-z0-9]+(-[a-z0-9]+)*$' and char_length(current_level) <= 100),
    target_level   text             not null
                                    check (target_level ~ '^[a-z0-9]+(-[a-z0-9]+)*$' and char_length(target_level) <= 100),
    coverage       double precision not null check (coverage between 0 and 1),
    -- [{"controlId": "...", "level": "basic", "status": "COMPLIANT"}, ...] in catalogue order.
    controls       jsonb            not null,
    evaluated_at   timestamptz      not null,
    constraint result_assessment_uq unique (assessment_id)
);
create index result_tenant_idx on assessment.result (tenant_id);
