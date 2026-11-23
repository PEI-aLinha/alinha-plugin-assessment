import { useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ArrowLeft, Check } from 'lucide-react'
import { errorMessage, formatDateTime, LOCALE, TIME_ZONE, useHost } from '@alinha/plugin-sdk'
import { Badge, Button, Card, EmptyState, ErrorState, Eyebrow, PageHeader, RadioGroup, SkeletonRows, useToast } from '@alinha/ui'
import type { AnswerValue, AssessmentDetail } from '../api'
import { useAssessmentDetail, useSaveAnswer } from '../assessmentQueries'
import { ProgressBar } from '../components/ProgressBar'
import { ANSWER_HINT, ANSWER_LABEL, buildQuestionnaire, type Step } from '../questionnaire'

/** Paths relative to this page (`…/assessment/:assessmentId/questions/:questionId`). */
const OVERVIEW = '../../..'
const sibling = (questionId: string) => `../${encodeURIComponent(questionId)}`

/**
 * One question of the assessment (FR09, US13): the answer options, and beside them what the question means with an
 * example. Choosing an option saves it at once; Anterior / Seguinte move through every question in order.
 */
export function QuestionPage() {
  const { assessmentId = '', questionId = '' } = useParams()
  const detail = useAssessmentDetail(assessmentId)
  const back = (
    <Link to={OVERVIEW} relative="path" className="-mb-4 inline-flex items-center gap-1.5 text-[0.875rem] font-bold text-brand hover:underline">
      <ArrowLeft className="size-4" aria-hidden /><span className="sr-only">Voltar à</span>{' '}Avaliação
    </Link>
  )

  if (detail.isError) return <>{back}<ErrorState error={detail.error} onRetry={() => void detail.refetch()} /></>
  if (detail.isPending) return <SkeletonRows rows={4} label="A carregar a pergunta…" />

  const questionnaire = buildQuestionnaire(detail.data)
  const index = questionnaire.steps.findIndex((s) => s.question.id === questionId)
  if (index < 0) {
    return (
      <EmptyState headingLevel={1} title="Pergunta não encontrada">
        <Link to={OVERVIEW} relative="path" className="font-bold text-brand hover:underline">Voltar à avaliação</Link>
      </EmptyState>
    )
  }
  const step = questionnaire.steps[index]
  const prev = questionnaire.steps[index - 1]?.question.id
  const next = questionnaire.steps[index + 1]?.question.id

  return (
    <>
      {back}
      <PageHeader eyebrow={`${detail.data.catalogueTitle} · ${step.section.name}`} title={`Pergunta ${index + 1} de ${questionnaire.total}`} />
      <ProgressBar value={questionnaire.answered} max={questionnaire.total} label="Progresso da avaliação" />
      <div className="mt-4 grid gap-6 lg:grid-cols-[minmax(0,1fr)_340px]">
        <QuestionCard key={step.question.id} assessment={detail.data} step={step} prev={prev} next={next} />
        <Explanation step={step} />
      </div>
    </>
  )
}

function QuestionCard({ assessment, step, prev, next }: { assessment: AssessmentDetail; step: Step; prev?: string; next?: string }) {
  const { can } = useHost()
  const navigate = useNavigate()
  const { question, control } = step
  const editable = assessment.status === 'draft' && can('assessment', 'answer')
  // Leaving the page while a save is on its way would drop a choice still waiting to be sent: wait for it.
  const [saving, setSaving] = useState(false)
  const go = (to: string) => navigate(to, { relative: 'path' })

  return (
    <Card className="flex flex-col gap-5 px-7 py-6 max-md:px-5">
      <div className="flex flex-col gap-2.5">
        <p className="flex flex-wrap items-center gap-2">
          {control.technical && <Badge tone="dark">Pergunta técnica</Badge>}
          <span className="text-[0.8125rem] font-semibold text-ink-soft">{control.title}</span>
        </p>
        <h2 className="text-[1.25rem] font-extrabold leading-snug">{question.text}</h2>
      </div>

      {editable ? <AnswerOptions assessmentId={assessment.id} step={step} saving={saving} onSavingChange={setSaving} /> : <ReadOnlyAnswer answer={question.answer} answeredAt={question.answeredAt} />}

      <div className="flex flex-wrap items-center justify-between gap-2">
        <Button variant="secondary" disabled={!prev || saving} onClick={() => prev && go(sibling(prev))}>← Anterior</Button>
        <div className="flex flex-wrap justify-end gap-2">
          {editable && <Button variant="secondary" disabled={saving} onClick={() => go(OVERVIEW)}>Guardar e continuar depois</Button>}
          {/* After the last question, back to the overview of the sections. */}
          <Button disabled={saving} onClick={() => go(next ? sibling(next) : OVERVIEW)}>{next ? 'Seguinte →' : 'Terminar'}</Button>
        </div>
      </div>
    </Card>
  )
}

/**
 * The options, saved as soon as one is chosen. Saves go one at a time and the last choice always wins: a choice
 * made while a save is on its way is sent when that one ends. A failed save shows an error and goes back to the
 * answer the server has. The page keeps `saving` so it can hold navigation until the last choice is saved.
 */
function AnswerOptions({ assessmentId, step, saving, onSavingChange: setSaving }: {
  assessmentId: string
  step: Step
  saving: boolean
  onSavingChange: (saving: boolean) => void
}) {
  const toast = useToast()
  const save = useSaveAnswer(assessmentId)
  const { question } = step
  const [choice, setChoice] = useState<AnswerValue | null>(question.answer)
  const [savedAt, setSavedAt] = useState<string | null>(null)
  const wanted = useRef<AnswerValue | null>(question.answer)
  const inFlight = useRef(false)
  const serverAnswer = useRef<AnswerValue | null>(question.answer)

  const send = (answer: AnswerValue) => {
    inFlight.current = true
    setSaving(true)
    save.mutateAsync({ questionId: question.id, answer })
      .then((saved) => {
        serverAnswer.current = saved.answer
        setSavedAt(saved.answeredAt)
      })
      .catch((error: unknown) => {
        toast.show(errorMessage(error), 'error')
        wanted.current = serverAnswer.current
        setChoice(serverAnswer.current)
      })
      .finally(() => {
        inFlight.current = false
        if (wanted.current !== null && wanted.current !== serverAnswer.current) send(wanted.current)
        else setSaving(false)
      })
  }

  const choose = (answer: AnswerValue) => {
    setChoice(answer)
    wanted.current = answer
    if (!inFlight.current && answer !== serverAnswer.current) send(answer)
  }

  return (
    <div className="flex flex-col gap-2">
      <RadioGroup name="resposta" legend="A sua resposta" value={choice} onChange={choose} columns={2}
        options={question.options.map((o) => ({ value: o, label: ANSWER_LABEL[o], hint: ANSWER_HINT[o] }))} />
      {/* Always rendered, so screen readers announce it when a save fills it in. */}
      <p role="status" className="flex min-h-5 items-center gap-1.5 text-[0.8125rem] font-semibold text-ink-soft">
        {saving ? 'A guardar…' : savedAt && <><Check className="size-4 text-signal" aria-hidden />Resposta guardada às {timeOf(savedAt)}</>}
      </p>
    </div>
  )
}

function ReadOnlyAnswer({ answer, answeredAt }: { answer: AnswerValue | null; answeredAt: string | null }) {
  return (
    <div className="rounded-[14px] bg-paper-soft p-4">
      <Eyebrow>Resposta</Eyebrow>
      <p className="mt-1 text-[1rem] font-bold">{answer ? ANSWER_LABEL[answer] : 'Sem resposta'}</p>
      {answeredAt && <p className="mt-1 text-[0.8125rem] text-muted">Respondida a {formatDateTime(answeredAt)}</p>}
    </div>
  )
}

/** US33: what the question means, with an example. Written by the team in the catalogue, not generated by AI. */
function Explanation({ step }: { step: Step }) {
  return (
    <section aria-labelledby="explicacao" className="flex flex-col gap-3.5 self-start rounded-[18px] bg-brand-deep p-[26px] max-md:p-5">
      <h2 id="explicacao" className="text-[1.1875rem] font-extrabold leading-snug text-white">O que significa esta pergunta?</h2>
      <p className="text-[0.875rem] leading-relaxed text-brand-soft">{step.question.explanation}</p>
      <div className="rounded-[12px] bg-brand px-4 py-3.5">
        <h3 className="text-[0.8125rem] font-bold text-white">Exemplo</h3>
        <p className="text-[0.8125rem] leading-relaxed text-brand-soft">{step.question.example}</p>
      </div>
    </section>
  )
}

const timeOf = (iso: string) =>
  new Intl.DateTimeFormat(LOCALE, { hour: '2-digit', minute: '2-digit', timeZone: TIME_ZONE }).format(new Date(iso))
