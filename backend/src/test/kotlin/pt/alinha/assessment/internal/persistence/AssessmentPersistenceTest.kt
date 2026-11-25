package pt.alinha.assessment.internal.persistence

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.context.request.RequestContextHolder
import pt.alinha.assessment.internal.AssessmentDatabase
import pt.alinha.assessment.internal.AssessmentTestApplication
import pt.alinha.assessment.internal.domain.AssessmentStatus
import pt.alinha.assessment.internal.domain.IllegalStatusTransitionException
import pt.alinha.core.compliance.Answer
import pt.alinha.core.compliance.Nis2Classification
import pt.alinha.core.tenant.TenantContext
import pt.alinha.pluginapi.TenantId
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@SpringBootTest(classes = [AssessmentTestApplication::class])
class AssessmentPersistenceTest {
    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) = AssessmentDatabase.register(registry)
    }

    @Autowired private lateinit var assessments: AssessmentRepository
    @Autowired private lateinit var answers: AnswerRepository
    @Autowired private lateinit var tenants: TenantContext
    @Autowired private lateinit var tx: TransactionTemplate
    @Autowired private lateinit var jdbc: JdbcTemplate

    // PostgreSQL keeps microseconds; a nanosecond Instant would not compare equal after a round trip.
    private val now = Instant.now().truncatedTo(ChronoUnit.MICROS)

    private fun newTenant() = TenantId(UUID.randomUUID())

    /**
     * Runs [block] as [tenant], in its own transaction, like a request of that organization would. The test's mock
     * request is bound to this thread and runAs refuses to run inside a request, so it is unbound meanwhile.
     */
    private fun <T : Any> asTenant(tenant: TenantId, block: () -> T): T {
        val request = RequestContextHolder.getRequestAttributes()
        RequestContextHolder.resetRequestAttributes()
        try {
            return tenants.runAs(tenant) { tx.execute { block() }!! }
        } finally {
            RequestContextHolder.setRequestAttributes(request)
        }
    }

    private fun assessment(framework: String = "nis2-qnrcs") = AssessmentEntity(
        id = UUID.randomUUID(),
        framework = framework,
        catalogueVersion = 1,
        maxLevel = "substantial",
        nis2Classification = Nis2Classification.IMPORTANT,
        rulesVersion = "r1",
        scopeInput = """{"size": {"employees": 120}}""",
        createdAt = now,
    )

    private fun answer(assessmentId: UUID, value: Answer = Answer.YES) =
        AnswerEntity(UUID.randomUUID(), assessmentId, "CTRL-A-Q1", value, now)

    @Test
    fun `an assessment and its answers are stored under the current tenant`() {
        val t = newTenant()
        val a = assessment()
        asTenant(t) {
            assessments.save(a)
            answers.save(answer(a.id))
        }

        val row = jdbc.queryForMap(
            "select tenant_id, status, scope_input->'size'->>'employees' as employees from assessment.assessment where id = ?",
            a.id,
        )
        assertThat(row["tenant_id"]).isEqualTo(t.value)
        assertThat(row["status"]).isEqualTo("DRAFT")
        assertThat(row["employees"]).isEqualTo("120")

        val answerRow = jdbc.queryForMap("select tenant_id, value from assessment.answer where assessment_id = ?", a.id)
        assertThat(answerRow["tenant_id"]).isEqualTo(t.value)
        assertThat(answerRow["value"]).isEqualTo("YES")
    }

    @Test
    fun `another tenant does not see it`() {
        val a = assessment()
        asTenant(newTenant()) {
            assessments.save(a)
            answers.save(answer(a.id))
        }

        asTenant(newTenant()) {
            assertThat(assessments.findOneById(a.id)).isNull()
            assertThat(answers.findAllByAssessmentId(a.id)).isEmpty()
        }
    }

    @Test
    fun `a second open assessment for the same framework is refused`() {
        val t = newTenant()
        asTenant(t) { assessments.saveAndFlush(assessment()) }

        assertThatThrownBy { asTenant(t) { assessments.saveAndFlush(assessment()) } }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a validated assessment can be followed by a new one`() {
        val t = newTenant()
        asTenant(t) {
            val first = assessments.saveAndFlush(assessment())
            first.moveTo(AssessmentStatus.SUBMITTED, now)
            first.moveTo(AssessmentStatus.EVALUATED, now)
            first.moveTo(AssessmentStatus.VALIDATED, now)
            assessments.saveAndFlush(first)
        }

        asTenant(t) { assessments.saveAndFlush(assessment()) }

        val count = jdbc.queryForObject(
            "select count(*) from assessment.assessment where tenant_id = ?", Long::class.java, t.value,
        )
        assertThat(count).isEqualTo(2)
    }

    @Test
    fun `an assessment cannot skip a state`() {
        val a = assessment()
        assertThatThrownBy { a.moveTo(AssessmentStatus.VALIDATED, now) }
            .isInstanceOf(IllegalStatusTransitionException::class.java)
        assertThat(a.status).isEqualTo(AssessmentStatus.DRAFT)
    }

    @Test
    fun `change returns the previous value only when it changes`() {
        val ans = answer(UUID.randomUUID())
        val later = now.plusSeconds(60)

        assertThat(ans.change(Answer.YES, later)).isNull()
        assertThat(ans.answeredAt).isEqualTo(now)

        assertThat(ans.change(Answer.NO, later)).isEqualTo(Answer.YES)
        assertThat(ans.value).isEqualTo(Answer.NO)
        assertThat(ans.answeredAt).isEqualTo(later)
    }
}