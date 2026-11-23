import type { AnswerValue, AssessmentDetail, ComplianceLevel, Nis2Classification, QuestionnaireControl, QuestionnaireQuestion } from './api'

/** The six areas of the QNRCS, by control id prefix (`GR.CO-1-B` → GR). The contract has no domain field. */
export const SECTIONS = [
  { id: 'GR', name: 'Governação' },
  { id: 'ID', name: 'Identificar' },
  { id: 'PR', name: 'Proteger' },
  { id: 'DE', name: 'Detetar' },
  { id: 'RS', name: 'Responder' },
  { id: 'RC', name: 'Recuperar' },
] as const

const OTHER_SECTION = { id: 'OTHER', name: 'Outras' }

export interface Section {
  id: string
  name: string
  answered: number
  total: number
}

/** One question with its control and section, in the order the user goes through them. */
export interface Step {
  question: QuestionnaireQuestion
  control: QuestionnaireControl
  section: Section
}

export interface Questionnaire {
  sections: Section[]
  steps: Step[]
  answered: number
  total: number
}

const sectionOf = (controlId: string) => SECTIONS.find((s) => controlId.startsWith(`${s.id}.`)) ?? OTHER_SECTION

/**
 * Groups the questions by section, in the QNRCS order (Governação first), keeping the catalogue order inside each
 * section. Sections with no questions are left out.
 */
export function buildQuestionnaire(assessment: Pick<AssessmentDetail, 'controls'>): Questionnaire {
  const order = [...SECTIONS, OTHER_SECTION]
  const sections: Section[] = []
  const steps: Step[] = []
  for (const def of order) {
    const controls = assessment.controls.filter((c) => sectionOf(c.id).id === def.id)
    const questions = controls.flatMap((control) => control.questions.map((question) => ({ question, control })))
    if (questions.length === 0) continue
    const section: Section = {
      id: def.id,
      name: def.name,
      answered: questions.filter(({ question }) => question.answer !== null).length,
      total: questions.length,
    }
    sections.push(section)
    steps.push(...questions.map((q) => ({ ...q, section })))
  }
  const answered = sections.reduce((n, s) => n + s.answered, 0)
  return { sections, steps, answered, total: steps.length }
}

/** Where "Continuar" goes: the first unanswered question (of a section, when given), else the first question. */
export function resumeStep(questionnaire: Questionnaire, sectionId?: string): Step | undefined {
  const steps = sectionId ? questionnaire.steps.filter((s) => s.section.id === sectionId) : questionnaire.steps
  return steps.find((s) => s.question.answer === null) ?? steps[0]
}

export const ANSWER_LABEL: Record<AnswerValue, string> = {
  yes: 'Sim',
  partial: 'Em parte',
  no: 'Não',
  unknown: 'Não sei',
}

export const ANSWER_HINT: Record<AnswerValue, string> = {
  yes: 'Está implementado em toda a empresa.',
  partial: 'Só em alguns sistemas, equipas ou casos.',
  no: 'Não está implementado.',
  unknown: 'Não tem a certeza. Pode voltar a esta pergunta mais tarde.',
}

export const LEVEL_LABEL: Record<ComplianceLevel, string> = {
  basic: 'básico',
  substantial: 'substancial',
  high: 'elevado',
}

export const CLASSIFICATION_LABEL: Record<Nis2Classification, string> = {
  essential: 'entidade essencial',
  important: 'entidade importante',
  'not-covered': 'não abrangida',
  'to-be-assessed': 'a confirmar pela consultora',
}
