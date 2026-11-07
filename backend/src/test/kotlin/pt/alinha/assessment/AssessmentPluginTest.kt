package pt.alinha.assessment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AssessmentPluginTest {
    @Test
    fun `manifest tem o id do plugin`() {
        assertEquals("assessment", AssessmentPlugin().manifest.id)
    }
}
