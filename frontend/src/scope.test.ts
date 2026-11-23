import { describe, expect, it } from 'vitest'
import {
  ENTITY_TYPE_GROUPS,
  parseWholeNumber,
  preselectedTypes,
  suggestedGroups,
  toApplicabilityInput,
  validateScope,
  type ScopeForm,
} from './scope'

/** Every value of `ApplicabilityInput.nis2.entityTypes` in the contract (alinha#155). */
const CONTRACT_TYPES = [
  'ELECTRICITY_SUPPLIER', 'ELECTRICITY_DISTRIBUTION_OPERATOR', 'ELECTRICITY_TRANSMISSION_OPERATOR',
  'ELECTRICITY_PRODUCER', 'ELECTRICITY_MARKET_OPERATOR', 'ELECTRICITY_MARKET_PARTICIPANT',
  'CHARGING_POINT_OPERATOR', 'DISTRICT_HEATING_COOLING', 'OIL_PIPELINE_OPERATOR', 'OIL_OPERATOR',
  'OIL_CENTRAL_STOCKHOLDING', 'GAS_SUPPLIER', 'GAS_DISTRIBUTION_OPERATOR', 'GAS_TRANSMISSION_OPERATOR',
  'GAS_STORAGE_OPERATOR', 'LNG_OPERATOR', 'NATURAL_GAS_UNDERTAKING', 'GAS_REFINING_OPERATOR',
  'HYDROGEN_OPERATOR', 'AIR_CARRIER', 'AIRPORT', 'AIR_TRAFFIC_CONTROL', 'RAIL_INFRASTRUCTURE_MANAGER',
  'RAILWAY_UNDERTAKING', 'WATER_TRANSPORT_COMPANY', 'PORT_MANAGING_BODY', 'VESSEL_TRAFFIC_SERVICE',
  'ROAD_TRAFFIC_AUTHORITY', 'INTELLIGENT_TRANSPORT_SYSTEMS', 'CREDIT_INSTITUTION', 'TRADING_VENUE',
  'CENTRAL_COUNTERPARTY', 'HEALTHCARE_PROVIDER', 'EU_REFERENCE_LABORATORY', 'MEDICINAL_PRODUCT_RESEARCH',
  'DRINKING_WATER', 'WASTE_WATER', 'INTERNET_EXCHANGE_POINT', 'DNS_SERVICE_PROVIDER', 'TLD_NAME_REGISTRY',
  'CLOUD_COMPUTING_PROVIDER', 'DATA_CENTRE_PROVIDER', 'CONTENT_DELIVERY_NETWORK',
  'QUALIFIED_TRUST_SERVICE_PROVIDER', 'TRUST_SERVICE_PROVIDER', 'PUBLIC_ELECTRONIC_COMMUNICATIONS_NETWORK',
  'PUBLIC_ELECTRONIC_COMMUNICATIONS_SERVICE', 'MANAGED_SERVICE_PROVIDER',
  'MANAGED_SECURITY_SERVICE_PROVIDER', 'SPACE_GROUND_INFRASTRUCTURE', 'POSTAL_COURIER', 'WASTE_MANAGEMENT',
  'CHEMICALS', 'FOOD', 'MEDICAL_DEVICES', 'ELECTRONICS_MANUFACTURING', 'ELECTRICAL_EQUIPMENT_MANUFACTURING',
  'MACHINERY_MANUFACTURING', 'MOTOR_VEHICLE_MANUFACTURING', 'OTHER_TRANSPORT_EQUIPMENT_MANUFACTURING',
  'ONLINE_MARKETPLACE', 'ONLINE_SEARCH_ENGINE', 'SOCIAL_NETWORK_PLATFORM', 'RESEARCH_ORGANISATION',
]

const FORM: ScopeForm = {
  employees: '120',
  turnover: '18 500 000',
  entityTypes: ['MOTOR_VEHICLE_MANUFACTURING'],
  processesPersonalData: true,
  usesAi: false,
}

describe('ENTITY_TYPE_GROUPS', () => {
  it('lists every entity type of the contract exactly once', () => {
    const ids = ENTITY_TYPE_GROUPS.flatMap((g) => g.types.map((t) => t.id))

    expect([...ids].sort()).toEqual([...CONTRACT_TYPES].sort())
  })
})

describe('suggestedGroups', () => {
  it('narrows to the subsector when the organization registered one', () => {
    expect(suggestedGroups('energia', 'gas').map((g) => g.label)).toEqual(['Gás'])
  })

  it('offers every group of the sector when there is no subsector', () => {
    expect(suggestedGroups('transportes', null).map((g) => g.subsector)).toEqual(['aereo', 'ferroviario', 'maritimo', 'rodoviario'])
  })

  it('offers nothing for "outro" or no sector', () => {
    expect(suggestedGroups('outro', null)).toEqual([])
    expect(suggestedGroups(undefined)).toEqual([])
  })
})

describe('preselectedTypes', () => {
  it('ticks the only type of the sector', () => {
    expect(preselectedTypes(suggestedGroups('industria', 'automoveis'))).toEqual(['MOTOR_VEHICLE_MANUFACTURING'])
  })

  it('ticks nothing when the user has to choose', () => {
    expect(preselectedTypes(suggestedGroups('infra_digitais'))).toEqual([])
  })
})

describe('parseWholeNumber', () => {
  it.each([['18 500 000', 18500000], ['18.500.000', 18500000], ['0', 0], [' 120 ', 120]])('reads %j', (input, expected) => {
    expect(parseWholeNumber(input)).toBe(expected)
  })

  it.each(['', 'abc', '-5', '1,5', '99999999999999999999'])('rejects %j', (input) => {
    expect(parseWholeNumber(input)).toBeNull()
  })
})

describe('validateScope', () => {
  it('accepts a complete form, with no entity type ticked', () => {
    expect(validateScope({ ...FORM, entityTypes: [] })).toEqual({})
  })

  it('asks for the size and both yes/no answers', () => {
    expect(Object.keys(validateScope({ employees: '', turnover: 'x', entityTypes: [], processesPersonalData: null, usesAi: null })))
      .toEqual(['employees', 'turnover', 'processesPersonalData', 'usesAi'])
  })
})

describe('toApplicabilityInput', () => {
  it('sends the numbers and answers the way the contract expects', () => {
    expect(toApplicabilityInput(FORM)).toEqual({
      size: { employees: 120, annualTurnoverEur: 18500000 },
      nis2: { entityTypes: ['MOTOR_VEHICLE_MANUFACTURING'] },
      gdpr: { processesPersonalData: true },
      aiAct: { usesAiSystems: false },
    })
  })
})
