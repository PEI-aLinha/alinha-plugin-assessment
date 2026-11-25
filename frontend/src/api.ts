/**
 * Types of the assessment endpoints, as in the OpenAPI contract of PEI-aLinha/alinha#155.
 * TODO: once #155 is on the main repo's `main`, replace them with `ApiResponse<...>` / `components['schemas']`
 * from `@alinha/api-client`, like the organization plugin does.
 */

export type AnswerValue = 'yes' | 'partial' | 'no' | 'unknown'
/** One level of the catalogue's own level scale (ADR 0007): `substantial` "Substancial", `3` "Definido". */
export interface Level {
  id: string
  label: string
}
export type AssessmentStatus = 'draft' | 'submitted' | 'evaluated' | 'validated'
export type Nis2Classification = 'essential' | 'important' | 'not-covered' | 'to-be-assessed'

export interface Progress {
  answered: number
  total: number
}

export interface AssessmentSummary {
  id: string
  framework: string
  catalogueVersion: number
  status: AssessmentStatus
  progress: Progress
  createdAt: string
  updatedAt: string
}

export interface QuestionnaireQuestion {
  id: string
  text: string
  explanation: string
  example: string
  options: AnswerValue[]
  answer: AnswerValue | null
  answeredAt: string | null
}

export interface QuestionnaireControl {
  id: string
  title: string
  /** @deprecated the level id; use `scaleLevel`. */
  level: string
  scaleLevel: Level
  technical: boolean
  questions: QuestionnaireQuestion[]
}

export interface AssessmentDetail extends AssessmentSummary {
  catalogueTitle: string
  nis2Classification: Nis2Classification
  /** `nis2Classification` is the official CNCS qualification, not the indicative one. */
  nis2ClassificationOfficial: boolean
  /** @deprecated the level id; use `maxScaleLevel`. */
  maxLevel: string
  maxScaleLevel: Level
  rulesVersion: string
  controls: QuestionnaireControl[]
}

export interface AssessmentPage {
  items: AssessmentSummary[]
  page: number
  pageSize: number
  total: number
}

export interface StartAssessmentRequest {
  framework: string
}

export interface SavedAnswer {
  questionId: string
  answer: AnswerValue
  answeredAt: string
  progress: Progress
}

/** The only framework this screen asks about for now. */
export const FRAMEWORK = 'nis2-qnrcs'

export const ASSESSMENTS_PATH = '/assessment/assessments'
export const assessmentPath = (id: string) => `${ASSESSMENTS_PATH}/${id}`
export const answerPath = (assessmentId: string, questionId: string) =>
  `${assessmentPath(assessmentId)}/answers/${encodeURIComponent(questionId)}`

/** Query keys start with the plugin id, so plugins never share a cache entry. */
export const KEYS = {
  all: ['assessment'] as const,
  list: ['assessment', 'list'] as const,
  detail: (id: string) => ['assessment', 'detail', id] as const,
}
