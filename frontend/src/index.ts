import { definePlugin } from '@alinha/plugin-sdk'
import { ClipboardCheck } from 'lucide-react'
import { AssessmentPage } from './pages/AssessmentPage'

/**
 * The plugin's frontend entry point. The shell reads this object and mounts
 * the routes and the menu — nothing to register by hand in the shell.
 *   area 'client'     → /org/:org/<path>
 *   area 'consultant' → /portfolio/:org/<path>
 * User-visible text (title, label) is in European Portuguese, plain language
 * (UI requirement in the technical report). Paths are in English.
 */
export default definePlugin({
  id: 'assessment',
  name: 'Assessment',
  routes: {
    client: [
      { path: 'assessment', handle: { title: 'Avaliação' }, Component: AssessmentPage },
    ],
  },
  nav: [
    { label: 'Avaliação', path: 'assessment', area: 'client', icon: ClipboardCheck, order: 30 },
  ],
})
