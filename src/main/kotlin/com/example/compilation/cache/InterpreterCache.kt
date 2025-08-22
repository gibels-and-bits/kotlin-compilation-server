package com.example.compilation.cache

import java.util.concurrent.ConcurrentHashMap
import javax.script.CompiledScript
import org.slf4j.LoggerFactory

data class CachedInterpreter(
    val teamId: String,
    val originalCode: String,
    val compiledScript: CompiledScript,
    val compiledAt: Long = System.currentTimeMillis()
)

class InterpreterCache {
    private val logger = LoggerFactory.getLogger(InterpreterCache::class.java)
    private val cache = ConcurrentHashMap<String, CachedInterpreter>()
    
    fun put(teamId: String, code: String, compiled: CompiledScript) {
        val cached = CachedInterpreter(teamId, code, compiled)
        cache[teamId] = cached
        logger.info("Cached interpreter for team: $teamId")
    }
    
    fun get(teamId: String): CachedInterpreter? {
        return cache[teamId]
    }
    
    fun remove(teamId: String): Boolean {
        val removed = cache.remove(teamId) != null
        if (removed) {
            logger.info("Removed cached interpreter for team: $teamId")
        }
        return removed
    }
    
    fun clear() {
        cache.clear()
        logger.info("Cleared all cached interpreters")
    }
    
    fun getAll(): Map<String, CachedInterpreter> {
        return cache.toMap()
    }
    
    fun size(): Int = cache.size
}