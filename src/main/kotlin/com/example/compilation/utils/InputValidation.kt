package com.example.compilation.utils

import org.slf4j.LoggerFactory

/**
 * Input validation utilities for compilation server
 * Designed to prevent malicious input attacks
 */
object InputValidation {
    private val logger = LoggerFactory.getLogger(InputValidation::class.java)
    
    // Maximum limits
    const val MAX_TEAM_NAME_LENGTH = 15
    const val MAX_TEAM_ID_LENGTH = 50
    const val MAX_CODE_LENGTH = 50000  // 50KB max code
    const val MAX_ERROR_MESSAGE_LENGTH = 500
    const val MAX_PRINT_COMMANDS = 1000
    
    // Regex patterns
    private val TEAM_ID_PATTERN = Regex("^[a-zA-Z0-9_-]+$")
    private val SAFE_TEXT_PATTERN = Regex("^[\\p{L}\\p{N}\\s.,!?()_-]+$")
    
    // Dangerous code patterns to detect potential attacks
    private val DANGEROUS_PATTERNS = listOf(
        "java.lang.Runtime",
        "ProcessBuilder",
        "java.io.File",
        "java.nio.file",
        "System.exit",
        "System.getProperty",
        "System.setProperty",
        "ClassLoader",
        "URLClassLoader",
        "SecurityManager",
        "Unsafe",
        "reflection",
        "invoke",
        "Method.invoke",
        "Constructor.newInstance",
        "\\.exec\\(",
        "executeCommand",
        "getRuntime",
        "loadLibrary",
        "load\\(",
        "finalize\\(",
        "serialVersionUID",
        "ObjectInputStream",
        "ObjectOutputStream",
        "readObject",
        "writeObject",
        "../",
        "..\\\\",
        "%00",
        "\\x00",
        "\\u0000",
        "javascript:",
        "<script",
        "onclick="
    )
    
    /**
     * Sanitize team name - truncate and clean
     */
    fun sanitizeTeamName(input: String?): String {
        if (input.isNullOrBlank()) return "Unknown"
        
        // Remove control characters and dangerous chars
        var sanitized = input
            .replace(Regex("[\\p{C}\\p{Z}]+"), " ")  // Control chars
            .replace(Regex("[<>\"'`\\\\]"), "")       // Script injection
            .take(MAX_TEAM_NAME_LENGTH)
            .trim()
        
        if (sanitized.isEmpty()) sanitized = "Team"
        
        logger.debug("Sanitized team name: '$input' -> '$sanitized'")
        return sanitized
    }
    
    /**
     * Validate and sanitize team ID
     */
    fun sanitizeTeamId(input: String?): String? {
        if (input.isNullOrBlank()) return null
        
        val sanitized = input
            .replace(Regex("[^a-zA-Z0-9_-]"), "")
            .take(MAX_TEAM_ID_LENGTH)
        
        if (!TEAM_ID_PATTERN.matches(sanitized) || sanitized.isEmpty()) {
            logger.warn("Invalid team ID format: '$input'")
            return null
        }
        
        return sanitized
    }
    
    /**
     * Validate interpreter code for dangerous patterns
     */
    fun validateInterpreterCode(code: String?): ValidationResult {
        if (code.isNullOrBlank()) {
            return ValidationResult(false, "Code cannot be empty")
        }
        
        if (code.length > MAX_CODE_LENGTH) {
            return ValidationResult(false, "Code exceeds maximum length of $MAX_CODE_LENGTH characters")
        }
        
        // Check for dangerous patterns
        val lowercaseCode = code.lowercase()
        for (pattern in DANGEROUS_PATTERNS) {
            if (lowercaseCode.contains(pattern.lowercase())) {
                logger.warn("Dangerous pattern detected in code: $pattern")
                return ValidationResult(false, "Code contains restricted operations")
            }
        }
        
        // Check for excessive recursion patterns
        if (hasExcessiveRecursion(code)) {
            return ValidationResult(false, "Code appears to have excessive recursion")
        }
        
        // Check for infinite loop patterns
        if (hasPotentialInfiniteLoop(code)) {
            return ValidationResult(false, "Code may contain infinite loops")
        }
        
        return ValidationResult(true)
    }
    
    /**
     * Check for excessive recursion patterns
     */
    private fun hasExcessiveRecursion(code: String): Boolean {
        // Simple heuristic: count function definitions and self-calls
        val functionPattern = Regex("fun\\s+(\\w+)\\s*\\(")
        val functions = functionPattern.findAll(code).map { it.groupValues[1] }.toList()
        
        for (func in functions) {
            // Count how many times the function calls itself
            val selfCallPattern = Regex("\\b$func\\s*\\(")
            val selfCallCount = selfCallPattern.findAll(code).count()
            
            // If a function calls itself more than 10 times in the code, flag it
            if (selfCallCount > 10) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Check for potential infinite loops
     */
    private fun hasPotentialInfiniteLoop(code: String): Boolean {
        // Look for while(true) or similar patterns
        val infinitePatterns = listOf(
            "while\\s*\\(\\s*true\\s*\\)",
            "while\\s*\\(\\s*1\\s*\\)",
            "for\\s*\\(\\s*;\\s*;\\s*\\)",
            "do\\s*\\{[^}]*\\}\\s*while\\s*\\(\\s*true\\s*\\)"
        )
        
        for (pattern in infinitePatterns) {
            if (Regex(pattern).containsMatchIn(code)) {
                // Check if there's a break statement within the loop
                // This is a simple heuristic and may have false positives
                if (!code.contains("break")) {
                    return true
                }
            }
        }
        
        return false
    }
    
    /**
     * Pass through error messages unfiltered for debugging
     * Client specifically requested unfiltered stack traces
     */
    fun sanitizeErrorMessage(error: String?): String {
        return error ?: "Unknown error"  // Return unmodified for full debugging info
    }
    
    /**
     * Validate print command count
     */
    fun validatePrintCommands(commands: List<Any>?): ValidationResult {
        if (commands == null) {
            return ValidationResult(false, "Commands cannot be null")
        }
        
        if (commands.isEmpty()) {
            return ValidationResult(false, "Commands cannot be empty")
        }
        
        if (commands.size > MAX_PRINT_COMMANDS) {
            return ValidationResult(false, "Too many print commands: ${commands.size} (max: $MAX_PRINT_COMMANDS)")
        }
        
        return ValidationResult(true)
    }
    
    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    )
}