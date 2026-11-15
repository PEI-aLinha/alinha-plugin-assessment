package pt.alinha.assessment.internal.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import pt.alinha.core.access.RequiresPermission

@RestController
@RequestMapping("/api/assessment")
class AssessmentController {

    /** Walking skeleton: proves the plugin is wired end to end. Every role may call it (`plugins:read`). */
    @RequiresPermission("plugins")
    @GetMapping("/ping")
    fun ping() = mapOf("plugin" to "assessment", "status" to "ok")
}
