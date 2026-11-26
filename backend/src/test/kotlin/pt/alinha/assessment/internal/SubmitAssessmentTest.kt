package pt.alinha.assessment.internal

import com.jayway.jsonpath.JsonPath
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.closeTo
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import pt.alinha.assessment.events.AssessmentEvaluated
import pt.alinha.assessment.events.AssessmentSubmitted
import pt.alinha.core.compliance.ControlRules
import java.util.UUID

private const val ASSESSMENTS = "/api/assessment/assessments"

/** Important entity: nis2-qnrcs v2 cut to substantial, so CTRL-A (basic), CTRL-B (substantial), CTRL-D (basic). */
private const val IMPORTANT_SCOPE = """{"size": {"employees": 120, "annualTurnoverEur": 18500000},
    "nis2": {"entityTypes": ["MACHINERY_MANUFACTURING"]}, "gdpr": {"processesPersonalData": true}, "aiAct": {}}"""

/** Submit and results (assessment#5): one transaction, audited, published, consultant-only results. */
@SpringBootTest(classes = [AssessmentTestApplication::class])
@AutoConfigureMockMvc
@RecordApplicationEvents
class SubmitAssessmentTest {
    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) = AssessmentDatabase.register(registry)
    }

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var events: ApplicationEvents

    private lateinit var tenant: UUID
    private lateinit var responsible: String
    private lateinit var consultant: String

    @BeforeEach
    fun newOrganization() {
        tenant = UUID.randomUUID()
        responsible = "responsible.${UUID.randomUUID()}.$tenant"
        consultant = "consultant.${UUID.randomUUID()}.$tenant"
    }

    private fun started(): String {
        val body = mvc.post(ASSESSMENTS) {
            header("Authorization", "Bearer $responsible")
            contentType = MediaType.APPLICATION_JSON
            content = """{"framework": "nis2-qnrcs", "scope": $IMPORTANT_SCOPE}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        return JsonPath.read(body, "$.id")
    }

    private fun answer(id: String, question: String, value: String) =
        mvc.put("$ASSESSMENTS/$id/answers/$question") {
            header("Authorization", "Bearer $responsible")
            contentType = MediaType.APPLICATION_JSON
            content = """{"answer": "$value"}"""
        }.andExpect { status { isOk() } }

    /** CTRL-A and CTRL-D in place, CTRL-B partial: basic reached, 2 of 3 controls compliant. */
    private fun answeredAll(): String = started().also {
        answer(it, "CTRL-A-Q1", "yes")
        answer(it, "CTRL-B-Q1", "partial")
        answer(it, "CTRL-D-Q1", "yes")
    }

    private fun submit(id: String, token: String = responsible): ResultActionsDsl =
        mvc.post("$ASSESSMENTS/$id/submit") { header("Authorization", "Bearer $token") }

    private fun results(id: String, token: String = consultant): ResultActionsDsl =
        mvc.get("$ASSESSMENTS/$id/results") {
            header("Authorization", "Bearer $token")
            if (token.startsWith("consultant.")) header("X-Alinha-Tenant", tenant.toString())
        }

    private fun auditActions(id: String): List<String> = jdbc.queryForList(
        "select action from audit.audit_event where entity_id = ? order by occurred_at, id", String::class.java, id,
    )

    private fun status(id: String): String =
        jdbc.queryForObject("select status from assessment.assessment where id = ?::uuid", String::class.java, id)!!

    @Test
    fun `submit freezes the answers, computes the results and audits both steps`() {
        val id = answeredAll()

        submit(id).andExpect {
            status { isOk() }
            jsonPath("$.id") { value(id) }
            jsonPath("$.status") { value("evaluated") }
            jsonPath("$.progress.answered") { value(3) }
        }

        assertThat(status(id)).isEqualTo("EVALUATED")
        assertThat(auditActions(id).takeLast(2)).containsExactly("assessment.submitted", "assessment.evaluated")
        val evaluated = jdbc.queryForMap(
            """select details->>'engineVersion' as engine, details->>'currentLevel' as level
               from audit.audit_event where entity_id = ? and action = 'assessment.evaluated'""",
            id,
        )
        assertThat(evaluated["engine"]).isEqualTo(ControlRules.VERSION)
        assertThat(evaluated["level"]).isEqualTo("basic")
        val submitted = jdbc.queryForMap(
            """select details->>'answered' as answered, details->>'questions' as questions
               from audit.audit_event where entity_id = ? and action = 'assessment.submitted'""",
            id,
        )
        assertThat(submitted["answered"]).isEqualTo("3")
        assertThat(submitted["questions"]).isEqualTo("3")
    }

    @Test
    fun `the results store level ids of the catalogue's scale, not enum names`() {
        val id = answeredAll()
        submit(id).andExpect { status { isOk() } }

        val stored = jdbc.queryForMap(
            """select current_level, target_level, controls->1->>'level' as control_level
               from assessment.result where assessment_id = ?::uuid""",
            id,
        )
        assertThat(stored["current_level"]).isEqualTo("basic")
        assertThat(stored["target_level"]).isEqualTo("substantial")
        assertThat(stored["control_level"]).isEqualTo("substantial")
    }

    @Test
    fun `a stored control missing from the questionnaire keeps its id as the title`() {
        val id = answeredAll()
        submit(id).andExpect { status { isOk() } }
        jdbc.update(
            """update assessment.result
               set controls = controls || '[{"controlId": "CTRL-GONE", "level": "basic", "status": "GAP"}]'::jsonb
               where assessment_id = ?::uuid""",
            id,
        )

        results(id).andExpect {
            status { isOk() }
            jsonPath("$.controls[3].controlId") { value("CTRL-GONE") }
            jsonPath("$.controls[3].title") { value("CTRL-GONE") }
        }
    }

    @Test
    fun `the consultant sees the results, in catalogue order with the control titles`() {
        val id = answeredAll()
        submit(id).andExpect { status { isOk() } }

        results(id).andExpect {
            status { isOk() }
            jsonPath("$.assessmentId") { value(id) }
            jsonPath("$.evaluatedAt") { value(notNullValue()) }
            jsonPath("$.engineVersion") { value(ControlRules.VERSION) }
            jsonPath("$.currentLevel") { value("basic") }
            jsonPath("$.targetLevel") { value("substantial") }
            jsonPath("$.coverage") { value(closeTo(2.0 / 3, 1e-9)) }
            jsonPath("$.controls[*].controlId") { value(contains("CTRL-A", "CTRL-B", "CTRL-D")) }
            jsonPath("$.controls[*].status") { value(contains("compliant", "partial", "compliant")) }
            jsonPath("$.controls[0].title") { value("Basic control") }
            jsonPath("$.controls[1].level") { value("substantial") }
        }
    }

    @Test
    fun `a gap in a basic control gives no current level`() {
        val id = started()
        answer(id, "CTRL-A-Q1", "unknown")
        answer(id, "CTRL-B-Q1", "yes")
        answer(id, "CTRL-D-Q1", "yes")
        submit(id).andExpect { status { isOk() } }

        results(id).andExpect {
            jsonPath("$.currentLevel") { value(nullValue()) }
            jsonPath("$.controls[0].status") { value("gap") }
        }
    }

    @Test
    fun `submit publishes AssessmentSubmitted and AssessmentEvaluated for the organization`() {
        val id = answeredAll()
        submit(id).andExpect { status { isOk() } }

        val submitted = events.stream(AssessmentSubmitted::class.java).filter { it.assessmentId.toString() == id }.toList()
        val evaluated = events.stream(AssessmentEvaluated::class.java).filter { it.assessmentId.toString() == id }.toList()
        assertThat(submitted).hasSize(1)
        assertThat(evaluated).hasSize(1)
        assertThat(submitted.single().tenantId.value).isEqualTo(tenant)
        assertThat(evaluated.single().correlationId).isEqualTo(submitted.single().correlationId)
    }

    @Test
    fun `unanswered questions are a 422 that lists them, and nothing changes`() {
        val id = started()
        answer(id, "CTRL-A-Q1", "yes")

        submit(id).andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.code") { value("ASSESSMENT_INCOMPLETE") }
            jsonPath("$.unanswered") { value(contains("CTRL-B-Q1", "CTRL-D-Q1")) }
        }
        assertThat(status(id)).isEqualTo("DRAFT")
        assertThat(auditActions(id)).doesNotContain("assessment.submitted", "assessment.evaluated")
        assertThat(events.stream(AssessmentSubmitted::class.java).filter { it.assessmentId.toString() == id }).isEmpty()
    }

    @Test
    fun `a second submit is a 409 and the answers stay frozen`() {
        val id = answeredAll()
        submit(id).andExpect { status { isOk() } }

        submit(id).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("ASSESSMENT_NOT_EDITABLE") }
        }
        mvc.put("$ASSESSMENTS/$id/answers/CTRL-A-Q1") {
            header("Authorization", "Bearer $responsible")
            contentType = MediaType.APPLICATION_JSON
            content = """{"answer": "no"}"""
        }.andExpect { status { isConflict() } }
        assertThat(auditActions(id).count { it == "assessment.evaluated" }).isEqualTo(1)
    }

    @Test
    fun `a draft has no results yet`() {
        val id = answeredAll()

        results(id).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("RESULTS_NOT_AVAILABLE") }
        }
    }

    @Test
    fun `only the responsible person submits, only the consultant sees the results`() {
        val id = answeredAll()
        val analyst = "analyst.${UUID.randomUUID()}.$tenant"

        submit(id, token = analyst).andExpect { status { isForbidden() } }
        mvc.post("$ASSESSMENTS/$id/submit") {
            header("Authorization", "Bearer $consultant")
            header("X-Alinha-Tenant", tenant.toString())
        }.andExpect { status { isForbidden() } }
        assertThat(status(id)).isEqualTo("DRAFT")

        submit(id).andExpect { status { isOk() } }
        results(id, token = responsible).andExpect { status { isForbidden() } }
        results(id, token = analyst).andExpect { status { isForbidden() } }
    }

    @Test
    fun `another organization can neither submit an assessment nor read its results`() {
        val id = answeredAll()
        val otherTenant = UUID.randomUUID()

        submit(id, token = "responsible.${UUID.randomUUID()}.$otherTenant").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("ASSESSMENT_NOT_FOUND") }
        }
        assertThat(status(id)).isEqualTo("DRAFT")

        submit(id).andExpect { status { isOk() } }
        val otherConsultant = "consultant.${UUID.randomUUID()}.$otherTenant"
        mvc.get("$ASSESSMENTS/$id/results") {
            header("Authorization", "Bearer $otherConsultant")
            header("X-Alinha-Tenant", otherTenant.toString())
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("ASSESSMENT_NOT_FOUND") }
        }
    }
}
