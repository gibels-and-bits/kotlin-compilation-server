package com.example.compilation.routes

import io.ktor.server.routing.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.http.*
import com.example.compilation.compiler.KotlinCompilerService
import com.example.compilation.models.*
import com.example.compilation.printer.ASCIIPrinter
import com.example.compilation.utils.InputValidation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

// ANSI color codes for console output
private const val CYAN = "\u001B[36m"
private const val GREEN = "\u001B[32m"
private const val NC = "\u001B[0m" // No Color

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
            
            // Validate and sanitize team ID
            val teamId = InputValidation.sanitizeTeamId(request.teamId)
            if (teamId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid team ID format"))
                return@post
            }
            
            // Validate interpreter code
            val codeValidation = InputValidation.validateInterpreterCode(request.code)
            if (!codeValidation.isValid) {
                call.respond(HttpStatusCode.BadRequest, mapOf(
                    "error" to (codeValidation.errorMessage ?: "Invalid code")
                ))
                return@post
            }
            
            // Sanitize team name
            val teamName = InputValidation.sanitizeTeamName(request.teamName)
            
            val response = compilerService.compile(teamId, request.code, teamName)
            
            if (response.success) {
                call.respond(HttpStatusCode.OK, response)
            } else {
                call.respond(HttpStatusCode.BadRequest, response)
            }
            
        } catch (e: Exception) {
            application.log.error("Error in /compile endpoint", e)
            call.respond(HttpStatusCode.InternalServerError, mapOf(
                "error" to "Failed to process compilation request",
                "details" to e.message,
                "stackTrace" to e.stackTraceToString()
            ))
        }
    }
    
    // Execute interpreter with JSON data
    post("/execute") {
        try {
            val request = call.receive<ExecuteRequest>()
            
            // Validate and sanitize team ID
            val teamId = InputValidation.sanitizeTeamId(request.teamId)
            if (teamId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid team ID format"))
                return@post
            }
            
            val response = compilerService.execute(teamId, request.jsonData, request.round)
            
            // If execution succeeded and we have commands, optionally render ASCII
            if (response.success && response.commands != null) {
                // Check if Android server is unavailable (could be a config flag)
                val androidOffline = System.getenv("ANDROID_OFFLINE") == "true" || 
                                   System.getProperty("android.offline") == "true"
                
                if (androidOffline) {
                    // Render ASCII receipt for debugging
                    val asciiPrinter = ASCIIPrinter()
                    response.commands?.forEach { cmd ->
                        when (cmd.type) {
                            "ADD_TEXT" -> asciiPrinter.addText(cmd.text ?: "")
                            "ADD_TEXT_STYLE" -> asciiPrinter.addTextStyle(
                                cmd.bold ?: false,
                                cmd.size ?: "NORMAL",
                                cmd.underline ?: false
                            )
                            "ADD_TEXT_ALIGN" -> asciiPrinter.addTextAlign(cmd.alignment ?: "LEFT")
                            "ADD_QR_CODE" -> asciiPrinter.addQRCode(cmd.data ?: "", cmd.qrSize ?: 3)
                            "ADD_BARCODE" -> asciiPrinter.addBarcode(cmd.data ?: "", cmd.text ?: "CODE128")
                            "ADD_FEED_LINE" -> asciiPrinter.addFeedLine(cmd.lines ?: 1)
                            "CUT_PAPER" -> asciiPrinter.cutPaper()
                        }
                    }
                    
                    // Create receipts directory if it doesn't exist
                    val receiptsDir = File("receipts")
                    if (!receiptsDir.exists()) {
                        receiptsDir.mkdirs()
                    }
                    
                    // Save ASCII receipt to file in receipts directory
                    val asciiFile = "receipts/ascii-receipt-${request.teamId}.txt"
                    asciiPrinter.renderToFile(asciiFile)
                    application.log.info("ASCII receipt saved to: $asciiFile")
                }
            }
            
            if (response.success) {
                call.respond(HttpStatusCode.OK, response)
            } else {
                call.respond(HttpStatusCode.BadRequest, response)
            }
            
        } catch (e: Exception) {
            application.log.error("Error in /execute endpoint", e)
            call.respond(HttpStatusCode.InternalServerError, mapOf(
                "error" to "Failed to execute interpreter",
                "details" to e.message,
                "stackTrace" to e.stackTraceToString()
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
    
    // Get compilation jobs
    get("/jobs") {
        val jobs = compilerService.getRecentJobs(50)
        call.respond(HttpStatusCode.OK, mapOf(
            "jobs" to jobs,
            "count" to jobs.size
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