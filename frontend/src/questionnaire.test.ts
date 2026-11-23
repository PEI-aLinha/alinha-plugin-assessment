import { describe, expect, it } from 'vitest'
import { buildQuestionnaire, resumeStep } from './questionnaire'
import { assessmentDetail } from './test/fixtures'

describe('buildQuestionnaire', () => {
  it('groups the questions by QNRCS section, in QNRCS order, whatever order the controls arrive in', () => {
    const q = buildQuestionnaire(assessmentDetail())

    expect(q.sections.map((s) => s.name)).toEqual(['Governação', 'Identificar', 'Proteger'])
    expect(q.steps.map((s) => s.question.id)).toEqual([
      'GR.CO-3-B-Q1', 'GR.CO-3-B-Q2', 'ID.GA-1-B-Q1', 'ID.GA-1-B-Q2', 'PR.GA-3-B-Q1',
    ])
  })

  it('counts the answered questions per section and in total', () => {
    const q = buildQuestionnaire(assessmentDetail())

    expect(q.sections.map((s) => [s.id, s.answered, s.total])).toEqual([['GR', 1, 2], ['ID', 0, 2], ['PR', 0, 1]])
    expect([q.answered, q.total]).toEqual([1, 5])
  })

  it('keeps a control with an unknown prefix, in a last "Outras" section', () => {
    const detail = assessmentDetail()
    const q = buildQuestionnaire({ controls: [{ ...detail.controls[0], id: 'XX.AB-1-B' }, detail.controls[1]] })

    expect(q.sections.map((s) => s.name)).toEqual(['Proteger', 'Outras'])
  })
})

describe('resumeStep', () => {
  it('is the first unanswered question', () => {
    expect(resumeStep(buildQuestionnaire(assessmentDetail()))?.question.id).toBe('GR.CO-3-B-Q2')
  })

  it('is the first unanswered question of a section, when given one', () => {
    expect(resumeStep(buildQuestionnaire(assessmentDetail()), 'PR')?.question.id).toBe('PR.GA-3-B-Q1')
  })

  it('is the first question once everything is answered', () => {
    const detail = assessmentDetail()
    const all = {
      controls: detail.controls.map((c) => ({ ...c, questions: c.questions.map((q) => ({ ...q, answer: 'no' as const })) })),
    }

    expect(resumeStep(buildQuestionnaire(all))?.question.id).toBe('GR.CO-3-B-Q1')
  })
})
