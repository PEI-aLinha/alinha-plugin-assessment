-- Whether nis2_classification is the official CNCS qualification (core#25) or the indicative one. Earlier assessments
-- were all started from the indicative classification.
alter table assessment.assessment add column nis2_classification_official boolean not null default false;
