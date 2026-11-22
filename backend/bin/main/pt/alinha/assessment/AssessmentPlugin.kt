package pt.alinha.assessment

import org.springframework.stereotype.Component
import pt.alinha.pluginapi.AlinhaPlugin
import pt.alinha.pluginapi.PluginKind
import pt.alinha.pluginapi.PluginManifest

/** Registers the plugin in the core's Module Registry. Keep in sync with plugin.yaml. */
@Component
class AssessmentPlugin : AlinhaPlugin {
    override val manifest = PluginManifest(
        id = "assessment",
        name = "Assessment",
        kind = PluginKind.FEATURE,
        version = "0.1.0",
        publishes = setOf(),
        consumes = setOf(),
        permissions = setOf(READ, START, ANSWER),
        rolePermissions = mapOf(
            "responsible" to setOf(READ, START, ANSWER),
            // Analysts answer technical questions; assessment#4 later limits them to the questions assigned to them.
            "analyst" to setOf(READ, ANSWER),
            "consultant" to setOf(READ),
        ),
    )

    companion object {
        const val READ = "assessment:read"
        const val START = "assessment:start"
        const val ANSWER = "assessment:answer"
    }
}