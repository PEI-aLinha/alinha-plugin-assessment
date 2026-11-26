package pt.alinha.assessment.internal

import com.jayway.jsonpath.JsonPath
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.contains
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.context.request.RequestContextHolder
import pt.alinha.assessment.internal.persistence.AssessmentEntity
import pt.alinha.assessment.internal.persistence.AssessmentRepository
import pt.alinha.core.compliance.ApplicabilityRules
import pt.alinha.core.compliance.Nis2Classification
import pt.alinha.core.tenant.TenantContext
import pt.alinha.pluginapi.TenantId
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

private const val ASSESSMENTS = "/api/assessment/assessments"

/** A medium machinery manufacturer: NIS2 annex II, so an important entity. */
private const val IMPORTANT_SCOPE = """{"size": {"employees": 120, "annualTurnoverEur": 18500000},
    "nis2": {"entityTypes": ["MACHINERY_MANUFACTURING"]}, "gdpr": {"processesPersonalData": true}, "aiAct": {}}"""

/** A DNS provider is essential whatever its size. */
private const val ESSENTIAL_SCOPE = """{"size": {"employees": 5, "annualTurnoverEur": 500000},
    "nis2": {"entityTypes": ["DNS_SERVICE_PROVIDER"]}, "gdpr": {"processesPersonalData": true}, "aiAct": {}}"""

@SpringBootTest(classes = [AssessmentTestApplication::class])
@AutoConfigureMockMvc
class StartAssessmentTest {
    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) = AssessmentDatabase.register(registry)
    }

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var assessments: AssessmentRepository
    @Autowired private lateinit var tenants: TenantContext
    @Autowired private lateinit var tx: TransactionTemplate
    @Autowired private lateinit var scopes: FakeApplicabilityQuery

    private lateinit var sub: String
    private lateinit var tenant: UUID

    @BeforeEach
    fun newOrganization() {
        sub = UUID.randomUUID().toString()
        tenant = UUID.randomUUID()
    }

    /** The organization answered [scope] in the organization plugin (null: not yet), then starts [framework]. */
    private fun start(
        framework: String,
        scope: String? = IMPORTANT_SCOPE,
        official: Nis2Classification? = null,
        body: String = """{"framework": "$framework"}""",
    ): ResultActionsDsl {
        if (scope != null) scopes.declare(tenant, scope, official)
        return mvc.post(ASSESSMENTS) {
            header("Authorization", "Bearer responsible.$sub.$tenant")
            contentType = MediaType.APPLICATION_JSON
            content = body
        }
    }

    private fun idOf(result: ResultActionsDsl): String =
        JsonPath.read(result.andReturn().response.contentAsString, "$.id")

    private fun rows(table: String): Long =
        jdbc.queryForObject("select count(*) from $table where tenant_id = ?", Long::class.java, tenant)!!

    /** Like AssessmentPersistenceTest: runAs refuses to run inside the test's mock request. */
    private fun <T : Any> asTenant(block: () -> T): T {
        val request = RequestContextHolder.getRequestAttributes()
        RequestContextHolder.resetRequestAttributes()
        try {
            return tenants.runAs(TenantId(tenant)) { tx.execute { block() }!! }
        } finally {
            RequestContextHolder.setRequestAttributes(request)
        }
    }

    @Test
    fun `starts an nis2 assessment on the newest catalogue, cut to the classification`() {
        val result = start("nis2-qnrcs").andExpect {
            status { isCreated() }
            jsonPath("$.status") { value("draft") }
            jsonPath("$.catalogueVersion") { value(2) }
            jsonPath("$.nis2Classification") { value("important") }
            jsonPath("$.nis2ClassificationOfficial") { value(false) }
            jsonPath("$.maxLevel") { value("substantial") }
            jsonPath("$.maxScaleLevel.id") { value("substantial") }
            jsonPath("$.maxScaleLevel.label") { value("Substancial") }
            jsonPath("$.maxScaleLevel.length()") { value(2) }
            jsonPath("$.controls[0].scaleLevel.length()") { value(2) }
            jsonPath("$.controls[0].scaleLevel.label") { value("Básico") }
            jsonPath("$.rulesVersion") { value(ApplicabilityRules.VERSION) }
            jsonPath("$.controls[*].id") { value(contains("CTRL-A", "CTRL-B", "CTRL-D")) }
            jsonPath("$.progress.total") { value(3) }
            jsonPath("$.progress.answered") { value(0) }
        }
        val id = idOf(result)

        val audit = jdbc.queryForList(
            """select action, entity_id, actor, details->>'nis2Classification' as classification,
                      details->'scope' is not null as has_scope
               from audit.audit_event where tenant_id = ?""",
            tenant,
        )
        assertThat(audit).hasSize(1)
        assertThat(audit[0]["action"]).isEqualTo("assessment.started")
        assertThat(audit[0]["entity_id"]).isEqualTo(id)
        assertThat(audit[0]["actor"]).isEqualTo(sub)
        assertThat(audit[0]["classification"]).isEqualTo("IMPORTANT")
        assertThat(audit[0]["has_scope"]).isEqualTo(false)

        val scope = jdbc.queryForMap(
            """select scope_input->>'nis2Classification' as classification, scope_input->>'rulesVersion' as rules,
                      jsonb_exists(scope_input, 'indicative') or jsonb_exists(scope_input, 'effectiveNis2Classification')
                          as derived,
                      jsonb_exists(scope_input, 'officialNis2Classification') as has_official
               from assessment.assessment where id = ?::uuid""",
            id,
        )
        // Only the stored facts, not the getters derived from them.
        assertThat(scope["derived"]).isEqualTo(false)
        assertThat(scope["has_official"]).isEqualTo(true)
        assertThat(scope["classification"]).isEqualTo("IMPORTANT")
        assertThat(scope["rules"]).isEqualTo(ApplicabilityRules.VERSION)
    }

    @Test
    fun `an essential entity also gets high-level questions`() {
        start("nis2-qnrcs", ESSENTIAL_SCOPE).andExpect {
            status { isCreated() }
            jsonPath("$.nis2Classification") { value("essential") }
            jsonPath("$.controls[*].id") { value(contains("CTRL-A", "CTRL-B", "CTRL-C", "CTRL-D")) }
        }
    }

    @Test
    fun `gdpr gets every level`() {
        start("gdpr").andExpect {
            status { isCreated() }
            jsonPath("$.maxLevel") { value("high") }
            jsonPath("$.controls[*].id") { value(contains("CTRL-G", "CTRL-GH")) }
        }
    }

    @Test
    fun `a framework outside the scope is refused and nothing is stored`() {
        start("ai-act").andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.code") { value("FRAMEWORK_NOT_IN_SCOPE") }
        }
        assertThat(rows("assessment.assessment")).isZero()
        assertThat(rows("audit.audit_event")).isZero()
    }

    @Test
    fun `a framework without a catalogue is refused`() {
        start("soc-cmm").andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.code") { value("CATALOGUE_NOT_FOUND") }
        }
    }

    @Test
    fun `a malformed framework id is a 422 on the framework field`() {
        start("NIS2!").andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
            jsonPath("$.errors[0].field") { value("framework") }
        }
        assertThat(rows("assessment.assessment")).isZero()
        assertThat(rows("audit.audit_event")).isZero()
    }

    @Test
    fun `a second open assessment of the same framework is a 409 naming the open one`() {
        val first = idOf(start("nis2-qnrcs").andExpect { status { isCreated() } })

        start("nis2-qnrcs").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("ASSESSMENT_ALREADY_OPEN") }
            jsonPath("$.assessmentId") { value(first) }
        }
    }

    @Test
    fun `before the scope is answered the start is a 409 and nothing is stored`() {
        start("nis2-qnrcs", scope = null).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("SCOPE_NOT_ANSWERED") }
        }
        assertThat(rows("assessment.assessment")).isZero()
        assertThat(rows("audit.audit_event")).isZero()
    }

    @Test
    fun `the official CNCS qualification sets the level`() {
        start("nis2-qnrcs", official = Nis2Classification.ESSENTIAL).andExpect {
            status { isCreated() }
            jsonPath("$.nis2Classification") { value("essential") }
            jsonPath("$.nis2ClassificationOfficial") { value(true) }
            jsonPath("$.maxLevel") { value("high") }
        }
    }

    @Test
    fun `a scope sent in the body is deprecated and ignored`() {
        val contradictory = """{"size": {"employees": 120, "annualTurnoverEur": 18500000}, "nis2": {},
            "gdpr": {"processesPersonalData": false, "specialOrCriminalData": true}, "aiAct": {}}"""
        start("nis2-qnrcs", body = """{"framework": "nis2-qnrcs", "scope": $contradictory}""").andExpect {
            status { isCreated() }
            jsonPath("$.nis2Classification") { value("important") }
        }
    }

    @Test
    fun `an assessment keeps its pinned catalogue version`() {
        // Started before v2 was published: stored directly on v1.
        val id = UUID.randomUUID()
        asTenant {
            assessments.save(
                AssessmentEntity(
                    id, "nis2-qnrcs", 1, "substantial", Nis2Classification.IMPORTANT, "r1", "{}",
                    Instant.now().truncatedTo(ChronoUnit.MICROS),
                ),
            )
        }

        mvc.get("$ASSESSMENTS/$id") { header("Authorization", "Bearer responsible.$sub.$tenant") }.andExpect {
            status { isOk() }
            jsonPath("$.catalogueVersion") { value(1) }
            jsonPath("$.controls[*].id") { value(contains("CTRL-A", "CTRL-B")) }
        }
    }
}
