package pt.alinha.assessment.internal.persistence

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.repository.Repository
import pt.alinha.assessment.internal.domain.AssessmentStatus
import java.util.UUID

/**
 * A plain Repository, not JpaRepository, so findById/getReferenceById do not exist: every lookup is a derived
 * query, which always carries Hibernate's tenant restriction. All queries see only the current tenant.
 */
interface AssessmentRepository : Repository<AssessmentEntity, UUID> {
    fun save(entity: AssessmentEntity): AssessmentEntity

    fun saveAndFlush(entity: AssessmentEntity): AssessmentEntity

    fun findOneById(id: UUID): AssessmentEntity?

    /** Locks the row: answers and, later, the submit of the same assessment run one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findForUpdateById(id: UUID): AssessmentEntity?

    fun findFirstByFrameworkAndStatusNot(framework: String, status: AssessmentStatus): AssessmentEntity?

    fun findAllBy(pageable: Pageable): Page<AssessmentEntity>
}
