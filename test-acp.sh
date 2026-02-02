#!/bin/bash
# test-acp.sh - Interactive ACP test

JAR="build/libs/moatbot-kotlin-0.1.0-SNAPSHOT-all.jar"

# Check jar exists
if [ ! -f "$JAR" ]; then
    echo "Error: $JAR not found. Run: ./gradlew shadowJar"
    exit 1
fi

echo "=== ACP Protocol Test ==="
echo ""
echo "Starting MoatBot ACP agent..."
echo "You can send JSON-RPC messages. Example flow:"
echo ""
echo '1. Initialize:'
echo '   {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":1,"clientCapabilities":{}}}'
echo ""
echo '2. Create session:'
echo '   {"jsonrpc":"2.0","id":2,"method":"session/new","params":{"cwd":"/tmp","mcpServers":[]}}'
echo ""
echo '3. Send prompt (replace SESSION_ID with actual ID from step 2):'
echo '   {"jsonrpc":"2.0","id":3,"method":"session/prompt","params":{"sessionId":"session-89c49a31-4996-4b46-982a-30218ec3ece9","prompt":[{"type":"text","text":"Hello!"}]}}'
echo ""
echo "Press Ctrl+C to exit"
echo "========================"
echo ""

# Run interactively - you type JSON, it responds
java -jar "$JAR" --acp
