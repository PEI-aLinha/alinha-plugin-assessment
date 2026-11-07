package pt.alinha.assessment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AssessmentPluginTest {
    @Test
    fun `manifest has the plugin id`() {
        assertEquals("assessment", AssessmentPlugin().manifest.id)
    }
}
