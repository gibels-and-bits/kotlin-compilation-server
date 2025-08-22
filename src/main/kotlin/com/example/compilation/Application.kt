package com.example.compilation

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.callloging.*
import io.ktor.http.*
import com.example.compilation.routes.configureRoutes
import com.example.compilation.compiler.KotlinCompilerService
import com.example.compilation.cache.InterpreterCache
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

fun main() {
    embeddedServer(Netty, port = 3001, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

fun Application.module() {
    // Initialize services
    val interpreterCache = InterpreterCache()
    val compilerService = KotlinCompilerService(interpreterCache)
    
    // Configure plugins
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
    
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        anyHost() // For development - restrict in production
    }
    
    install(CallLogging) {
        level = Level.INFO
    }
    
    // Configure routes
    routing {
        configureRoutes(compilerService)
    }
    
    environment.log.info("Kotlin Compilation Server started on port 3001")
    environment.log.info("Server address: 192.168.29.3:3001")
}