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

  it('does not leave the question while a save is on its way, so the last choice is not lost', async () => {
    let release: () => void = () => {}
    const first = new Promise<void>((resolve) => { release = resolve })
    let calls = 0
    const fn = api(assessmentDetail(), async (id, answer) => {
      if (++calls === 1) await first
      return saved(id, answer)
    })
    const { router } = renderAt(questionUrl(ASSESSMENT_ID, 'GR.CO-3-B-Q2'), { host: { api: fn } })

    fireEvent.click(await screen.findByRole('radio', { name: /^Não(?! sei)/ }))
    await waitFor(() => expect(putCalls(fn)).toHaveLength(1))
    fireEvent.click(screen.getByRole('radio', { name: /^Sim/ }))
    for (const name of ['Seguinte', 'Anterior', 'Guardar e continuar depois']) {
      expect(screen.getByRole('button', { name })).toBeDisabled()
    }
    fireEvent.click(screen.getByRole('button', { name: 'Seguinte' }))
    expect(router.state.location.pathname).toBe(questionUrl(ASSESSMENT_ID, 'GR.CO-3-B-Q2'))

    await act(async () => { release() })
    await waitFor(() => expect(putCalls(fn).map(([, a]) => a)).toEqual(['no', 'yes']))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Seguinte' })).toBeEnabled())
    fireEvent.click(screen.getByRole('button', { name: 'Seguinte' }))
    await waitFor(() => expect(router.state.location.pathname).toBe(questionUrl(ASSESSMENT_ID, 'ID.GA-1-B-Q1')))
  })

  it('holds a move away from the page until the last choice is saved', async () => {
    let release: () => void = () => {}
    const pending = new Promise<void>((resolve) => { release = resolve })
    const fn = api(assessmentDetail(), async (id, answer) => { await pending; return saved(id, answer) })
    const { router } = renderAt(questionUrl(ASSESSMENT_ID, 'GR.CO-3-B-Q2'), { host: { api: fn } })

    fireEvent.click(await screen.findByRole('radio', { name: /^Sim/ }))
    await waitFor(() => expect(putCalls(fn)).toHaveLength(1))
    await act(async () => { await router.navigate(BASE) })
    expect(router.state.location.pathname).toBe(questionUrl(ASSESSMENT_ID, 'GR.CO-3-B-Q2'))

    await act(async () => { release() })
    await waitFor(() => expect(router.state.location.pathname).toBe(BASE))
  })

  it('cancels a held move when the save fails, so the error and the saved answer stay in view', async () => {
    let reject: (error: unknown) => void = () => {}
    const pending = new Promise<never>((_, fail) => { reject = fail })
    const fn = api(assessmentDetail(), async () => pending)
    const { router } = renderAt(questionUrl(ASSESSMENT_ID, 'GR.CO-3-B-Q1'), { host: { api: fn } })

    fireEvent.click(await screen.findByRole('radio', { name: /^Não(?! sei)/ }))
    await waitFor(() => expect(putCalls(fn)).toHaveLength(1))
    await act(async () => { await router.navigate(BASE) })
    expect(router.state.location.pathname).toBe(questionUrl(ASSESSMENT_ID, 'GR.CO-3-B-Q1'))

    await act(async () => {
      reject(new ApiError(409, 'x', undefined, { code: 'ASSESSMENT_NOT_EDITABLE', detail: 'A avaliação já foi submetida.' }))
    })

    expect(await screen.findByText('A avaliação já foi submetida.')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByRole('radio', { name: /^Sim/ })).toBeChecked())
    expect(router.state.location.pathname).toBe(questionUrl(ASSESSMENT_ID, 'GR.CO-3-B-Q1'))
    // Moving again goes through: nothing is on its way any more.
    await act(async () => { await router.navigate(BASE) })
    await waitFor(() => expect(router.state.location.pathname).toBe(BASE))
  })

  it('asks before closing the tab only while a save is on its way', async () => {
    let release: () => void = () => {}
    const pending = new Promise<void>((resolve) => { release = resolve })
    const fn = api(assessmentDetail(), async (id, answer) => { await pending; return saved(id, answer) })
    renderAt(questionUrl(ASSESSMENT_ID, 'GR.CO-3-B-Q2'), { host: { api: fn } })
    const closing = () => {
      const event = new Event('beforeunload', { cancelable: true })
      window.dispatchEvent(event)
      return event.defaultPrevented
    }

    fireEvent.click(await screen.findByRole('radio', { name: /^Sim/ }))
    await waitFor(() => expect(putCalls(fn)).toHaveLength(1))
    expect(closing()).toBe(true)

    await act(async () => { release() })
    await screen.findByText(/Resposta guardada às/)
    expect(closing()).toBe(false)
  })

  it('answers with keys 1 to 4 and goes on with Enter', async () => {
    const fn = api()
    const { router } = renderAt(questionUrl(ASSESSMENT_ID, 'GR.CO-3-B-Q2'), { host: { api: fn } })
    await screen.findByRole('radio', { name: /^Em parte/ })
    expect(screen.getByText(/Pelo teclado: 1 a 4 escolhem a resposta/)).toBeInTheDocument()

    fireEvent.keyDown(document.body, { key: '2' })
    expect(screen.getByRole('radio', { name: /^Em parte/ })).toBeChecked()
    await waitFor(() => expect(putCalls(fn)).toEqual([[answerPath(ASSESSMENT_ID, 'GR.CO-3-B-Q2'), 'partial']]))
    await screen.findByText(/Resposta guardada/)

    fireEvent.keyDown(document.body, { key: 'Enter' })
    await waitFor(() => expect(router.state.location.pathname).toBe(questionUrl(ASSESSMENT_ID, 'ID.GA-1-B-Q1')))
  })

  it('leaves Enter to a focused button and ignores keys with a modifier', async () => {
    const { router } = renderAt(questionUrl(ASSESSMENT_ID, 'GR.CO-3-B-Q2'), { host: { api: api() } })
    const previous = await screen.findByRole('button', { name: 'Anterior' })

    fireEvent.keyDown(previous, { key: 'Enter' })
    fireEvent.keyDown(document.body, { key: '1', ctrlKey: true })
    expect(router.state.location.pathname).toBe(questionUrl(ASSESSMENT_ID, 'GR.CO-3-B-Q2'))
    expect(screen.getByRole('radio', { name: /^Sim/ })).not.toBeChecked()
  })

  it('ignores a held key, so a held Enter does not skip question after question', async () => {
    const fn = api()
    const { router } = renderAt(questionUrl(ASSESSMENT_ID, 'GR.CO-3-B-Q2'), { host: { api: fn } })
    await screen.findByRole('radio', { name: /^Em parte/ })

    fireEvent.keyDown(document.body, { key: '1', repeat: true })
    fireEvent.keyDown(document.body, { key: 'Enter', repeat: true })

    expect(screen.getByRole('radio', { name: /^Sim/ })).not.toBeChecked()
    expect(putCalls(fn)).toEqual([])
    expect(router.state.location.pathname).toBe(questionUrl(ASSESSMENT_ID, 'GR.CO-3-B-Q2'))
  })

  it('ignores the keys while a dialog is open', async () => {
    const fn = api()
    const { router } = renderAt(questionUrl(ASSESSMENT_ID, 'GR.CO-3-B-Q2'), { host: { api: fn } })
    await screen.findByRole('radio', { name: /^Em parte/ })
    const dialog = document.body.appendChild(document.createElement('div'))
    dialog.setAttribute('role', 'dialog')

    fireEvent.keyDown(document.body, { key: '1' })
    fireEvent.keyDown(document.body, { key: 'Enter' })

    expect(screen.getByRole('radio', { name: /^Sim/ })).not.toBeChecked()
    expect(router.state.location.pathname).toBe(questionUrl(ASSESSMENT_ID, 'GR.CO-3-B-Q2'))
    dialog.remove()
  })

  it('only takes the digits of the options a question has', async () => {
    const detail = assessmentDetail()
    const [first] = detail.controls
    const three = { ...first, questions: first.questions.map((q) => q.id === 'GR.CO-3-B-Q2' ? { ...q, options: ['yes', 'no', 'unknown'] as AnswerValue[] } : q) }
    const fn = api({ ...detail, controls: [three, ...detail.controls.slice(1)] })
    renderAt(questionUrl(ASSESSMENT_ID, 'GR.CO-3-B-Q2'), { host: { api: fn } })
    await screen.findByRole('radio', { name: /^Sim/ })
    expect(screen.getByText(/Pelo teclado: 1 a 3 escolhem a resposta/)).toBeInTheDocument()

    fireEvent.keyDown(document.body, { key: '4' })
    expect(putCalls(fn)).toEqual([])

    fireEvent.keyDown(document.body, { key: '3' })
    await waitFor(() => expect(putCalls(fn)).toEqual([[answerPath(ASSESSMENT_ID, 'GR.CO-3-B-Q2'), 'unknown']]))
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

    fireEvent.click(await screen.findByRole('button', { name: 'Seguinte' }))
    await waitFor(() => expect(router.state.location.pathname).toBe(questionUrl(ASSESSMENT_ID, 'ID.GA-1-B-Q1')))

    fireEvent.click(await screen.findByRole('button', { name: 'Anterior' }))
    await waitFor(() => expect(router.state.location.pathname).toBe(questionUrl(ASSESSMENT_ID, 'GR.CO-3-B-Q2')))

    await act(async () => { await router.navigate(questionUrl(ASSESSMENT_ID, 'PR.GA-3-B-Q1')) })
    fireEvent.click(await screen.findByRole('button', { name: 'Terminar' }))
    await waitFor(() => expect(router.state.location.pathname).toBe(BASE))
  })

  it('has no previous question on the first one', async () => {
    renderAt(questionUrl(ASSESSMENT_ID, 'GR.CO-3-B-Q1'), { host: { api: api() } })

    expect(await screen.findByRole('button', { name: 'Anterior' })).toBeDisabled()
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
