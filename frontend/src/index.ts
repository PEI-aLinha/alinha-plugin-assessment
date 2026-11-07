import { definePlugin } from '@alinha/plugin-sdk'
import { AssessmentPage } from './pages/AssessmentPage'

/**
 * The plugin's frontend entry point. The shell reads this object and mounts
 * the routes and the menu — nothing to register by hand in the shell.
 *   area 'client'     → /org/:org/<path>
 *   area 'consultant' → /carteira/<path>
 * User-visible text (title, label) and paths are in Portuguese, plain language
 * (UI requirement in the technical report).
 */
export default definePlugin({
  id: 'assessment',
  name: 'Assessment',
  routes: {
    client: [
      { path: 'avaliacao', handle: { title: 'Avaliação' }, Component: AssessmentPage },
    ],
  },
  nav: [
    { label: 'Avaliação', path: 'avaliacao', area: 'client' },
  ],
})
