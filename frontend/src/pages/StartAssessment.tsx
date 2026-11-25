import { useQueryClient } from '@tanstack/react-query'
import { ApiError, errorMessage } from '@alinha/plugin-sdk'
import { Button, Card, Note } from '@alinha/ui'
import { FRAMEWORK, KEYS } from '../api'
import { useStartAssessment } from '../assessmentQueries'

/**
 * Starts the NIS2 assessment. The server cuts the questions to the regulatory scope the organization answered on
 * its organization page (and to the official CNCS qualification, when registered).
 */
export function StartAssessment() {
  const queryClient = useQueryClient()
  const start = useStartAssessment()

  const submit = () => start.mutate(
    { framework: FRAMEWORK },
    {
      // An assessment of NIS2 is already open (another tab or person started it): reload the list, which then
      // shows it instead of this card.
      onError: (error) => { if (alreadyOpen(error)) void queryClient.invalidateQueries({ queryKey: KEYS.list }) },
    },
  )

  if (start.isSuccess) {
    return <Note>Avaliação criada. A abrir…</Note>
  }

  return (
    <Card className="flex max-w-[760px] flex-col gap-5 px-7 py-6 max-md:px-5">
      <div className="flex flex-col gap-1.5">
        <h2 className="text-[1.25rem] font-extrabold">Começar a avaliação NIS2</h2>
        <p className="text-[0.9375rem] text-ink-soft">
          As perguntas são escolhidas a partir do âmbito regulatório que indicou na página da organização.
        </p>
      </div>
      {start.isError && <StartError error={start.error} />}
      <Button className="self-start" loading={start.isPending} onClick={submit}>Começar a avaliação</Button>
    </Card>
  )
}

const alreadyOpen = (error: unknown) =>
  error instanceof ApiError && error.status === 409 && error.problem?.code === 'ASSESSMENT_ALREADY_OPEN'

function StartError({ error }: { error: Error }) {
  if (error instanceof ApiError && error.problem?.code === 'SCOPE_NOT_ANSWERED') {
    return (
      <Note>
        Antes de começar, responda às perguntas do âmbito regulatório, na página da organização. É por elas que
        sabemos que perguntas lhe fazer.
      </Note>
    )
  }
  if (error instanceof ApiError && error.problem?.code === 'FRAMEWORK_NOT_IN_SCOPE') {
    return (
      <Note>
        Pelas respostas do âmbito regulatório, a organização não está abrangida pelo regime de cibersegurança (NIS2),
        por isso não há perguntas a responder. Se acha que está, reveja essas respostas ou fale com a sua consultora.
      </Note>
    )
  }
  return <p role="alert" className="rounded-[10px] bg-risk-high px-3.5 py-2.5 text-sm font-semibold text-risk-high-ink">{errorMessage(error)}</p>
}
