import { useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import { ArrowRight, CheckCircle2, ChevronDown, ClipboardCheck, Clock } from 'lucide-react'
import { useHost } from '@alinha/plugin-sdk'
import { Badge, Card, cn, EmptyState, ErrorState, Eyebrow, Note, PageHeader, SkeletonRows } from '@alinha/ui'
import { FRAMEWORK, type AssessmentDetail, type AssessmentStatus } from '../api'
import { useAssessmentDetail, useAssessmentList } from '../assessmentQueries'
import { ProgressBar } from '../components/ProgressBar'
import { buildQuestionnaire, CLASSIFICATION_LABEL, LEVEL_LABEL, resumeStep, type Questionnaire } from '../questionnaire'
import { StartAssessment } from './StartAssessment'

const TITLE = 'Avaliação'

export const STATUS_LABEL: Record<AssessmentStatus, string> = {
  draft: 'Em curso',
  submitted: 'Submetida · a aguardar a consultora',
  evaluated: 'Avaliada · a aguardar validação',
  validated: 'Validada pela consultora',
}

/** The NIS2 assessment: start it, or see the progress per section and continue where the user stopped. */
export function AssessmentPage() {
  const { can } = useHost()
  const list = useAssessmentList()
  // Newest first (the API sorts by updatedAt): the one the user worked on last.
  const current = list.data?.items.find((a) => a.framework === FRAMEWORK)
  const detail = useAssessmentDetail(current?.id)

  const header = <PageHeader title={TITLE} subtitle="Questionário sobre as práticas de cibersegurança da organização, alinhado com o regime NIS2." />
  if (list.isError) return <>{header}<ErrorState error={list.error} onRetry={() => void list.refetch()} /></>
  if (list.isPending) return <>{header}<SkeletonRows rows={6} label="A carregar a avaliação…" /></>

  if (!current) {
    return (
      <>
        {header}
        {can('assessment', 'start') ? <StartAssessment /> : (
          <EmptyState icon={<ClipboardCheck className="size-5" aria-hidden />} title="Ainda não há avaliação">
            A pessoa responsável pela organização começa a avaliação. Depois, aparece aqui.
          </EmptyState>
        )}
      </>
    )
  }

  if (detail.isError) return <>{header}<ErrorState error={detail.error} onRetry={() => void detail.refetch()} /></>
  if (detail.isPending) return <>{header}<SkeletonRows rows={6} label="A carregar a avaliação…" /></>
  return <>{header}<Overview assessment={detail.data} /></>
}

function Overview({ assessment }: { assessment: AssessmentDetail }) {
  const questionnaire = buildQuestionnaire(assessment)
  const { answered, total } = questionnaire
  const resume = resumeStep(questionnaire)
  const draft = assessment.status === 'draft'
  const cta = !draft ? 'Ver respostas' : answered < total ? 'Responder à próxima pergunta' : 'Rever respostas'
  const questionLink = (questionId: string) => `${assessment.id}/questions/${encodeURIComponent(questionId)}`

  return (
    <div className="flex max-w-[860px] flex-col gap-4">
      <ProgressCard to={resume && questionLink(resume.question.id)} label={cta}>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <Eyebrow>{assessment.catalogueTitle}</Eyebrow>
          <Badge tone={draft ? 'signal' : 'neutral'}>{STATUS_LABEL[assessment.status]}</Badge>
        </div>
        <p className="text-3xl font-extrabold">
          {answered} <span className="text-base font-semibold text-ink-soft">de {total} perguntas respondidas</span>
        </p>
        <ProgressBar value={answered} max={total} label="Progresso da avaliação" />
        <p className="text-sm text-ink-soft">
          Classificação NIS2 indicativa: <strong>{CLASSIFICATION_LABEL[assessment.nis2Classification]}</strong>. Por
          isso, as perguntas vão até ao nível {LEVEL_LABEL[assessment.maxLevel]}. A classificação oficial é feita pelo CNCS.
        </p>
      </ProgressCard>

      {!draft && <Note>A avaliação já foi submetida. As respostas já não podem ser alteradas.</Note>}
      {draft && total > 0 && answered === total && (
        <Note>Respondeu a todas as perguntas. Pode rever as respostas enquanto a avaliação não for submetida.</Note>
      )}

      <SectionsTable questionnaire={questionnaire} questionLink={questionLink} />
    </div>
  )
}

/**
 * The progress summary. While there is a question to open, the whole card is one link to it (Inês's usability
 * change in the prototype), named by `label` so a screen reader does not read the whole card as the link text.
 */
function ProgressCard({ to, label, children }: { to?: string; label: string; children: ReactNode }) {
  if (!to) return <Card className="flex flex-col gap-3">{children}</Card>
  return (
    <Link to={to} aria-label={label}
      className="group flex items-center gap-4 rounded-[14px] border border-line-soft bg-paper px-5 py-[18px] transition-[border-color,box-shadow] hover:border-brand hover:shadow-[0_2px_10px_rgb(var(--shadow-rgb)/0.06)]">
      <div className="flex min-w-0 flex-1 flex-col gap-3">{children}</div>
      <span className="grid size-10 shrink-0 place-items-center rounded-full bg-brand text-white transition-transform group-hover:translate-x-0.5">
        <ArrowRight className="size-5" aria-hidden />
      </span>
    </Link>
  )
}

/** The sections, collapsed by default (Inês's usability change): each row opens to its progress and link. */
function SectionsTable({ questionnaire, questionLink }: { questionnaire: Questionnaire; questionLink: (questionId: string) => string }) {
  const [open, setOpen] = useState(false)
  const [expanded, setExpanded] = useState<string | null>(null)
  const { sections, total } = questionnaire
  return (
    <section aria-labelledby="sections-title" className="rounded-[14px] border border-line-soft bg-paper">
      <button type="button" onClick={() => setOpen((o) => !o)} aria-expanded={open} aria-controls="sections-list"
        className="flex w-full items-center gap-3 px-5 py-3.5 text-left">
        <span className="min-w-0 flex-1">
          <span id="sections-title" className="block text-sm font-bold">Perguntas por secção</span>
          <span className="block text-[0.8125rem] text-ink-soft">{sections.length} secções · {total} perguntas</span>
        </span>
        <ChevronDown className={cn('size-4 text-ink-soft transition-transform', open && 'rotate-180')} aria-hidden />
      </button>
      {open && (
        <ul id="sections-list" className="border-t border-line-soft" aria-label="Secções da avaliação">
          {sections.map((section) => {
            const complete = section.answered === section.total
            const isOpen = expanded === section.id
            const panel = `section-${section.id}`
            const first = resumeStep(questionnaire, section.id)
            return (
              <li key={section.id} className="border-b border-line-soft last:border-b-0">
                <button type="button" onClick={() => setExpanded(isOpen ? null : section.id)} aria-expanded={isOpen} aria-controls={panel}
                  className="flex w-full items-center gap-3 px-5 py-3 text-left hover:bg-paper-soft">
                  {complete
                    ? <CheckCircle2 className="size-4 shrink-0 text-brand" role="img" aria-label="Completa" />
                    : <Clock className="size-4 shrink-0 text-muted" role="img" aria-label="Por completar" />}
                  {' '}<span className="min-w-0 flex-1 text-sm font-bold">{section.name}</span>
                  {' '}<span className="w-12 shrink-0 text-right text-sm font-semibold">{section.answered}/{section.total}</span>
                  <ChevronDown className={cn('size-4 shrink-0 text-ink-soft transition-transform', isOpen && 'rotate-180')} aria-hidden />
                </button>
                {isOpen && (
                  <div id={panel} className="flex flex-col gap-2.5 px-5 pb-4">
                    <ProgressBar value={section.answered} max={section.total} label={`Progresso de ${section.name}`} />
                    {first && (
                      <Link to={questionLink(first.question.id)} className="self-start text-sm font-bold text-brand hover:underline">
                        {complete ? 'Ver respostas' : 'Responder'}{' '}<span className="sr-only">de {section.name}</span>
                      </Link>
                    )}
                  </div>
                )}
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}
