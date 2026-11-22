import { ClipboardCheck } from 'lucide-react'
import { EmptyState, PageHeader } from '@alinha/ui'

export function AssessmentPage() {
  return (
    <>
      <PageHeader title="Avaliação" subtitle="Questionário de maturidade em cibersegurança." />
      <EmptyState icon={<ClipboardCheck className="size-5" aria-hidden />} title="Em construção">
        Aqui vai poder responder ao questionário sobre as práticas de segurança da sua organização e ver o que já cumpre e o que falta.
      </EmptyState>
    </>
  )
}
