import type { ApplicabilityInput } from './api'

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

export const ENTITY_TYPE_GROUPS: readonly EntityTypeGroup[] = [
  { sector: 'energia', subsector: 'eletricidade', label: 'Eletricidade', types: [
    { id: 'ELECTRICITY_SUPPLIER', label: 'Comercialização de eletricidade' },
    { id: 'ELECTRICITY_DISTRIBUTION_OPERATOR', label: 'Operador da rede de distribuição de eletricidade' },
    { id: 'ELECTRICITY_TRANSMISSION_OPERATOR', label: 'Operador da rede de transporte de eletricidade' },
    { id: 'ELECTRICITY_PRODUCER', label: 'Produção de eletricidade' },
    { id: 'ELECTRICITY_MARKET_OPERATOR', label: 'Operador nomeado do mercado da eletricidade' },
    { id: 'ELECTRICITY_MARKET_PARTICIPANT', label: 'Participante no mercado da eletricidade (agregação, armazenamento ou resposta da procura)' },
    { id: 'CHARGING_POINT_OPERATOR', label: 'Operador de pontos de carregamento' },
  ] },
  { sector: 'energia', subsector: 'aquecimento', label: 'Aquecimento e arrefecimento urbano', types: [
    { id: 'DISTRICT_HEATING_COOLING', label: 'Operador de aquecimento ou arrefecimento urbano' },
  ] },
  { sector: 'energia', subsector: 'petroleo', label: 'Petróleo', types: [
    { id: 'OIL_PIPELINE_OPERATOR', label: 'Operador de oleodutos' },
    { id: 'OIL_OPERATOR', label: 'Produção, refinação, tratamento, armazenamento ou transporte de petróleo' },
    { id: 'OIL_CENTRAL_STOCKHOLDING', label: 'Entidade central de armazenagem de petróleo' },
  ] },
  { sector: 'energia', subsector: 'gas', label: 'Gás', types: [
    { id: 'GAS_SUPPLIER', label: 'Comercialização de gás' },
    { id: 'GAS_DISTRIBUTION_OPERATOR', label: 'Operador da rede de distribuição de gás' },
    { id: 'GAS_TRANSMISSION_OPERATOR', label: 'Operador da rede de transporte de gás' },
    { id: 'GAS_STORAGE_OPERATOR', label: 'Operador de armazenamento de gás' },
    { id: 'LNG_OPERATOR', label: 'Operador de terminal de gás natural liquefeito' },
    { id: 'NATURAL_GAS_UNDERTAKING', label: 'Empresa de gás natural' },
    { id: 'GAS_REFINING_OPERATOR', label: 'Refinação e tratamento de gás natural' },
  ] },
  { sector: 'energia', subsector: 'hidrogenio', label: 'Hidrogénio', types: [
    { id: 'HYDROGEN_OPERATOR', label: 'Produção, armazenamento ou transporte de hidrogénio' },
  ] },
  { sector: 'transportes', subsector: 'aereo', label: 'Transporte aéreo', types: [
    { id: 'AIR_CARRIER', label: 'Transportadora aérea' },
    { id: 'AIRPORT', label: 'Entidade gestora de aeroporto' },
    { id: 'AIR_TRAFFIC_CONTROL', label: 'Serviço de controlo de tráfego aéreo' },
  ] },
  { sector: 'transportes', subsector: 'ferroviario', label: 'Transporte ferroviário', types: [
    { id: 'RAIL_INFRASTRUCTURE_MANAGER', label: 'Gestor de infraestrutura ferroviária' },
    { id: 'RAILWAY_UNDERTAKING', label: 'Empresa ferroviária' },
  ] },
  { sector: 'transportes', subsector: 'maritimo', label: 'Transporte marítimo e fluvial', types: [
    { id: 'WATER_TRANSPORT_COMPANY', label: 'Empresa de transporte marítimo ou fluvial' },
    { id: 'PORT_MANAGING_BODY', label: 'Entidade gestora de porto' },
    { id: 'VESSEL_TRAFFIC_SERVICE', label: 'Serviço de tráfego marítimo' },
  ] },
  { sector: 'transportes', subsector: 'rodoviario', label: 'Transporte rodoviário', types: [
    { id: 'ROAD_TRAFFIC_AUTHORITY', label: 'Autoridade rodoviária responsável pelo controlo da gestão do tráfego' },
    { id: 'INTELLIGENT_TRANSPORT_SYSTEMS', label: 'Operador de sistemas de transporte inteligentes' },
  ] },
  { sector: 'bancario', label: 'Setor bancário', types: [
    { id: 'CREDIT_INSTITUTION', label: 'Instituição de crédito' },
  ] },
  { sector: 'financeiro', label: 'Infraestruturas do mercado financeiro', types: [
    { id: 'TRADING_VENUE', label: 'Operador de plataforma de negociação' },
    { id: 'CENTRAL_COUNTERPARTY', label: 'Contraparte central' },
  ] },
  { sector: 'saude', label: 'Saúde', types: [
    { id: 'HEALTHCARE_PROVIDER', label: 'Prestador de cuidados de saúde' },
    { id: 'EU_REFERENCE_LABORATORY', label: 'Laboratório de referência da UE' },
    { id: 'MEDICINAL_PRODUCT_RESEARCH', label: 'Investigação e desenvolvimento de medicamentos' },
  ] },
  { sector: 'agua', label: 'Água potável', types: [
    { id: 'DRINKING_WATER', label: 'Fornecimento e distribuição de água para consumo humano' },
  ] },
  { sector: 'aguas_residuais', label: 'Águas residuais', types: [
    { id: 'WASTE_WATER', label: 'Recolha, eliminação ou tratamento de águas residuais' },
  ] },
  { sector: 'infra_digitais', label: 'Infraestruturas digitais', types: [
    { id: 'INTERNET_EXCHANGE_POINT', label: 'Ponto de troca de tráfego (IXP)' },
    { id: 'DNS_SERVICE_PROVIDER', label: 'Prestador de serviços de DNS' },
    { id: 'TLD_NAME_REGISTRY', label: 'Registo de nomes de domínio de topo' },
    { id: 'CLOUD_COMPUTING_PROVIDER', label: 'Prestador de serviços de computação em nuvem' },
    { id: 'DATA_CENTRE_PROVIDER', label: 'Prestador de serviços de centro de dados' },
    { id: 'CONTENT_DELIVERY_NETWORK', label: 'Rede de distribuição de conteúdos' },
    { id: 'QUALIFIED_TRUST_SERVICE_PROVIDER', label: 'Prestador qualificado de serviços de confiança' },
    { id: 'TRUST_SERVICE_PROVIDER', label: 'Prestador de serviços de confiança (não qualificado)' },
    { id: 'PUBLIC_ELECTRONIC_COMMUNICATIONS_NETWORK', label: 'Rede pública de comunicações eletrónicas' },
    { id: 'PUBLIC_ELECTRONIC_COMMUNICATIONS_SERVICE', label: 'Serviço de comunicações eletrónicas acessível ao público' },
  ] },
  { sector: 'tic', label: 'Gestão de serviços de TIC (entre empresas)', types: [
    { id: 'MANAGED_SERVICE_PROVIDER', label: 'Prestador de serviços geridos' },
    { id: 'MANAGED_SECURITY_SERVICE_PROVIDER', label: 'Prestador de serviços de segurança geridos' },
  ] },
  { sector: 'espaco', label: 'Espaço', types: [
    { id: 'SPACE_GROUND_INFRASTRUCTURE', label: 'Operador de infraestruturas terrestres de apoio a serviços espaciais' },
  ] },
  { sector: 'postais', label: 'Serviços postais e de estafeta', types: [
    { id: 'POSTAL_COURIER', label: 'Prestador de serviços postais ou de estafeta' },
  ] },
  { sector: 'residuos', label: 'Gestão de resíduos', types: [
    { id: 'WASTE_MANAGEMENT', label: 'Gestão de resíduos' },
  ] },
  { sector: 'quimicos', label: 'Produtos químicos', types: [
    { id: 'CHEMICALS', label: 'Produção, fabrico ou distribuição de produtos químicos' },
  ] },
  { sector: 'alimentar', label: 'Produtos alimentares', types: [
    { id: 'FOOD', label: 'Produção, transformação ou distribuição grossista de produtos alimentares' },
  ] },
  { sector: 'industria', subsector: 'dispositivos_medicos', label: 'Fabrico de dispositivos médicos', types: [
    { id: 'MEDICAL_DEVICES', label: 'Fabrico de dispositivos médicos' },
  ] },
  { sector: 'industria', subsector: 'informatica', label: 'Fabrico de equipamentos informáticos, eletrónicos e óticos', types: [
    { id: 'ELECTRONICS_MANUFACTURING', label: 'Fabrico de equipamentos informáticos, eletrónicos e óticos' },
  ] },
  { sector: 'industria', subsector: 'eletrico', label: 'Fabrico de equipamento elétrico', types: [
    { id: 'ELECTRICAL_EQUIPMENT_MANUFACTURING', label: 'Fabrico de equipamento elétrico' },
  ] },
  { sector: 'industria', subsector: 'maquinas', label: 'Fabrico de máquinas e equipamentos', types: [
    { id: 'MACHINERY_MANUFACTURING', label: 'Fabrico de máquinas e equipamentos' },
  ] },
  { sector: 'industria', subsector: 'automoveis', label: 'Fabrico de veículos automóveis, reboques e componentes', types: [
    { id: 'MOTOR_VEHICLE_MANUFACTURING', label: 'Fabrico de veículos automóveis, reboques e componentes' },
  ] },
  { sector: 'industria', subsector: 'outro_transporte', label: 'Fabrico de outro equipamento de transporte', types: [
    { id: 'OTHER_TRANSPORT_EQUIPMENT_MANUFACTURING', label: 'Fabrico de outro equipamento de transporte' },
  ] },
  { sector: 'digitais', label: 'Prestadores de serviços digitais', types: [
    { id: 'ONLINE_MARKETPLACE', label: 'Mercado em linha' },
    { id: 'ONLINE_SEARCH_ENGINE', label: 'Motor de pesquisa em linha' },
    { id: 'SOCIAL_NETWORK_PLATFORM', label: 'Plataforma de redes sociais' },
  ] },
  { sector: 'investigacao', label: 'Investigação', types: [
    { id: 'RESEARCH_ORGANISATION', label: 'Organismo de investigação' },
  ] },
]

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
