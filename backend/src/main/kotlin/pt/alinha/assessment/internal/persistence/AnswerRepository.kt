package pt.alinha.assessment.internal.persistence

import org.springframework.data.repository.Repository
import java.util.UUID

/** Same rule as AssessmentRepository: derived queries only, always filtered by tenant. */
interface AnswerRepository : Repository<AnswerEntity, UUID> {
    fun save(entity: AnswerEntity): AnswerEntity

    fun findAllByAssessmentId(assessmentId: UUID): List<AnswerEntity>

    /** The answers of a whole page of assessments in one query (the list's progress). */
    fun findAllByAssessmentIdIn(assessmentIds: Collection<UUID>): List<AnswerEntity>

    fun findOneByAssessmentIdAndQuestionId(assessmentId: UUID, questionId: String): AnswerEntity?
}
