package pt.alinha.assessment.internal.persistence

import org.springframework.data.repository.Repository
import java.util.UUID

/** Same rule as AssessmentRepository: derived queries only, always filtered by tenant. */
interface RoadmapRepository : Repository<RoadmapEntity, UUID> {
    fun save(entity: RoadmapEntity): RoadmapEntity
}

interface RoadmapActionRepository : Repository<RoadmapActionEntity, UUID> {
    fun saveAll(entities: Iterable<RoadmapActionEntity>): List<RoadmapActionEntity>
}
