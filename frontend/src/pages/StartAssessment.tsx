import { useState, type FormEvent } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { ApiError, errorMessage, formatNumber, useApiQuery, useHost } from '@alinha/plugin-sdk'
import { Button, Card, Checkbox, Field, Note, RadioGroup, SkeletonRows, TextInput } from '@alinha/ui'
import { FRAMEWORK, KEYS } from '../api'
import { useStartAssessment } from '../assessmentQueries'
import {
  ENTITY_TYPE_GROUPS,
  preselectedTypes,
  suggestedGroups,
  toApplicabilityInput,
  validateScope,
  type EntityTypeGroup,
  type ScopeErrors,
  type ScopeForm,
} from '../scope'

/** The part of `GET /organization/profile` this form uses (organization plugin, read over the API). */
interface ProfileFacts {
  sector: string
  subsector: string | null
  employees: number
  turnover: number
}

const YES_NO = [
  { value: 'yes', label: 'Sim' },
  { value: 'no', label: 'Não' },
] as const

/**
 * Starts the NIS2 assessment. The size comes from the organization's registration when there is one; the user
 * confirms it and answers the few facts the registration does not have. The server works out the NIS2
 * classification from them and asks only the questions of that level.
 */
export function StartAssessment() {
  const { can } = useHost()
  // A missing profile (not registered, or no permission to read it) just leaves the form empty.
  const profile = useApiQuery<ProfileFacts>(['assessment', 'organization-profile'], '/organization/profile', {
    enabled: can('organization'),
  })
  if (can('organization') && profile.isPending) return <SkeletonRows rows={4} label="A preparar a avaliação…" />
  return <StartForm profile={profile.data} />
}

function StartForm({ profile }: { profile?: ProfileFacts }) {
  const queryClient = useQueryClient()
  const start = useStartAssessment()
  const suggested = suggestedGroups(profile?.sector, profile?.subsector)
  const [form, setForm] = useState<ScopeForm>({
    employees: profile ? String(profile.employees) : '',
    turnover: profile ? formatNumber(profile.turnover) : '',
    entityTypes: preselectedTypes(suggested),
    processesPersonalData: null,
    usesAi: null,
  })
  const [showAll, setShowAll] = useState(suggested.length === 0)
  const [errors, setErrors] = useState<ScopeErrors>({})
  const groups = showAll ? ENTITY_TYPE_GROUPS : suggested

  const set = <K extends keyof ScopeForm>(key: K, value: ScopeForm[K]) => setForm((f) => ({ ...f, [key]: value }))
  // From the latest state, so two quick clicks never drop one.
  const toggleType = (id: string, on: boolean) => setForm((f) => ({
    ...f,
    entityTypes: on ? [...f.entityTypes.filter((t) => t !== id), id] : f.entityTypes.filter((t) => t !== id),
  }))

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const found = validateScope(form)
    setErrors(found)
    if (Object.keys(found).length > 0) return
    start.mutate(
      { framework: FRAMEWORK, scope: toApplicabilityInput(form) },
      {
        // An assessment of NIS2 is already open (another tab or person started it): reload the list, which then
        // shows it instead of this form.
        onError: (error) => { if (alreadyOpen(error)) void queryClient.invalidateQueries({ queryKey: KEYS.list }) },
      },
    )
  }

  if (start.isSuccess) {
    return <Note>Avaliação criada. A abrir…</Note>
  }

  return (
    <Card className="flex max-w-[760px] flex-col gap-5 px-7 py-6 max-md:px-5">
      <div className="flex flex-col gap-1.5">
        <h2 className="text-[1.25rem] font-extrabold">Começar a avaliação NIS2</h2>
        <p className="text-[0.9375rem] text-ink-soft">
          Antes das perguntas, confirme alguns dados da organização. Servem para saber que regras se aplicam e que
          perguntas lhe fazer. {profile && 'Os números vêm do registo da organização.'}
        </p>
      </div>
      <form noValidate className="flex flex-col gap-5" onSubmit={submit} aria-label="Dados para a avaliação">
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Número de trabalhadores" error={errors.employees}>
            {(p) => <TextInput id={p.id} aria-describedby={p.describedBy} invalid={p.invalid} inputMode="numeric"
              value={form.employees} onChange={(e) => set('employees', e.target.value)} />}
          </Field>
          <Field label="Volume de negócios anual (euros)" error={errors.turnover}>
            {(p) => <TextInput id={p.id} aria-describedby={p.describedBy} invalid={p.invalid} inputMode="numeric"
              value={form.turnover} onChange={(e) => set('turnover', e.target.value)} />}
          </Field>
        </div>

        <fieldset className="flex flex-col gap-3">
          <legend className="mb-1.5 text-[0.8125rem] font-semibold text-ink-soft">O que faz a organização?</legend>
          <p className="text-[0.8125rem] text-muted">
            Marque as atividades que descrevem a organização. Se nenhuma se aplicar, deixe tudo por marcar.
          </p>
          {groups.map((group) => (
            <TypeGroup key={`${group.sector}/${group.subsector ?? ''}`} group={group} selected={form.entityTypes}
              onToggle={toggleType} showLabel={showAll || groups.length > 1} />
          ))}
          {!showAll && (
            <Button variant="link" className="self-start" onClick={() => setShowAll(true)}>Ver todas as atividades</Button>
          )}
        </fieldset>

        <RadioGroup name="dados-pessoais" legend="A organização trata dados pessoais (de clientes, trabalhadores ou outras pessoas)?"
          value={yesNo(form.processesPersonalData)} onChange={(v) => set('processesPersonalData', v === 'yes')}
          options={[...YES_NO]} columns={2} error={errors.processesPersonalData} />
        <RadioGroup name="ia" legend="A organização usa sistemas de inteligência artificial no trabalho?"
          value={yesNo(form.usesAi)} onChange={(v) => set('usesAi', v === 'yes')}
          options={[...YES_NO]} columns={2} error={errors.usesAi} />

        {start.isError && <StartError error={start.error} />}
        <Button type="submit" className="self-start" loading={start.isPending}>Começar a avaliação</Button>
      </form>
    </Card>
  )
}

function TypeGroup({ group, selected, onToggle, showLabel }: {
  group: EntityTypeGroup
  selected: string[]
  onToggle: (id: string, on: boolean) => void
  showLabel: boolean
}) {
  return (
    <div className="flex flex-col gap-2">
      {showLabel && <p className="text-[0.8125rem] font-bold text-ink">{group.label}</p>}
      {group.types.map((t) => (
        <Checkbox key={t.id} label={t.label} checked={selected.includes(t.id)} onChange={(e) => onToggle(t.id, e.target.checked)} />
      ))}
    </div>
  )
}

const yesNo = (value: boolean | null) => (value === null ? null : value ? 'yes' : 'no')

const alreadyOpen = (error: unknown) =>
  error instanceof ApiError && error.status === 409 && error.problem?.code === 'ASSESSMENT_ALREADY_OPEN'

function StartError({ error }: { error: Error }) {
  if (error instanceof ApiError && error.problem?.code === 'FRAMEWORK_NOT_IN_SCOPE') {
    return (
      <Note>
        Pelas respostas, a organização não está abrangida pelo regime de cibersegurança (NIS2), por isso não há
        perguntas a responder. Se acha que está, confirme os dados ou fale com a sua consultora.
      </Note>
    )
  }
  return <p role="alert" className="rounded-[10px] bg-risk-high px-3.5 py-2.5 text-sm font-semibold text-risk-high-ink">{errorMessage(error)}</p>
}
