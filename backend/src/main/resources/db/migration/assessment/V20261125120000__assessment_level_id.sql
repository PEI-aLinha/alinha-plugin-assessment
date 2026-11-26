-- Catalogues declare their own level scale (ADR 0007): max_level is now a level id of the assessment's catalogue
-- (basic, substantial, high for nis2-qnrcs; 1 to 5 for soc-cmm), no longer an enum name.
alter table assessment.assessment drop constraint assessment_max_level_check;
update assessment.assessment set max_level = lower(max_level);
alter table assessment.assessment add constraint assessment_max_level_check
    check (max_level ~ '^[a-z0-9]+(-[a-z0-9]+)*$' and char_length(max_level) <= 100);
