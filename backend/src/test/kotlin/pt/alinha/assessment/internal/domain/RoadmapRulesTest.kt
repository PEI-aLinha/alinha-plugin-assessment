package pt.alinha.assessment.internal.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import pt.alinha.core.compliance.Catalogue
import pt.alinha.core.compliance.ComplianceLevel
import pt.alinha.core.compliance.ComplianceLevel.BASIC
import pt.alinha.core.compliance.ComplianceLevel.HIGH
import pt.alinha.core.compliance.ComplianceLevel.SUBSTANTIAL
import pt.alinha.core.compliance.Control
import pt.alinha.core.compliance.ControlEvaluation
import pt.alinha.core.compliance.ControlStatus
import pt.alinha.core.compliance.ControlStatus.COMPLIANT
import pt.alinha.core.compliance.ControlStatus.GAP
import pt.alinha.core.compliance.ControlStatus.PARTIAL
import pt.alinha.core.compliance.Evaluation
import pt.alinha.core.compliance.Requirement
import java.time.Instant
import java.time.LocalDate

class RoadmapRulesTest {
    private val evaluatedAt = Instant.parse("2026-11-26T10:00:00Z")

    private fun control(id: String, level: ComplianceLevel, technical: Boolean = false) =
        Control(id, "Title $id", level, technical, listOf("Evidence $id."), emptyList())

    private val catalogue = Catalogue(
        framework = "nis2-qnrcs", version = 1, title = "Test", sources = emptyList(),
        requirements = listOf(
            Requirement("REQ-1", "Reference 1", "Requirement 1.", listOf("A", "B")),
            Requirement("REQ-2", "Reference 2", "Requirement 2.", listOf("A", "C")),
        ),
        controls = listOf(control("A", BASIC), control("B", SUBSTANTIAL), control("C", HIGH, technical = true), control("D", BASIC)),
    )

    private fun evaluation(vararg controls: Pair<String, ControlStatus>) = Evaluation(
        engineVersion = "test", currentLevel = null, targetLevel = HIGH, coverage = 0.0,
        controls = controls.map { (id, status) -> ControlEvaluation(id, catalogue.controls.single { it.id == id }.level, status) },
    )

    private fun plan(vararg controls: Pair<String, ControlStatus>) =
        RoadmapRules.plan(catalogue, evaluation(*controls), evaluatedAt)

    @Test
    fun `one action per gap or partial control, none for a compliant one`() {
        val actions = plan("A" to COMPLIANT, "B" to PARTIAL, "C" to GAP)

        assertThat(actions.map { it.controlId }).containsExactlyInAnyOrder("B", "C")
    }

    @Test
    fun `no gaps, no actions`() {
        assertThat(plan("A" to COMPLIANT, "D" to COMPLIANT)).isEmpty()
    }

    @ParameterizedTest
    @CsvSource(
        "BASIC,GAP,HIGH",
        "BASIC,PARTIAL,MEDIUM",
        "SUBSTANTIAL,GAP,MEDIUM",
        "HIGH,GAP,MEDIUM",
        "SUBSTANTIAL,PARTIAL,LOW",
        "HIGH,PARTIAL,LOW",
    )
    fun `priority comes from the gap severity and the control's level`(
        level: ComplianceLevel, status: ControlStatus, expected: ActionPriority,
    ) {
        assertThat(RoadmapRules.priority(level, status)).isEqualTo(expected)
    }

    @Test
    fun `the deadline follows the priority, counted from the evaluation day`() {
        val actions = plan("A" to GAP, "D" to PARTIAL, "B" to PARTIAL).associateBy { it.controlId }

        assertThat(actions.getValue("A").dueOn).isEqualTo(LocalDate.parse("2026-12-26"))
        assertThat(actions.getValue("D").dueOn).isEqualTo(LocalDate.parse("2027-02-24"))
        assertThat(actions.getValue("B").dueOn).isEqualTo(LocalDate.parse("2027-05-25"))
    }

    @Test
    fun `technical controls go to the technical team, the others to the responsible person`() {
        val actions = plan("A" to GAP, "C" to GAP).associateBy { it.controlId }

        assertThat(actions.getValue("A").owner).isEqualTo(ActionOwner.RESPONSIBLE)
        assertThat(actions.getValue("C").owner).isEqualTo(ActionOwner.TECHNICAL)
    }

    @Test
    fun `each action carries the control's title, evidence and every legal reference that asks for it`() {
        val a = plan("A" to GAP).single()

        assertThat(a.title).isEqualTo("Title A")
        assertThat(a.evidence).containsExactly("Evidence A.")
        assertThat(a.legalReferences).containsExactly("Reference 1", "Reference 2")
    }

    @Test
    fun `most urgent first, then in catalogue order`() {
        val actions = plan("A" to PARTIAL, "B" to GAP, "C" to PARTIAL, "D" to GAP)

        assertThat(actions.map { it.controlId }).containsExactly("D", "A", "B", "C")
    }
}
