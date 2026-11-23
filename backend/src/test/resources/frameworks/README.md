# Test catalogues

Small catalogues used only by the assessment plugin's tests (`AssessmentTestApplication` points
`alinha.frameworks.dir` here). Production catalogues live in the main repo's `frameworks/`.

They must pass the core's `CatalogueLoader` checks (ids, non-blank texts, at least one requirement, evidence and
question, two or more answers per question), or the test context fails to start.

| Catalogue | Controls (level) | What the tests use it for |
|---|---|---|
| `nis2-qnrcs/v1` | `CTRL-A` (basic), `CTRL-B` (substantial), `CTRL-C` (high, technical, answers `yes`/`no` only) | Cutting the questionnaire by NIS2 classification; a value the question does not offer (`unknown` on `CTRL-C-Q1`); an assessment pinned to v1 |
| `nis2-qnrcs/v2` | v1 + `CTRL-D` (basic) | A new assessment starts on the newest version; an existing one keeps its pinned version |
| `gdpr/v1` | `CTRL-G` (basic), `CTRL-GH` (high) | Frameworks other than NIS2 get every level; a question from another framework is not part of the assessment; list order by last change |

When a test needs a new case, add a control here and update this table. Do not change what an existing control
is for: other tests rely on it.