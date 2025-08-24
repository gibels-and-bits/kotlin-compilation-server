package com.example.compilation.compiler

import com.example.compilation.cache.InterpreterCache
import com.example.compilation.models.*
import com.example.compilation.orders.OrderRepository
import com.example.compilation.jobs.JobTracker
import com.example.compilation.jobs.JobStatus
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import javax.script.ScriptEngineManager
import javax.script.CompiledScript
import javax.script.Compilable
import javax.script.SimpleBindings
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import java.time.Duration
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class AndroidServerNotification(
    val team_id: String,
    val team_name: String,
    val compilation_status: String,
    val error_message: String? = null
)

class KotlinCompilerService(
    private val cache: InterpreterCache,
    private val jobTracker: JobTracker = JobTracker()
) {
    private val logger = LoggerFactory.getLogger(KotlinCompilerService::class.java)
    private val scriptEngineManager = ScriptEngineManager()
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val androidServerUrl = System.getenv("ANDROID_SERVER_URL") ?: 
        if (System.getProperty("debug") == "true") "http://localhost:8080" else "http://192.168.29.2:8080"
    
    fun compile(teamId: String, code: String, teamName: String? = null): CompileResponse {
        // Track job received
        jobTracker.trackJobReceived(teamId, teamName)
        
        return try {
            logger.info("Compiling interpreter for team: $teamId")
            
            // Update job status to compiling
            jobTracker.updateJobStatus(teamId, JobStatus.COMPILING)
            
            // Validate the code structure - now requires Order parameter
            if (!code.contains("fun interpret") || !code.contains("jsonString") || !code.contains("printer") || !code.contains("order")) {
                jobTracker.updateJobStatus(teamId, JobStatus.FAILED, "Invalid interpreter code")
                return CompileResponse(
                    success = false,
                    error = "Invalid interpreter code. Must define: fun interpret(jsonString: String, printer: EpsonPrinter, order: Order?)"
                )
            }
            
            // Get Kotlin script engine
            val engine = scriptEngineManager.getEngineByExtension("kts")
                ?: run {
                    jobTracker.updateJobStatus(teamId, JobStatus.FAILED, "Kotlin script engine not available")
                    return CompileResponse(
                        success = false,
                        error = "Kotlin script engine not available"
                    )
                }
            
            // Wrap the interpreter code with necessary imports and structure
            val wrappedCode = wrapInterpreterCode(code)
            
            // Compile the script
            val compilable = engine as Compilable
            val compiledScript = compilable.compile(wrappedCode)
            
            // Cache the compiled script
            cache.put(teamId, code, compiledScript)
            
            logger.info("Successfully compiled interpreter for team: $teamId")
            
            // Update job status to success
            jobTracker.updateJobStatus(teamId, JobStatus.SUCCESS)
            
            // Notify Android server of successful compilation
            notifyAndroidServer(teamId, teamName ?: teamId, "success", null)
            
            CompileResponse(
                success = true,
                message = "Interpreter compiled and cached successfully"
            )
            
        } catch (e: Exception) {
            logger.error("Failed to compile interpreter for team $teamId", e)
            
            // Try to extract line number from error
            val lineNumber = extractLineNumber(e.message)
            
            // Format the error message for better readability
            val errorMessage = when {
                e.message?.contains("Unresolved reference") == true -> {
                    val unresolvedRef = e.message?.substringAfter("Unresolved reference: ")?.substringBefore("\n") ?: ""
                    "Unresolved reference: '$unresolvedRef'\nMake sure to use the correct class names (e.g., EpsonPrinter, JSONObject, JSONArray)"
                }
                e.message?.contains("Type mismatch") == true -> {
                    "Type mismatch error:\n${e.message}"
                }
                e.message?.contains("Expecting") == true -> {
                    "Syntax error:\n${e.message}"
                }
                else -> e.message ?: "Compilation failed"
            }
            
            // Update job status to failed
            jobTracker.updateJobStatus(teamId, JobStatus.FAILED, errorMessage)
            
            // Notify Android server of failed compilation
            notifyAndroidServer(teamId, teamName ?: teamId, "failed", errorMessage)
            
            CompileResponse(
                success = false,
                error = errorMessage,
                lineNumber = lineNumber
            )
        }
    }
    
    fun execute(teamId: String, jsonData: String, round: Int = 0): ExecuteResponse {
        return try {
            logger.info("Executing interpreter for team: $teamId, round: $round")
            
            val cachedInterpreter = cache.get(teamId)
                ?: return ExecuteResponse(
                    success = false,
                    error = "No compiled interpreter found for team $teamId. Please submit interpreter first."
                )
            
            // Get order for this round
            val order = OrderRepository.getOrderForRound(round)
            logger.info("Using order for round $round: ${order?.orderId ?: "no order"}")
            
            // Create command capture printer
            val printer = CommandCapturePrinter()
            
            // Create bindings for the script
            val bindings = SimpleBindings().apply {
                put("printer", printer)
                put("jsonString", jsonData)
                put("order", order)
            }
            
            // Execute with timeout
            runBlocking {
                withTimeout(5000L) {
                    cachedInterpreter.compiledScript.eval(bindings)
                }
            }
            
            // Get captured commands
            val userCommands = printer.getCommands()
            
            // Prepend header with team and round info
            val headerCommands = mutableListOf<InternalPrinterCommand>()
            headerCommands.add(InternalPrinterCommand.AddTextAlign("CENTER"))
            headerCommands.add(InternalPrinterCommand.AddText("═══════════════════════"))
            headerCommands.add(InternalPrinterCommand.AddTextStyle(true, "LARGE", false))
            headerCommands.add(InternalPrinterCommand.AddText("TEAM: $teamId"))
            headerCommands.add(InternalPrinterCommand.AddTextStyle(true, "NORMAL", false))
            headerCommands.add(InternalPrinterCommand.AddText("ROUND: $round"))
            headerCommands.add(InternalPrinterCommand.AddText("═══════════════════════"))
            headerCommands.add(InternalPrinterCommand.AddFeedLine(1))
            headerCommands.add(InternalPrinterCommand.AddTextAlign("LEFT"))
            
            // Combine header and user commands
            val allCommands = (headerCommands + userCommands).map { it.toSerializable() }
            
            logger.info("Successfully executed interpreter for team $teamId, captured ${allCommands.size} commands")
            
            ExecuteResponse(
                success = true,
                commands = allCommands
            )
            
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            logger.error("Execution timeout for team $teamId")
            ExecuteResponse(
                success = false,
                error = "Execution timeout (5 seconds)"
            )
        } catch (e: Exception) {
            logger.error("Failed to execute interpreter for team $teamId", e)
            ExecuteResponse(
                success = false,
                error = e.message ?: "Execution failed"
            )
        }
    }
    
    fun clearCache(teamId: String): Boolean {
        return cache.remove(teamId)
    }
    
    fun getCacheStatus(): Map<String, String> {
        return cache.getAll().mapValues { (_, cached) ->
            "Compiled at: ${java.time.Instant.ofEpochMilli(cached.compiledAt)}"
        }
    }
    
    fun getJobs() = jobTracker.getJobs()
    
    fun getRecentJobs(limit: Int = 20) = jobTracker.getRecentJobs(limit)
    
    private fun wrapInterpreterCode(code: String): String {
        return """
            import com.example.compilation.compiler.*
            import com.example.compilation.models.*
            import org.json.JSONObject
            import org.json.JSONArray
            import kotlin.math.*
            
            // Get bindings
            val printer = bindings["printer"] as com.example.compilation.compiler.EpsonPrinter
            val jsonString = bindings["jsonString"] as String
            val order = bindings["order"] as? com.example.compilation.models.Order
            
            // User's interpreter code
            $code
            
            // Execute the interpret function
            try {
                interpret(jsonString, printer, order)
            } catch (e: Exception) {
                throw RuntimeException("Interpreter execution failed: " + e.message, e)
            }
        """.trimIndent()
    }
    
    private fun extractLineNumber(errorMessage: String?): Int? {
        if (errorMessage == null) return null
        
        // Try multiple patterns to extract line number from error message
        val patterns = listOf(
            Regex("line (\\d+)"),
            Regex(":(\\d+):"),
            Regex("at line (\\d+)"),
            Regex("Line (\\d+)")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(errorMessage)
            val lineNum = match?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (lineNum != null) {
                // Adjust for wrapper code offset (the wrapper adds ~8 lines before user code)
                return lineNum - 8
            }
        }
        
        return null
    }
    
    private fun notifyAndroidServer(teamId: String, teamName: String, status: String, errorMessage: String?) {
        try {
            val notification = AndroidServerNotification(
                team_id = teamId,
                team_name = teamName,
                compilation_status = status,
                error_message = errorMessage
            )
            
            val requestBody = json.encodeToString(notification)
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$androidServerUrl/api/register-upload"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(2))
                .build()
            
            // Send notification asynchronously - don't wait for response
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete { response, throwable ->
                    if (throwable != null) {
                        logger.warn("Failed to notify Android server about upload status: ${throwable.message}")
                    } else {
                        logger.info("Notified Android server: team=$teamId, status=$status, responseCode=${response.statusCode()}")
                    }
                }
        } catch (e: Exception) {
            // Don't fail compilation due to notification issues
            logger.warn("Error notifying Android server: ${e.message}")
        }
    }
}