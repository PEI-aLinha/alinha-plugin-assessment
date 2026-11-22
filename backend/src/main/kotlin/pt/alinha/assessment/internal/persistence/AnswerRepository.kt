package pt.alinha.assessment.internal.persistence

import org.springframework.data.repository.Repository
import java.util.UUID

/** Same rule as AssessmentRepository: derived queries only, always filtered by tenant. */
interface AnswerRepository : Repository<AnswerEntity, UUID> {
    fun save(entity: AnswerEntity): AnswerEntity

    fun findAllByAssessmentId(assessmentId: UUID): List<AnswerEntity>

    fun findOneByAssessmentIdAndQuestionId(assessmentId: UUID, questionId: String): AnswerEntity?

    fun countByAssessmentId(assessmentId: UUID): Long
}
