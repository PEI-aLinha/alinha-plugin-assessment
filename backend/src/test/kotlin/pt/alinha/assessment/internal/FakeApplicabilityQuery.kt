package pt.alinha.assessment.internal

import pt.alinha.core.compliance.ApplicabilityInput
import pt.alinha.core.compliance.ApplicabilityQuery
import pt.alinha.core.compliance.ApplicabilityResult
import pt.alinha.core.compliance.ApplicabilityRules
import pt.alinha.core.compliance.Nis2Classification
import pt.alinha.pluginapi.TenantId
import tools.jackson.databind.json.JsonMapper
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Stands in for the organization plugin: each test declares the scope its organization answered. */
class FakeApplicabilityQuery(private val rules: ApplicabilityRules, private val json: JsonMapper) : ApplicabilityQuery {
    private val results = ConcurrentHashMap<TenantId, ApplicabilityResult>()

    /** [scope] is an `ApplicabilityInput` as JSON; [official] the CNCS qualification, when registered. */
    fun declare(tenant: UUID, scope: String, official: Nis2Classification? = null) {
        val result = rules.assess(json.readValue(scope, ApplicabilityInput::class.java))
        results[TenantId(tenant)] = result.copy(officialNis2Classification = official)
    }

    override fun current(tenantId: TenantId): ApplicabilityResult? = results[tenantId]
}
