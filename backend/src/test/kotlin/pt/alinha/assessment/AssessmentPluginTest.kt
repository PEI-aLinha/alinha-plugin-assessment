package pt.alinha.assessment

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AssessmentPluginTest {
    @Test
    fun `manifest has the plugin id`() {
        assertEquals("assessment", AssessmentPlugin().manifest.id)
    }

    @Test
    fun `roles get least privilege`() {
        val m = AssessmentPlugin().manifest
        assertThat(m.permissions).containsExactlyInAnyOrder(
            "assessment:read", "assessment:start", "assessment:answer", "assessment:submit", "assessment:review",
        )
        assertThat(m.rolePermissions).isEqualTo(
            mapOf(
                "responsible" to setOf("assessment:read", "assessment:start", "assessment:answer", "assessment:submit"),
                "analyst" to setOf("assessment:read", "assessment:answer"),
                "consultant" to setOf("assessment:read", "assessment:review"),
            ),
        )
    }

    @Test
    fun `publishes the submit and evaluation events`() {
        assertThat(AssessmentPlugin().manifest.publishes).containsExactlyInAnyOrder("AssessmentSubmitted", "AssessmentEvaluated")
    }
}