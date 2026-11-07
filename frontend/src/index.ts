import { definePlugin } from '@alinha/plugin-sdk'
import { AssessmentPage } from './pages/AssessmentPage'

/**
 * Ponto de entrada do plugin no frontend. O shell lê este objeto e monta
 * as rotas e o menu — não há nada a registar à mão no shell.
 *   area 'client'     → /org/:org/<path>
 *   area 'consultant' → /carteira/<path>
 */
export default definePlugin({
  id: 'assessment',
  name: 'Assessment',
  routes: {
    client: [
      { path: 'assessment', handle: { title: 'Assessment' }, Component: AssessmentPage },
    ],
  },
  nav: [
    { label: 'Assessment', path: 'assessment', area: 'client' },
  ],
})
