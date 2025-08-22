package com.example.compilation.compiler

import com.example.compilation.cache.InterpreterCache
import com.example.compilation.models.*
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import javax.script.ScriptEngineManager
import javax.script.CompiledScript
import javax.script.Compilable
import javax.script.SimpleBindings

class KotlinCompilerService(private val cache: InterpreterCache) {
    private val logger = LoggerFactory.getLogger(KotlinCompilerService::class.java)
    private val scriptEngineManager = ScriptEngineManager()
    
    fun compile(teamId: String, code: String): CompileResponse {
        return try {
            logger.info("Compiling interpreter for team: $teamId")
            
            // Validate the code structure
            if (!code.contains("fun interpret") || !code.contains("jsonString") || !code.contains("printer")) {
                return CompileResponse(
                    success = false,
                    error = "Invalid interpreter code. Must define: fun interpret(jsonString: String, printer: EpsonPrinter)"
                )
            }
            
            // Get Kotlin script engine
            val engine = scriptEngineManager.getEngineByExtension("kts")
                ?: return CompileResponse(
                    success = false,
                    error = "Kotlin script engine not available"
                )
            
            // Wrap the interpreter code with necessary imports and structure
            val wrappedCode = wrapInterpreterCode(code)
            
            // Compile the script
            val compilable = engine as Compilable
            val compiledScript = compilable.compile(wrappedCode)
            
            // Cache the compiled script
            cache.put(teamId, code, compiledScript)
            
            logger.info("Successfully compiled interpreter for team: $teamId")
            CompileResponse(
                success = true,
                message = "Interpreter compiled and cached successfully"
            )
            
        } catch (e: Exception) {
            logger.error("Failed to compile interpreter for team $teamId", e)
            
            // Try to extract line number from error
            val lineNumber = extractLineNumber(e.message)
            
            CompileResponse(
                success = false,
                error = e.message ?: "Compilation failed",
                lineNumber = lineNumber
            )
        }
    }
    
    fun execute(teamId: String, jsonData: String): ExecuteResponse {
        return try {
            logger.info("Executing interpreter for team: $teamId")
            
            val cachedInterpreter = cache.get(teamId)
                ?: return ExecuteResponse(
                    success = false,
                    error = "No compiled interpreter found for team $teamId. Please submit interpreter first."
                )
            
            // Create command capture printer
            val printer = CommandCapturePrinter()
            
            // Create bindings for the script
            val bindings = SimpleBindings().apply {
                put("printer", printer)
                put("jsonString", jsonData)
            }
            
            // Execute with timeout
            runBlocking {
                withTimeout(5000L) {
                    cachedInterpreter.compiledScript.eval(bindings)
                }
            }
            
            // Get captured commands
            val commands = printer.getCommands().map { it.toSerializable() }
            
            logger.info("Successfully executed interpreter for team $teamId, captured ${commands.size} commands")
            
            ExecuteResponse(
                success = true,
                commands = commands
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
    
    private fun wrapInterpreterCode(code: String): String {
        return """
            import com.example.compilation.compiler.*
            import org.json.JSONObject
            import org.json.JSONArray
            import kotlin.math.*
            
            // Get bindings
            val printer = bindings["printer"] as com.example.compilation.compiler.EpsonPrinter
            val jsonString = bindings["jsonString"] as String
            
            // User's interpreter code
            $code
            
            // Execute the interpret function
            try {
                interpret(jsonString, printer)
            } catch (e: Exception) {
                throw RuntimeException("Interpreter execution failed: " + e.message, e)
            }
        """.trimIndent()
    }
    
    private fun extractLineNumber(errorMessage: String?): Int? {
        if (errorMessage == null) return null
        
        // Try to extract line number from error message
        val linePattern = Regex("line (\\d+)")
        val match = linePattern.find(errorMessage)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()
    }
}