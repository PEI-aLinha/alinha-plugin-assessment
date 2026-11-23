package pt.alinha.assessment.internal

import com.jayway.jsonpath.JsonPath
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.notNullValue
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
import org.springframework.test.web.servlet.put
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.context.request.RequestContextHolder
import pt.alinha.assessment.internal.domain.AssessmentStatus
import pt.alinha.assessment.internal.persistence.AssessmentRepository
import pt.alinha.core.tenant.TenantContext
import pt.alinha.pluginapi.TenantId
import java.time.Instant
import java.util.UUID

private const val ASSESSMENTS = "/api/assessment/assessments"

/** Important entity: nis2-qnrcs v2 cut to substantial, so CTRL-A, CTRL-B and CTRL-D (3 questions). */
private const val IMPORTANT_SCOPE = """{"size": {"employees": 120, "annualTurnoverEur": 18500000},
    "nis2": {"entityTypes": ["MACHINERY_MANUFACTURING"]}, "gdpr": {"processesPersonalData": true}, "aiAct": {}}"""

/** Essential entity: also gets CTRL-C, whose question only offers yes/no. */
private const val ESSENTIAL_SCOPE = """{"size": {"employees": 5, "annualTurnoverEur": 500000},
    "nis2": {"entityTypes": ["DNS_SERVICE_PROVIDER"]}, "gdpr": {"processesPersonalData": true}, "aiAct": {}}"""

@SpringBootTest(classes = [AssessmentTestApplication::class])
@AutoConfigureMockMvc
class AnswerAndResumeTest {
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

    private lateinit var sub: String
    private lateinit var tenant: UUID
    private val responsible get() = "responsible.$sub.$tenant"

    @BeforeEach
    fun newOrganization() {
        sub = UUID.randomUUID().toString()
        tenant = UUID.randomUUID()
    }

    /** Starts an assessment as the responsible person and returns its id. */
    private fun started(framework: String = "nis2-qnrcs", scope: String = IMPORTANT_SCOPE): String {
        val body = mvc.post(ASSESSMENTS) {
            header("Authorization", "Bearer $responsible")
            contentType = MediaType.APPLICATION_JSON
            content = """{"framework": "$framework", "scope": $scope}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        return JsonPath.read(body, "$.id")
    }

    private fun answer(id: String, question: String, value: String, token: String = responsible): ResultActionsDsl =
        mvc.put("$ASSESSMENTS/$id/answers/$question") {
            header("Authorization", "Bearer $token")
            if (token.startsWith("consultant.")) header("X-Alinha-Tenant", tenant.toString())
            contentType = MediaType.APPLICATION_JSON
            content = """{"answer": "$value"}"""
        }

    private fun open(id: String): ResultActionsDsl =
        mvc.get("$ASSESSMENTS/$id") { header("Authorization", "Bearer $responsible") }

    private fun auditActions(id: String): List<String> = jdbc.queryForList(
        "select action from audit.audit_event where entity_id = ? order by occurred_at, id", String::class.java, id,
    )

    private fun answerRows(): Long =
        jdbc.queryForObject("select count(*) from assessment.answer where tenant_id = ?", Long::class.java, tenant)!!

    /** Like the other tests: runAs refuses to run inside the test's mock request. */
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
    fun `an answer is saved, audited and shown when the assessment is opened again`() {
        val id = started()

        answer(id, "CTRL-A-Q1", "yes").andExpect {
            status { isOk() }
            jsonPath("$.questionId") { value("CTRL-A-Q1") }
            jsonPath("$.answer") { value("yes") }
            jsonPath("$.progress.answered") { value(1) }
            jsonPath("$.progress.total") { value(3) }
        }

        open(id).andExpect {
            jsonPath("$.controls[0].questions[0].id") { value("CTRL-A-Q1") }
            jsonPath("$.controls[0].questions[0].answer") { value("yes") }
            jsonPath("$.controls[0].questions[0].answeredAt") { value(notNullValue()) }
            jsonPath("$.progress.answered") { value(1) }
        }

        assertThat(auditActions(id)).containsExactly("assessment.started", "assessment.answer.saved")
        val details = jdbc.queryForMap(
            """select details->>'questionId' as question, details->>'to' as "to", jsonb_typeof(details->'from') as "from"
               from audit.audit_event where entity_id = ? and action = 'assessment.answer.saved'""",
            id,
        )
        assertThat(details["question"]).isEqualTo("CTRL-A-Q1")
        assertThat(details["to"]).isEqualTo("YES")
        assertThat(details["from"]).isEqualTo("null")
    }

    @Test
    fun `changing an answer is audited with the previous value`() {
        val id = started()
        answer(id, "CTRL-A-Q1", "yes").andExpect { status { isOk() } }
        answer(id, "CTRL-A-Q1", "no").andExpect { status { isOk() } }

        val changes = jdbc.queryForList(
            """select details->>'from' as "from", details->>'to' as "to" from audit.audit_event
               where entity_id = ? and action = 'assessment.answer.saved' order by occurred_at, id""",
            id,
        ).map { it["from"] to it["to"] }
        assertThat(changes).containsExactly(null to "YES", "YES" to "NO")
        open(id).andExpect { jsonPath("$.controls[0].questions[0].answer") { value("no") } }
    }

    @Test
    fun `saving the same answer twice writes and audits once`() {
        val id = started()
        val first = answer(id, "CTRL-A-Q1", "yes").andExpect { status { isOk() } }.andReturn().response.contentAsString
        val second = answer(id, "CTRL-A-Q1", "yes").andExpect { status { isOk() } }.andReturn().response.contentAsString

        assertThat(JsonPath.read<String>(second, "$.answeredAt")).isEqualTo(JsonPath.read<String>(first, "$.answeredAt"))
        assertThat(auditActions(id).count { it == "assessment.answer.saved" }).isEqualTo(1)
    }

    @Test
    fun `a question above the assessment's level is not part of it`() {
        val id = started() // important: cut to substantial

        answer(id, "CTRL-C-Q1", "yes").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("QUESTION_NOT_IN_ASSESSMENT") }
        }
        answer(id, "CTRL-G-Q1", "yes").andExpect { // a gdpr question
            status { isNotFound() }
            jsonPath("$.code") { value("QUESTION_NOT_IN_ASSESSMENT") }
        }
        assertThat(answerRows()).isZero()
    }

    @Test
    fun `an answer the question does not offer is a 422`() {
        val id = started(scope = ESSENTIAL_SCOPE)

        answer(id, "CTRL-C-Q1", "unknown").andExpect { // CTRL-C-Q1 offers yes/no only
            status { isUnprocessableContent() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
            jsonPath("$.errors[0].field") { value("answer") }
        }
        assertThat(answerRows()).isZero()
        assertThat(auditActions(id)).containsExactly("assessment.started")
    }

    @Test
    fun `an unknown answer value is a 422`() {
        val id = started()
        answer(id, "CTRL-A-Q1", "maybe").andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
        }
    }

    @Test
    fun `answers are frozen after submit`() {
        val id = started()
        asTenant { assessments.findOneById(UUID.fromString(id))!!.moveTo(AssessmentStatus.SUBMITTED, Instant.now()) }

        answer(id, "CTRL-A-Q1", "yes").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("ASSESSMENT_NOT_EDITABLE") }
        }
        assertThat(auditActions(id)).containsExactly("assessment.started")
    }

    @Test
    fun `the list shows the most recently worked assessment first, with progress`() {
        started("nis2-qnrcs")
        val gdpr = started("gdpr")
        answer(gdpr, "CTRL-G-Q1", "yes").andExpect { status { isOk() } }

        mvc.get(ASSESSMENTS) { header("Authorization", "Bearer $responsible") }.andExpect {
            status { isOk() }
            jsonPath("$.items[0].framework") { value("gdpr") }
            jsonPath("$.items[0].progress.answered") { value(1) }
            jsonPath("$.items[0].progress.total") { value(2) }
            jsonPath("$.items[1].framework") { value("nis2-qnrcs") }
            jsonPath("$.total") { value(2) }
            jsonPath("$.page") { value(0) }
            jsonPath("$.pageSize") { value(20) }
        }
    }

    @Test
    fun `page and page size outside the contract's range are rejected`() {
        for ((query, field) in listOf("pageSize=500" to "pageSize", "pageSize=0" to "pageSize", "page=-1" to "page")) {
            mvc.get("$ASSESSMENTS?$query") { header("Authorization", "Bearer $responsible") }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
                jsonPath("$.errors[0].field") { value(field) }
            }
        }
    }

    @Test
    fun `the largest page size is accepted`() {
        mvc.get("$ASSESSMENTS?pageSize=100") { header("Authorization", "Bearer $responsible") }.andExpect {
            status { isOk() }
            jsonPath("$.pageSize") { value(100) }
        }
    }

    @Test
    fun `an analyst can answer but not start, a consultant can only read`() {
        val id = started()
        val analyst = "analyst.${UUID.randomUUID()}.$tenant"
        val consultant = "consultant.${UUID.randomUUID()}.$tenant"

        answer(id, "CTRL-A-Q1", "yes", token = analyst).andExpect { status { isOk() } }
        mvc.post(ASSESSMENTS) {
            header("Authorization", "Bearer $analyst")
            contentType = MediaType.APPLICATION_JSON
            content = """{"framework": "gdpr", "scope": $IMPORTANT_SCOPE}"""
        }.andExpect { status { isForbidden() } }

        mvc.get("$ASSESSMENTS/$id") {
            header("Authorization", "Bearer $consultant")
            header("X-Alinha-Tenant", tenant.toString())
        }.andExpect { status { isOk() } }
        answer(id, "CTRL-A-Q1", "no", token = consultant).andExpect { status { isForbidden() } }
        mvc.post(ASSESSMENTS) {
            header("Authorization", "Bearer $consultant")
            header("X-Alinha-Tenant", tenant.toString())
            contentType = MediaType.APPLICATION_JSON
            content = """{"framework": "gdpr", "scope": $IMPORTANT_SCOPE}"""
        }.andExpect { status { isForbidden() } }
    }
}
