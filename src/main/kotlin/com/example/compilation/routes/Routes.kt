package com.example.compilation.routes

import io.ktor.server.routing.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.http.*
import com.example.compilation.compiler.KotlinCompilerService
import com.example.compilation.models.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun Routing.configureRoutes(compilerService: KotlinCompilerService) {
    
    // Health check
    get("/health") {
        call.respondText(
            """
            {
                "status": "healthy",
                "service": "Kotlin Compilation Server",
                "version": "1.0.0",
                "cache_size": ${compilerService.getCacheStatus().size}
            }
            """.trimIndent(),
            ContentType.Application.Json
        )
    }
    
    // Compile interpreter
    post("/compile") {
        try {
            val request = call.receive<CompileRequest>()
            
            if (request.teamId.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Team ID is required"))
                return@post
            }
            
            if (request.code.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Interpreter code is required"))
                return@post
            }
            
            val response = compilerService.compile(request.teamId, request.code, request.teamName)
            
            if (response.success) {
                call.respond(HttpStatusCode.OK, response)
            } else {
                call.respond(HttpStatusCode.BadRequest, response)
            }
            
        } catch (e: Exception) {
            application.log.error("Error in /compile endpoint", e)
            call.respond(HttpStatusCode.InternalServerError, mapOf(
                "error" to "Failed to process compilation request",
                "details" to e.message
            ))
        }
    }
    
    // Execute interpreter with JSON data
    post("/execute") {
        try {
            val request = call.receive<ExecuteRequest>()
            
            if (request.teamId.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Team ID is required"))
                return@post
            }
            
            val response = compilerService.execute(request.teamId, request.jsonData, request.round)
            
            if (response.success) {
                call.respond(HttpStatusCode.OK, response)
            } else {
                call.respond(HttpStatusCode.BadRequest, response)
            }
            
        } catch (e: Exception) {
            application.log.error("Error in /execute endpoint", e)
            call.respond(HttpStatusCode.InternalServerError, mapOf(
                "error" to "Failed to execute interpreter",
                "details" to e.message
            ))
        }
    }
    
    // Clear cache for a team
    delete("/cache/{teamId}") {
        val teamId = call.parameters["teamId"]
        
        if (teamId.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Team ID is required"))
            return@delete
        }
        
        val removed = compilerService.clearCache(teamId)
        
        if (removed) {
            call.respond(HttpStatusCode.OK, mapOf(
                "success" to true,
                "message" to "Cache cleared for team $teamId"
            ))
        } else {
            call.respond(HttpStatusCode.NotFound, mapOf(
                "success" to false,
                "message" to "No cached interpreter found for team $teamId"
            ))
        }
    }
    
    // Get cache status
    get("/cache/status") {
        val status = compilerService.getCacheStatus()
        val teams = status.map { (teamId, info) ->
            TeamCacheInfo(teamId, info)
        }
        call.respond(HttpStatusCode.OK, CacheStatusResponse(
            cache_size = status.size,
            teams = teams
        ))
    }
    
    // Test endpoint for direct interpreter execution (for debugging)
    post("/test") {
        try {
            val body = call.receiveText()
            val json = Json.parseToJsonElement(body).jsonObject
            
            val code = json["code"]?.jsonPrimitive?.content ?: ""
            val jsonData = json["jsonData"]?.jsonPrimitive?.content ?: "{}"
            
            // Compile and execute immediately without caching
            val testTeamId = "test_${System.currentTimeMillis()}"
            
            val compileResponse = compilerService.compile(testTeamId, code, "Test Team")
            if (!compileResponse.success) {
                call.respond(HttpStatusCode.BadRequest, compileResponse)
                return@post
            }
            
            val executeResponse = compilerService.execute(testTeamId, jsonData)
            
            // Clean up test cache
            compilerService.clearCache(testTeamId)
            
            call.respond(HttpStatusCode.OK, executeResponse)
            
        } catch (e: Exception) {
            application.log.error("Error in /test endpoint", e)
            call.respond(HttpStatusCode.InternalServerError, mapOf(
                "error" to "Test execution failed",
                "details" to e.message
            ))
        }
    }
}