# Kotlin Compilation Server

A Ktor-based server that provides remote Kotlin code compilation and execution services. This server allows teams to submit Kotlin interpreter code that processes JSON data and generates printer commands, primarily designed for hackathon projects involving receipt printer integrations.

## Features

- **Dynamic Kotlin Compilation**: Compile Kotlin code at runtime using JSR-223 scripting
- **Team-based Caching**: Compiled interpreters are cached per team for efficient re-execution
- **JSON Processing**: Interpreters can process JSON input data to generate dynamic outputs
- **Printer Command Generation**: Built-in support for Epson printer command generation
- **RESTful API**: Clean HTTP endpoints for all operations
- **CORS Support**: Enabled for cross-origin requests during development
- **Timeout Protection**: 5-second execution timeout to prevent runaway scripts

## Architecture

The server consists of several key components:

- **Application.kt**: Main entry point, configures Ktor server with plugins (CORS, Content Negotiation, Logging)
- **KotlinCompilerService**: Core service handling compilation and execution of Kotlin scripts
- **InterpreterCache**: Thread-safe caching mechanism for compiled scripts
- **Routes**: RESTful API endpoints for compilation, execution, and cache management
- **PrinterInterface**: Abstraction for Epson printer commands

## Prerequisites

- Java 17 or higher
- Gradle 7.x or higher
- Kotlin 1.9.20

## Installation

1. Clone the repository:
```bash
git clone <repository-url>
cd kotlin-compilation-server
```

2. Build the project:
```bash
./gradlew build
```

3. Run the server:
```bash
./gradlew run
```

The server will start on port 3001 by default.

## API Documentation

### Base URL
```
http://localhost:3001
```

### Endpoints

#### 1. Health Check
**GET** `/health`

Check the server status and get basic information.

**Response:**
```json
{
  "status": "healthy",
  "service": "Kotlin Compilation Server",
  "version": "1.0.0",
  "cache_size": 0
}
```

---

#### 2. Compile Interpreter
**POST** `/compile`

Submit Kotlin interpreter code for compilation. The code must define a function with the signature:
```kotlin
fun interpret(jsonString: String, printer: EpsonPrinter)
```

**Request Body:**
```json
{
  "teamId": "team-123",
  "code": "fun interpret(jsonString: String, printer: EpsonPrinter) {\n    // Your interpreter code here\n}"
}
```

**Success Response (200):**
```json
{
  "success": true,
  "message": "Interpreter compiled and cached successfully"
}
```

**Error Response (400):**
```json
{
  "success": false,
  "error": "Compilation error message",
  "lineNumber": 42
}
```

---

#### 3. Execute Interpreter
**POST** `/execute`

Execute a previously compiled interpreter with JSON data input.

**Request Body:**
```json
{
  "teamId": "team-123",
  "jsonData": "{\"items\": [{\"name\": \"Coffee\", \"price\": 4.50}]}"
}
```

**Success Response (200):**
```json
{
  "success": true,
  "commands": [
    {
      "type": "ADD_TEXT",
      "params": {
        "text": "Receipt Header"
      }
    },
    {
      "type": "ADD_TEXT_STYLE",
      "params": {
        "bold": "true",
        "size": "LARGE",
        "underline": "false"
      }
    },
    {
      "type": "ADD_QR_CODE",
      "params": {
        "data": "https://example.com",
        "size": "3"
      }
    },
    {
      "type": "CUT_PAPER",
      "params": {}
    }
  ]
}
```

**Error Response (400):**
```json
{
  "success": false,
  "error": "No compiled interpreter found for team team-123. Please submit interpreter first."
}
```

---

#### 4. Clear Team Cache
**DELETE** `/cache/{teamId}`

Remove a team's compiled interpreter from the cache.

**Success Response (200):**
```json
{
  "success": true,
  "message": "Cache cleared for team team-123"
}
```

**Error Response (404):**
```json
{
  "success": false,
  "message": "No cached interpreter found for team team-123"
}
```

---

#### 5. Get Cache Status
**GET** `/cache/status`

Get information about all cached interpreters.

**Response:**
```json
{
  "cache_size": 2,
  "teams": {
    "team-123": "Compiled at: 2024-01-20T10:30:00Z",
    "team-456": "Compiled at: 2024-01-20T11:00:00Z"
  }
}
```

---

#### 6. Test Endpoint
**POST** `/test`

Test interpreter compilation and execution in a single call (for debugging). The interpreter is not cached.

**Request Body:**
```json
{
  "code": "fun interpret(jsonString: String, printer: EpsonPrinter) {\n    printer.addText(\"Test\")\n}",
  "jsonData": "{}"
}
```

**Response:** Same as Execute Interpreter endpoint

## Printer Commands

The server supports the following printer command types:

- **ADD_TEXT**: Add text to the receipt
- **ADD_TEXT_STYLE**: Set text styling (bold, size, underline)
- **ADD_TEXT_ALIGN**: Set text alignment (LEFT, CENTER, RIGHT)
- **ADD_QR_CODE**: Add a QR code with custom data and size
- **ADD_FEED_LINE**: Add blank lines
- **CUT_PAPER**: Cut the receipt paper

### Available Classes in Interpreter

When writing interpreter code, you have access to:

- `EpsonPrinter` interface with methods:
  - `addText(text: String)`
  - `addTextStyle(style: TextStyle)`
  - `addTextAlign(alignment: Alignment)`
  - `addQRCode(data: String, options: QRCodeOptions?)`
  - `addFeedLine(lines: Int)`
  - `cutPaper()`

- `TextStyle` data class with properties:
  - `bold: Boolean`
  - `underline: Boolean`
  - `size: TextSize` (SMALL, NORMAL, LARGE, XLARGE)

- `Alignment` enum: LEFT, CENTER, RIGHT

- `QRCodeOptions` for QR code configuration

- JSON parsing via `org.json.JSONObject` and `org.json.JSONArray`

## Example Interpreter Code

```kotlin
fun interpret(jsonString: String, printer: EpsonPrinter) {
    val json = JSONObject(jsonString)
    
    // Print header
    printer.addTextAlign(Alignment.CENTER)
    printer.addTextStyle(TextStyle(bold = true, size = TextSize.LARGE))
    printer.addText("RECEIPT\n")
    
    // Print items
    printer.addTextAlign(Alignment.LEFT)
    printer.addTextStyle(TextStyle(size = TextSize.NORMAL))
    
    val items = json.getJSONArray("items")
    for (i in 0 until items.length()) {
        val item = items.getJSONObject(i)
        val name = item.getString("name")
        val price = item.getDouble("price")
        printer.addText("$name - $$price\n")
    }
    
    // Add QR code
    printer.addFeedLine(2)
    printer.addQRCode("https://example.com/receipt", QRCodeOptions(size = 4))
    
    // Cut paper
    printer.addFeedLine(3)
    printer.cutPaper()
}
```

## Error Handling

- **Compilation Errors**: Returns detailed error messages with line numbers when possible
- **Execution Timeout**: Scripts have a 5-second timeout to prevent infinite loops
- **Missing Interpreter**: Clear error when trying to execute without compiling first
- **Invalid Code Structure**: Validates that required function signature is present

## Development

### Running Tests
```bash
./gradlew test
```

### Building JAR
```bash
./gradlew build
```

The JAR file will be created in `build/libs/`.

### Configuration

Server configuration can be modified in `Application.kt`:
- Port: Default 3001
- Host: Default 0.0.0.0
- CORS: Currently allows all origins (restrict in production)
- Timeout: 5 seconds for script execution

## Security Considerations

- **Input Validation**: Always validate interpreter code before compilation
- **Timeout Protection**: 5-second execution timeout prevents DoS attacks
- **CORS**: Configure appropriate origins for production
- **Script Isolation**: Consider sandboxing for production deployments
- **Rate Limiting**: Implement rate limiting for production use

## License

[Add your license here]

## Contributing

[Add contribution guidelines if applicable]