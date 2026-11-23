import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ApiError, type PluginHost } from '@alinha/plugin-sdk'
import { ASSESSMENTS_PATH, assessmentPath, type AssessmentPage as Page } from '../api'
import { ASSESSMENT_ID, assessmentDetail, assessmentPage } from '../test/fixtures'
import { BASE, renderAt } from '../test/renderWithHost'

const PROFILE = { sector: 'industria', subsector: 'automoveis', employees: 120, turnover: 18500000 }

/** A fake `api` that answers by path (and method), like the backend would. */
function fakeApi(routes: Record<string, (init?: RequestInit) => unknown>) {
  return vi.fn(async (path: string, init?: RequestInit) => {
    const key = `${init?.method ?? 'GET'} ${path}`
    const handler = routes[key]
    if (!handler) throw new Error(`unexpected API call: ${key}`)
    return handler(init)
  }) as unknown as PluginHost['api'] & ReturnType<typeof vi.fn>
}

const empty: Page = assessmentPage([])

describe('AssessmentPage with an assessment', () => {
  const api = () => fakeApi({
    [`GET ${ASSESSMENTS_PATH}`]: () => assessmentPage(),
    [`GET ${assessmentPath(ASSESSMENT_ID)}`]: () => assessmentDetail(),
  })

  it('shows the progress, the indicative classification and each section', async () => {
    renderAt(BASE, { host: { api: api() } })

    expect(await screen.findByText('de 5 perguntas respondidas')).toBeInTheDocument()
    expect(screen.getByText('entidade importante')).toBeInTheDocument()
    expect(screen.getByText(/as perguntas vão até ao nível substancial/)).toBeInTheDocument()
    // Collapsed until asked for.
    expect(screen.queryByRole('list', { name: 'Secções da avaliação' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /^Perguntas por secção/ }))
    const sections = within(screen.getByRole('list', { name: 'Secções da avaliação' })).getAllByRole('button')
    expect(sections.map((b) => b.textContent?.trim())).toEqual(['Governação 1/2', 'Identificar 0/2', 'Proteger 0/1'])
    expect(screen.getByText('3 secções · 5 perguntas')).toBeInTheDocument()
  })

  it('continues at the first unanswered question', async () => {
    const { router } = renderAt(BASE, { host: { api: api() } })

    fireEvent.click(await screen.findByRole('link', { name: 'Responder à próxima pergunta' }))

    await waitFor(() => expect(router.state.location.pathname).toBe(`${BASE}/${ASSESSMENT_ID}/questions/GR.CO-3-B-Q2`))
  })

  it('opens a section at its first unanswered question', async () => {
    const { router } = renderAt(BASE, { host: { api: api() } })

    fireEvent.click(await screen.findByRole('button', { name: /^Perguntas por secção/ }))
    fireEvent.click(screen.getByRole('button', { name: /Proteger/ }))
    expect(screen.getByRole('progressbar', { name: 'Progresso de Proteger' })).toHaveAttribute('aria-valuenow', '0')
    fireEvent.click(screen.getByRole('link', { name: /^Responder de Proteger/ }))

    await waitFor(() => expect(router.state.location.pathname).toBe(`${BASE}/${ASSESSMENT_ID}/questions/PR.GA-3-B-Q1`))
  })

  it('says when the assessment was submitted and only offers to see the answers', async () => {
    const submitted = fakeApi({
      [`GET ${ASSESSMENTS_PATH}`]: () => assessmentPage(),
      [`GET ${assessmentPath(ASSESSMENT_ID)}`]: () => assessmentDetail({ status: 'submitted' }),
    })
    renderAt(BASE, { host: { api: submitted } })

    expect(await screen.findByText('A avaliação já foi submetida. As respostas já não podem ser alteradas.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ver respostas' })).toBeInTheDocument()
  })

  it('shows an error with a retry when the list fails', async () => {
    const failing = vi.fn()
      .mockRejectedValueOnce(new ApiError(500, ASSESSMENTS_PATH))
      .mockResolvedValueOnce(empty) as unknown as PluginHost['api']
    renderAt(BASE, { host: { api: failing, can: (resource) => resource === 'assessment' } })

    fireEvent.click(await screen.findByRole('button', { name: 'Tentar de novo' }))

    expect(await screen.findByRole('heading', { name: 'Começar a avaliação NIS2' })).toBeInTheDocument()
  })
})

describe('AssessmentPage with no assessment yet', () => {
  it('tells someone who cannot start one who does it', async () => {
    const api = fakeApi({ [`GET ${ASSESSMENTS_PATH}`]: () => empty })
    renderAt(BASE, { host: { api, can: (resource, action) => !(resource === 'assessment' && action === 'start') } })

    expect(await screen.findByText('Ainda não há avaliação')).toBeInTheDocument()
  })

  it('prefills the size from the registration and ticks the only activity of the sector', async () => {
    const api = fakeApi({
      [`GET ${ASSESSMENTS_PATH}`]: () => empty,
      'GET /organization/profile': () => PROFILE,
    })
    renderAt(BASE, { host: { api } })

    expect(await screen.findByLabelText('Número de trabalhadores')).toHaveValue('120')
    expect((screen.getByLabelText('Volume de negócios anual (euros)') as HTMLInputElement).value).toMatch(/^18\s500\s000$/)
    expect(screen.getByRole('checkbox', { name: 'Fabrico de veículos automóveis, reboques e componentes' })).toBeChecked()
    expect(screen.getAllByRole('checkbox')).toHaveLength(1)
  })

  it('shows every activity when asked', async () => {
    const api = fakeApi({ [`GET ${ASSESSMENTS_PATH}`]: () => empty, 'GET /organization/profile': () => PROFILE })
    renderAt(BASE, { host: { api } })

    fireEvent.click(await screen.findByRole('button', { name: 'Ver todas as atividades' }))

    expect(screen.getByRole('checkbox', { name: 'Prestador de serviços de DNS' })).toBeInTheDocument()
    expect(screen.getAllByRole('checkbox').length).toBeGreaterThan(60)
  })

  it('starts with an empty form when the organization has no profile', async () => {
    const api = fakeApi({
      [`GET ${ASSESSMENTS_PATH}`]: () => empty,
      'GET /organization/profile': () => { throw new ApiError(404, '/organization/profile', undefined, { code: 'ORGANIZATION_NOT_REGISTERED' }) },
    })
    renderAt(BASE, { host: { api } })

    expect(await screen.findByLabelText('Número de trabalhadores')).toHaveValue('')
    // No sector to suggest from: every activity is on the list already.
    expect(screen.getByRole('checkbox', { name: 'Prestador de serviços de DNS' })).not.toBeChecked()
    expect(screen.queryByRole('button', { name: 'Ver todas as atividades' })).not.toBeInTheDocument()
  })

  it('does not read the profile without permission to', async () => {
    const api = fakeApi({ [`GET ${ASSESSMENTS_PATH}`]: () => empty })
    renderAt(BASE, { host: { api, can: (resource) => resource !== 'organization' } })

    expect(await screen.findByLabelText('Número de trabalhadores')).toHaveValue('')
    expect(api).not.toHaveBeenCalledWith('/organization/profile', expect.anything())
  })

  it('asks for the missing answers before starting', async () => {
    const api = fakeApi({ [`GET ${ASSESSMENTS_PATH}`]: () => empty, 'GET /organization/profile': () => PROFILE })
    renderAt(BASE, { host: { api } })

    fireEvent.click(await screen.findByRole('button', { name: 'Começar a avaliação' }))

    expect(screen.getAllByText('Escolha uma opção.')).toHaveLength(2)
    expect(api).not.toHaveBeenCalledWith(ASSESSMENTS_PATH, expect.objectContaining({ method: 'POST' }))
  })

  it('starts the assessment with the scope and then shows it', async () => {
    let started = false
    const api = fakeApi({
      [`GET ${ASSESSMENTS_PATH}`]: () => (started ? assessmentPage() : empty),
      'GET /organization/profile': () => PROFILE,
      [`POST ${ASSESSMENTS_PATH}`]: () => { started = true; return assessmentDetail({ progress: { answered: 0, total: 5 } }) },
      [`GET ${assessmentPath(ASSESSMENT_ID)}`]: () => assessmentDetail(),
    })
    renderAt(BASE, { host: { api } })

    fireEvent.click(within(await screen.findByRole('group', { name: /dados pessoais/ })).getByLabelText('Sim'))
    fireEvent.click(within(screen.getByRole('group', { name: /inteligência artificial/ })).getByLabelText('Não'))
    fireEvent.click(screen.getByRole('button', { name: 'Começar a avaliação' }))

    expect(await screen.findByText('de 5 perguntas respondidas')).toBeInTheDocument()
    const post = api.mock.calls.find(([, init]) => (init as RequestInit | undefined)?.method === 'POST')
    expect(JSON.parse((post?.[1] as RequestInit).body as string)).toEqual({
      framework: 'nis2-qnrcs',
      scope: {
        size: { employees: 120, annualTurnoverEur: 18500000 },
        nis2: { entityTypes: ['MOTOR_VEHICLE_MANUFACTURING'] },
        gdpr: { processesPersonalData: true },
        aiAct: { usesAiSystems: false },
      },
    })
  })

  it('explains when NIS2 does not apply by the answers', async () => {
    const api = fakeApi({
      [`GET ${ASSESSMENTS_PATH}`]: () => empty,
      'GET /organization/profile': () => PROFILE,
      [`POST ${ASSESSMENTS_PATH}`]: () => { throw new ApiError(422, ASSESSMENTS_PATH, undefined, { code: 'FRAMEWORK_NOT_IN_SCOPE' }) },
    })
    renderAt(BASE, { host: { api } })

    fireEvent.click(within(await screen.findByRole('group', { name: /dados pessoais/ })).getByLabelText('Não'))
    fireEvent.click(within(screen.getByRole('group', { name: /inteligência artificial/ })).getByLabelText('Não'))
    fireEvent.click(screen.getByRole('button', { name: 'Começar a avaliação' }))

    expect(await screen.findByText(/não está abrangida pelo regime de cibersegurança/)).toBeInTheDocument()
  })

  it('opens the assessment someone else already started', async () => {
    let listCalls = 0
    const api = fakeApi({
      [`GET ${ASSESSMENTS_PATH}`]: () => (++listCalls === 1 ? empty : assessmentPage()),
      'GET /organization/profile': () => PROFILE,
      [`POST ${ASSESSMENTS_PATH}`]: () => {
        throw new ApiError(409, ASSESSMENTS_PATH, undefined, { code: 'ASSESSMENT_ALREADY_OPEN', detail: 'Já existe uma avaliação aberta.' })
      },
      [`GET ${assessmentPath(ASSESSMENT_ID)}`]: () => assessmentDetail(),
    })
    renderAt(BASE, { host: { api } })

    fireEvent.click(within(await screen.findByRole('group', { name: /dados pessoais/ })).getByLabelText('Sim'))
    fireEvent.click(within(screen.getByRole('group', { name: /inteligência artificial/ })).getByLabelText('Sim'))
    fireEvent.click(screen.getByRole('button', { name: 'Começar a avaliação' }))

    expect(await screen.findByText('de 5 perguntas respondidas')).toBeInTheDocument()
  })
})
