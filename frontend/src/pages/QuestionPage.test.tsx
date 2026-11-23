import { act, fireEvent, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ApiError, type PluginHost } from '@alinha/plugin-sdk'
import { answerPath, assessmentPath, type AnswerValue, type SavedAnswer } from '../api'
import { ASSESSMENT_ID, assessmentDetail } from '../test/fixtures'
import { BASE, questionUrl, renderAt } from '../test/renderWithHost'

const saved = (questionId: string, answer: AnswerValue, answered = 2): SavedAnswer => ({
  questionId, answer, answeredAt: '2026-11-22T15:44:00Z', progress: { answered, total: 5 },
})

function api(detail = assessmentDetail(), onSave?: (questionId: string, answer: AnswerValue) => Promise<SavedAnswer>) {
  return vi.fn(async (path: string, init?: RequestInit) => {
    if (path === assessmentPath(ASSESSMENT_ID) && !init?.method) return detail
    const match = path.match(/\/answers\/(.+)$/)
    if (match && init?.method === 'PUT') {
      const { answer } = JSON.parse(init.body as string) as { answer: AnswerValue }
      return (onSave ?? (async (id, a) => saved(id, a)))(decodeURIComponent(match[1]), answer)
    }
    throw new Error(`unexpected API call: ${init?.method ?? 'GET'} ${path}`)
  }) as unknown as PluginHost['api'] & ReturnType<typeof vi.fn>
}

const putCalls = (fn: ReturnType<typeof vi.fn>) =>
  fn.mock.calls.filter(([, init]) => (init as RequestInit | undefined)?.method === 'PUT')
    .map(([path, init]) => [path, JSON.parse((init as RequestInit).body as string).answer])

describe('QuestionPage', () => {
  it('shows the question, its position, the explanation and the example', async () => {
    renderAt(questionUrl(ASSESSMENT_ID, 'PR.GA-3-B-Q1'), { host: { api: api() } })

    expect(await screen.findByRole('heading', { level: 1, name: 'Pergunta 5 de 5' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Os acessos remotos pedem um segundo fator, além da palavra-passe?' })).toBeInTheDocument()
    expect(screen.getByText('Pergunta técnica')).toBeInTheDocument()
    expect(screen.getByText('Explicação de PR.GA-3-B-Q1.')).toBeInTheDocument()
    expect(screen.getByText('Exemplo de PR.GA-3-B-Q1.')).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: /^Não sei/ })).toBeInTheDocument()
  })

  it('shows the saved answer as chosen', async () => {
    renderAt(questionUrl(ASSESSMENT_ID, 'GR.CO-3-B-Q1'), { host: { api: api() } })

    expect(await screen.findByRole('radio', { name: /^Sim/ })).toBeChecked()
    expect(screen.queryByText('Pergunta técnica')).not.toBeInTheDocument()
  })

  it('saves the answer as soon as it is chosen, and says so', async () => {
    const fn = api()
    renderAt(questionUrl(ASSESSMENT_ID, 'GR.CO-3-B-Q2'), { host: { api: fn } })

    fireEvent.click(await screen.findByRole('radio', { name: /^Em parte/ }))

    expect(await screen.findByText(/Resposta guardada às 15:44/)).toBeInTheDocument()
    expect(putCalls(fn)).toEqual([[answerPath(ASSESSMENT_ID, 'GR.CO-3-B-Q2'), 'partial']])
    // The progress bar follows the saved answer without reloading the assessment.
    expect(screen.getByRole('progressbar', { name: 'Progresso da avaliação' })).toHaveAttribute('aria-valuenow', '2')
  })

  it('sends quick changes one at a time and keeps the last one', async () => {
    let release: () => void = () => {}
    const first = new Promise<void>((resolve) => { release = resolve })
    let calls = 0
    const fn = api(assessmentDetail(), async (id, answer) => {
      if (++calls === 1) await first
      return saved(id, answer)
    })
    renderAt(questionUrl(ASSESSMENT_ID, 'GR.CO-3-B-Q2'), { host: { api: fn } })

    fireEvent.click(await screen.findByRole('radio', { name: /^Não(?! sei)/ }))
    await waitFor(() => expect(putCalls(fn)).toHaveLength(1))
    fireEvent.click(screen.getByRole('radio', { name: /^Em parte/ }))
    fireEvent.click(screen.getByRole('radio', { name: /^Sim/ }))
    expect(putCalls(fn)).toHaveLength(1)
    await act(async () => { release() })

    await waitFor(() => expect(putCalls(fn).map(([, a]) => a)).toEqual(['no', 'yes']))
    expect(screen.getByRole('radio', { name: /^Sim/ })).toBeChecked()
  })

  it('goes back to the saved answer when a save fails', async () => {
    const fn = api(assessmentDetail(), async () => { throw new ApiError(409, 'x', undefined, { code: 'ASSESSMENT_NOT_EDITABLE', detail: 'A avaliação já foi submetida.' }) })
    renderAt(questionUrl(ASSESSMENT_ID, 'GR.CO-3-B-Q1'), { host: { api: fn } })

    fireEvent.click(await screen.findByRole('radio', { name: /^Não(?! sei)/ }))

    expect(await screen.findByText('A avaliação já foi submetida.')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByRole('radio', { name: /^Sim/ })).toBeChecked())
  })

  it('moves through every question in order, across sections, and ends at the overview', async () => {
    const { router } = renderAt(questionUrl(ASSESSMENT_ID, 'GR.CO-3-B-Q2'), { host: { api: api() } })

    fireEvent.click(await screen.findByRole('button', { name: 'Seguinte →' }))
    await waitFor(() => expect(router.state.location.pathname).toBe(questionUrl(ASSESSMENT_ID, 'ID.GA-1-B-Q1')))

    fireEvent.click(await screen.findByRole('button', { name: '← Anterior' }))
    await waitFor(() => expect(router.state.location.pathname).toBe(questionUrl(ASSESSMENT_ID, 'GR.CO-3-B-Q2')))

    await act(async () => { await router.navigate(questionUrl(ASSESSMENT_ID, 'PR.GA-3-B-Q1')) })
    fireEvent.click(await screen.findByRole('button', { name: 'Terminar' }))
    await waitFor(() => expect(router.state.location.pathname).toBe(BASE))
  })

  it('has no previous question on the first one', async () => {
    renderAt(questionUrl(ASSESSMENT_ID, 'GR.CO-3-B-Q1'), { host: { api: api() } })

    expect(await screen.findByRole('button', { name: '← Anterior' })).toBeDisabled()
  })

  it('"Guardar e continuar depois" goes back to the overview', async () => {
    const { router } = renderAt(questionUrl(ASSESSMENT_ID, 'GR.CO-3-B-Q2'), { host: { api: api() } })

    fireEvent.click(await screen.findByRole('button', { name: 'Guardar e continuar depois' }))

    await waitFor(() => expect(router.state.location.pathname).toBe(BASE))
  })

  it('only shows the answer once the assessment is submitted', async () => {
    renderAt(questionUrl(ASSESSMENT_ID, 'GR.CO-3-B-Q1'), { host: { api: api(assessmentDetail({ status: 'submitted' })) } })

    expect(await screen.findByText('Sim')).toBeInTheDocument()
    expect(screen.queryByRole('radio')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Guardar e continuar depois' })).not.toBeInTheDocument()
  })

  it('only shows the answer to someone who cannot answer', async () => {
    renderAt(questionUrl(ASSESSMENT_ID, 'GR.CO-3-B-Q2'), {
      host: { api: api(), can: (resource, action) => !(resource === 'assessment' && action === 'answer') },
    })

    expect(await screen.findByText('Sem resposta')).toBeInTheDocument()
    expect(screen.queryByRole('radio')).not.toBeInTheDocument()
  })

  it('says when the question is not in the assessment', async () => {
    renderAt(questionUrl(ASSESSMENT_ID, 'XX-Q9'), { host: { api: api() } })

    expect(await screen.findByRole('heading', { level: 1, name: 'Pergunta não encontrada' })).toBeInTheDocument()
  })

  it('shows an error with a retry when the assessment does not load', async () => {
    const fn = vi.fn()
      .mockRejectedValueOnce(new ApiError(500, assessmentPath(ASSESSMENT_ID)))
      .mockResolvedValueOnce(assessmentDetail()) as unknown as PluginHost['api']
    renderAt(questionUrl(ASSESSMENT_ID, 'GR.CO-3-B-Q1'), { host: { api: fn } })

    fireEvent.click(await screen.findByRole('button', { name: 'Tentar de novo' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Pergunta 1 de 5' })).toBeInTheDocument()
  })
})
