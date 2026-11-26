package pt.alinha.assessment.internal.persistence

import org.springframework.data.repository.Repository
import java.util.UUID

/** Same rule as AssessmentRepository: derived queries only, always filtered by tenant. */
interface ResultRepository : Repository<ResultEntity, UUID> {
    fun save(entity: ResultEntity): ResultEntity

    fun findOneByAssessmentId(assessmentId: UUID): ResultEntity?
}
