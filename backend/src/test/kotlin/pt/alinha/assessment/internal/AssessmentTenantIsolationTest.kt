package pt.alinha.assessment.internal

import com.jayway.jsonpath.JsonPath
import org.assertj.core.api.Assertions.assertThat
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
import java.util.UUID

private const val ASSESSMENTS = "/api/assessment/assessments"

private const val IMPORTANT_SCOPE = """{"size": {"employees": 120, "annualTurnoverEur": 18500000},
    "nis2": {"entityTypes": ["MACHINERY_MANUFACTURING"]}, "gdpr": {"processesPersonalData": true}, "aiAct": {}}"""

/** Focus of review 1: another organization's assessment id never reads, writes or audits anything. */
@SpringBootTest(classes = [AssessmentTestApplication::class])
@AutoConfigureMockMvc
class AssessmentTenantIsolationTest {
    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) = AssessmentDatabase.register(registry)
    }

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var jdbc: JdbcTemplate

    private lateinit var tenantA: UUID
    private lateinit var tenantB: UUID
    private lateinit var tokenA: String
    private lateinit var tokenB: String

    /** Organization A's assessment, with CTRL-A-Q1 answered "yes". */
    private lateinit var idA: String

    @BeforeEach
    fun twoOrganizations() {
        tenantA = UUID.randomUUID()
        tenantB = UUID.randomUUID()
        tokenA = "responsible.${UUID.randomUUID()}.$tenantA"
        tokenB = "responsible.${UUID.randomUUID()}.$tenantB"
        idA = JsonPath.read(start(tokenA).andExpect { status { isCreated() } }.andReturn().response.contentAsString, "$.id")
        answer(tokenA, idA, "yes").andExpect { status { isOk() } }
    }

    private fun start(token: String): ResultActionsDsl = mvc.post(ASSESSMENTS) {
        header("Authorization", "Bearer $token")
        contentType = MediaType.APPLICATION_JSON
        content = """{"framework": "nis2-qnrcs", "scope": $IMPORTANT_SCOPE}"""
    }

    private fun answer(token: String, id: String, value: String): ResultActionsDsl =
        mvc.put("$ASSESSMENTS/$id/answers/CTRL-A-Q1") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"answer": "$value"}"""
        }

    @Test
    fun `another tenant cannot open an assessment by id`() {
        mvc.get("$ASSESSMENTS/$idA") { header("Authorization", "Bearer $tokenB") }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("ASSESSMENT_NOT_FOUND") }
        }
    }

    @Test
    fun `another tenant cannot answer it`() {
        answer(tokenB, idA, "no").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("ASSESSMENT_NOT_FOUND") }
        }

        val value = jdbc.queryForObject(
            "select value from assessment.answer where assessment_id = ?::uuid", String::class.java, idA,
        )
        assertThat(value).isEqualTo("YES")
        val auditOfB = jdbc.queryForObject(
            "select count(*) from audit.audit_event where tenant_id = ?", Long::class.java, tenantB,
        )
        assertThat(auditOfB).isZero()
    }

    @Test
    fun `the list shows only the caller's assessments`() {
        mvc.get(ASSESSMENTS) { header("Authorization", "Bearer $tokenB") }.andExpect {
            status { isOk() }
            jsonPath("$.total") { value(0) }
        }
    }

    @Test
    fun `a consultant sees only the organization in X-Alinha-Tenant`() {
        val consultant = "consultant.${UUID.randomUUID()}.$tenantA" // assigned to A only

        mvc.get("$ASSESSMENTS/$idA") {
            header("Authorization", "Bearer $consultant")
            header("X-Alinha-Tenant", tenantA.toString())
        }.andExpect { status { isOk() } }

        mvc.get("$ASSESSMENTS/$idA") {
            header("Authorization", "Bearer $consultant")
            header("X-Alinha-Tenant", tenantB.toString())
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `the same framework can be open in two tenants`() {
        start(tokenB).andExpect { status { isCreated() } }
    }
}
