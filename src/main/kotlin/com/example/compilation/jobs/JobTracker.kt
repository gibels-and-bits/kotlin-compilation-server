package com.example.compilation.jobs

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class CompilationJob(
    val teamId: String,
    val teamName: String? = null,
    val status: JobStatus,
    val receivedAt: Long,
    val updatedAt: Long,
    val error: String? = null
)

@Serializable
enum class JobStatus {
    RECEIVED,
    COMPILING,
    SUCCESS,
    FAILED
}

class JobTracker {
    private val jobs = ConcurrentHashMap<String, CompilationJob>()
    
    fun trackJobReceived(teamId: String, teamName: String? = null) {
        val now = System.currentTimeMillis()
        jobs[teamId] = CompilationJob(
            teamId = teamId,
            teamName = teamName,
            status = JobStatus.RECEIVED,
            receivedAt = now,
            updatedAt = now
        )
    }
    
    fun updateJobStatus(teamId: String, status: JobStatus, error: String? = null) {
        jobs[teamId]?.let { job ->
            jobs[teamId] = job.copy(
                status = status,
                updatedAt = System.currentTimeMillis(),
                error = error
            )
        }
    }
    
    fun getJobs(): Map<String, CompilationJob> = jobs.toMap()
    
    fun getRecentJobs(limit: Int = 20): List<CompilationJob> {
        return jobs.values
            .sortedByDescending { it.receivedAt }
            .take(limit)
    }
    
    fun clearOldJobs(olderThanMillis: Long = 3600000) { // Default: 1 hour
        val cutoff = System.currentTimeMillis() - olderThanMillis
        jobs.entries.removeIf { it.value.updatedAt < cutoff }
    }
}