import type { ApplicabilityInput } from './api'
import entityTypeGroups from './entityTypes.json'

/**
 * NIS2 entity types (contract `ApplicabilityInput.nis2.entityTypes`), grouped by the sectors of the organization
 * registration (`sector` / `subsector` ids of the organization plugin, `Sectors.kt`). The registration only knows
 * the sector, so the start form suggests that sector's types and the user ticks the ones that apply.
 */
export interface EntityTypeGroup {
  sector: string
  subsector?: string
  label: string
  types: ReadonlyArray<{ id: string; label: string }>
}

/** The table itself is data (`entityTypes.json`): labels in European Portuguese, ids as in the contract. */
export const ENTITY_TYPE_GROUPS: readonly EntityTypeGroup[] = entityTypeGroups

/** The groups for a registered sector (and subsector, when it has one). Empty for "outro" or an unknown sector. */
export function suggestedGroups(sector?: string | null, subsector?: string | null): EntityTypeGroup[] {
  if (!sector) return []
  const inSector = ENTITY_TYPE_GROUPS.filter((g) => g.sector === sector)
  const exact = subsector ? inSector.filter((g) => g.subsector === subsector) : []
  return exact.length > 0 ? exact : inSector
}

/** When the sector leaves a single type, it is ticked from the start. */
export function preselectedTypes(groups: readonly EntityTypeGroup[]): string[] {
  const types = groups.flatMap((g) => g.types)
  return types.length === 1 ? [types[0].id] : []
}

/** The start form, as the user fills it in (numbers as typed). */
export interface ScopeForm {
  employees: string
  turnover: string
  entityTypes: string[]
  processesPersonalData: boolean | null
  usesAi: boolean | null
}

export type ScopeErrors = Partial<Record<'employees' | 'turnover' | 'processesPersonalData' | 'usesAi', string>>

/** "18 500 000", "18.500.000" or "18500000" → 18500000; anything else (or empty) → null. */
export function parseWholeNumber(value: string): number | null {
  const digits = value.replace(/[\s.]/g, '') // \s also covers the no-break spaces of formatNumber
  if (!/^\d+$/.test(digits)) return null
  const n = Number(digits)
  return Number.isSafeInteger(n) ? n : null
}

export function validateScope(form: ScopeForm): ScopeErrors {
  const errors: ScopeErrors = {}
  if (parseWholeNumber(form.employees) === null) errors.employees = 'Indique o número de trabalhadores (só algarismos).'
  if (parseWholeNumber(form.turnover) === null) errors.turnover = 'Indique o volume de negócios anual em euros (só algarismos).'
  if (form.processesPersonalData === null) errors.processesPersonalData = 'Escolha uma opção.'
  if (form.usesAi === null) errors.usesAi = 'Escolha uma opção.'
  return errors
}

/** The `scope` of startAssessment. Call only with a form that `validateScope` accepts. */
export function toApplicabilityInput(form: ScopeForm): ApplicabilityInput {
  return {
    size: { employees: parseWholeNumber(form.employees) ?? 0, annualTurnoverEur: parseWholeNumber(form.turnover) ?? 0 },
    nis2: { entityTypes: [...form.entityTypes] },
    gdpr: { processesPersonalData: form.processesPersonalData === true },
    aiAct: { usesAiSystems: form.usesAi === true },
  }
}
