import type { ReactNode } from 'react'
import { render } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { vi } from 'vitest'
import { PluginHostContext, type PluginHost } from '@alinha/plugin-sdk'
import { ToastProvider } from '@alinha/ui'
import { AssessmentPage } from '../pages/AssessmentPage'
import { QuestionPage } from '../pages/QuestionPage'

export const BASE = '/org/comp-agueda/assessment'
export const questionUrl = (assessmentId: string, questionId: string) => `${BASE}/${assessmentId}/questions/${questionId}`

/**
 * Renders the plugin's client routes at `url`, mounted like the shell does (`/org/:org/<path>`), with a fake host.
 * `api` throws unless the test gives one. Both pages are mounted, so tests can follow links between them.
 */
export function renderAt(url: string, { host = {} }: { host?: Partial<PluginHost> } = {}) {
  const fullHost: PluginHost = {
    tenantId: 'a1b2c3d4-0000-4000-8000-000000000001',
    organization: { id: 'a1b2c3d4-0000-4000-8000-000000000001', slug: 'comp-agueda', name: 'Componentes Águeda, Lda.' },
    user: { id: 'u1', name: 'Rui Santos', roles: ['responsible'] },
    can: () => true,
    api: vi.fn(async (path: string) => { throw new Error(`unexpected API call: ${path}`) }) as PluginHost['api'],
    reauthenticate: vi.fn(),
    ...host,
  }
  const page = (ui: ReactNode) => <ToastProvider>{ui}</ToastProvider>
  const router = createMemoryRouter(
    [
      { path: '/org/:org/assessment', element: page(<AssessmentPage />) },
      { path: '/org/:org/assessment/:assessmentId/questions/:questionId', element: page(<QuestionPage />) },
    ],
    { initialEntries: [url] },
  )
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  const view = render(
    <QueryClientProvider client={queryClient}>
      <PluginHostContext.Provider value={fullHost}>
        <RouterProvider router={router} />
      </PluginHostContext.Provider>
    </QueryClientProvider>,
  )
  return { ...view, router, host: fullHost, queryClient }
}
