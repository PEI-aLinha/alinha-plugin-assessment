import type { AssessmentDetail, AssessmentPage, QuestionnaireControl, QuestionnaireQuestion } from '../api'

export const ASSESSMENT_ID = '3f6c1a52-7b0e-4d2a-9c1f-2e8b5d4a7c11'

const question = (id: string, text: string, answer: QuestionnaireQuestion['answer'] = null): QuestionnaireQuestion => ({
  id,
  text,
  explanation: `Explicação de ${id}.`,
  example: `Exemplo de ${id}.`,
  options: ['yes', 'partial', 'no', 'unknown'],
  answer,
  answeredAt: answer ? '2026-11-22T10:00:00Z' : null,
})

const control = (id: string, title: string, technical: boolean, questions: QuestionnaireQuestion[]): QuestionnaireControl => ({
  id,
  title,
  level: 'basic',
  scaleLevel: { id: 'basic', label: 'Básico' },
  technical,
  questions,
})

/**
 * A small NIS2 assessment, from the real catalogue's wording: 3 sections, 5 questions, the first one answered.
 * Controls arrive out of section order (PR before ID), as nothing in the contract promises otherwise.
 */
export function assessmentDetail(overrides: Partial<AssessmentDetail> = {}): AssessmentDetail {
  return {
    id: ASSESSMENT_ID,
    framework: 'nis2-qnrcs',
    catalogueVersion: 1,
    status: 'draft',
    progress: { answered: 1, total: 5 },
    createdAt: '2026-11-22T09:00:00Z',
    updatedAt: '2026-11-22T10:00:00Z',
    catalogueTitle: 'NIS2 — Regime Jurídico da Cibersegurança',
    nis2Classification: 'important',
    nis2ClassificationOfficial: false,
    maxLevel: 'substantial',
    maxScaleLevel: { id: 'substantial', label: 'Substancial' },
    rulesVersion: '2026.11.1',
    controls: [
      control('GR.CO-3-B', 'Requisitos legais conhecidos', false, [
        question('GR.CO-3-B-Q1', 'A empresa sabe que leis e regulamentos de cibersegurança tem de cumprir?', 'yes'),
        question('GR.CO-3-B-Q2', 'Esses requisitos estão registados por escrito?'),
      ]),
      control('PR.GA-3-B', 'Autenticação multifator', true, [
        question('PR.GA-3-B-Q1', 'Os acessos remotos pedem um segundo fator, além da palavra-passe?'),
      ]),
      control('ID.GA-1-B', 'Inventário de equipamentos', false, [
        question('ID.GA-1-B-Q1', 'A empresa tem uma lista atualizada dos seus equipamentos?'),
        question('ID.GA-1-B-Q2', 'A lista diz quem é responsável por cada equipamento?'),
      ]),
    ],
    ...overrides,
  }
}

export function assessmentPage(items: AssessmentPage['items'] = [assessmentDetail()]): AssessmentPage {
  return { items, page: 0, pageSize: 20, total: items.length }
}
