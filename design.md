# MoatBot Gateway POC - Go Implementation Plan

## Overview

A minimal gateway POC in Go following TDD Chicago School with a simple 3-layer architecture.

---

## Architecture

```
moatbot/
├── cmd/
│   └── moatbot/
│       └── main.go              # Entry point
├── domain/                      # Pure business logic (no dependencies)
│   ├── message.go               # Message, ToolCall, ToolResult types
│   ├── session.go               # Session aggregate
│   ├── protocol.go              # Frame types (Request/Response/Event)
│   └── provider.go              # Provider result types
├── app/                         # Application services (orchestration)
│   ├── gateway.go               # Gateway service (coordinates domain + infra)
│   ├── chat.go                  # Chat handler
│   ├── tools.go                 # Tool registry & invocation
│   └── acp.go                   # ACP bridge service
├── infra/                       # External adapters (mockable boundaries)
│   ├── ws/
│   │   └── server.go            # WebSocket server (gorilla/websocket)
│   ├── http/
│   │   └── server.go            # HTTP server (OpenAI-compatible endpoints)
│   ├── provider/
│   │   ├── provider.go          # Provider interface
│   │   ├── anthropic.go         # Anthropic Claude adapter
│   │   └── openai.go            # OpenAI adapter
│   ├── acp/
│   │   └── stdio.go             # ACP stdio transport
│   └── store/
│       └── session.go           # Session persistence
├── go.mod
└── go.sum
```

---

## Domain Layer (Pure, No Dependencies)

### Core Types

```go
// domain/protocol.go
type FrameType string
const (
    FrameTypeRequest  FrameType = "req"
    FrameTypeResponse FrameType = "res"
    FrameTypeEvent    FrameType = "event"
)

type RequestFrame struct {
    Type   FrameType       `json:"type"`
    ID     string          `json:"id"`
    Method string          `json:"method"`
    Params json.RawMessage `json:"params,omitempty"`
}

type ResponseFrame struct {
    Type    FrameType       `json:"type"`
    ID      string          `json:"id"`
    OK      bool            `json:"ok"`
    Payload json.RawMessage `json:"payload,omitempty"`
    Error   *ErrorPayload   `json:"error,omitempty"`
}

type EventFrame struct {
    Type    FrameType       `json:"type"`
    Event   string          `json:"event"`
    Payload json.RawMessage `json:"payload,omitempty"`
}
```

```go
// domain/provider.go - Sum types via interface + type switch
type ProviderResult interface {
    providerResult()
}

type Success struct {
    Text string
}
func (Success) providerResult() {}

type ToolCalls struct {
    Calls []ToolCall
}
func (ToolCalls) providerResult() {}

type ProviderError struct {
    Code    string
    Message string
    Retry   bool
}
func (ProviderError) providerResult() {}
```

```go
// domain/session.go - Session aggregate
type Session struct {
    Key       SessionKey
    Messages  []Message
    CreatedAt time.Time
}

type SessionKey string

func NewSession(key SessionKey) *Session {
    return &Session{
        Key:       key,
        Messages:  []Message{},
        CreatedAt: time.Now(),
    }
}

func (s *Session) AddMessage(msg Message) {
    s.Messages = append(s.Messages, msg)
}
```

---

## App Layer (Orchestration)

```go
// app/gateway.go
type Gateway struct {
    provider Provider      // infra boundary
    sessions SessionStore  // infra boundary
    tools    *ToolRegistry
}

func NewGateway(provider Provider, sessions SessionStore) *Gateway {
    return &Gateway{
        provider: provider,
        sessions: sessions,
        tools:    NewToolRegistry(),
    }
}

func (g *Gateway) HandleChat(ctx context.Context, req ChatRequest) (ChatResponse, error) {
    session, err := g.sessions.Get(ctx, req.SessionKey)
    if err != nil {
        session = domain.NewSession(req.SessionKey)
    }

    session.AddMessage(domain.UserMessage(req.Message))

    result, err := g.provider.Complete(ctx, CompletionRequest{
        Messages: session.Messages,
        Tools:    g.tools.Definitions(),
    })
    if err != nil {
        return ChatResponse{}, err
    }

    switch r := result.(type) {
    case domain.Success:
        session.AddMessage(domain.AssistantMessage(r.Text))
        g.sessions.Save(ctx, session)
        return ChatResponse{Text: r.Text}, nil
    case domain.ToolCalls:
        // Execute tools, loop back
        return g.executeToolsAndContinue(ctx, session, r.Calls)
    case domain.ProviderError:
        return ChatResponse{}, fmt.Errorf("%s: %s", r.Code, r.Message)
    }
    return ChatResponse{}, errors.New("unknown result type")
}
```

---

## Infra Layer (Boundaries - These Get Mocked)

```go
// infra/provider/provider.go - Port interface
type Provider interface {
    Complete(ctx context.Context, req CompletionRequest) (domain.ProviderResult, error)
}

// infra/store/session.go - Port interface
type SessionStore interface {
    Get(ctx context.Context, key domain.SessionKey) (*domain.Session, error)
    Save(ctx context.Context, session *domain.Session) error
}
```

---

## TDD Chicago School Approach

### Test Structure

```go
// app/gateway_test.go
func TestGateway_HandleChat_ReturnsCompletion(t *testing.T) {
    // Arrange: state-based fake (not mock)
    fakeProvider := &FakeProvider{
        Result: domain.Success{Text: "Hello from AI"},
    }
    fakeStore := NewInMemorySessionStore()
    gateway := app.NewGateway(fakeProvider, fakeStore)

    // Act
    resp, err := gateway.HandleChat(context.Background(), app.ChatRequest{
        SessionKey: "test-session",
        Message:    "Hello",
    })

    // Assert: verify state, not interactions
    assert.NoError(t, err)
    assert.Equal(t, "Hello from AI", resp.Text)

    // Verify session state was updated
    session, _ := fakeStore.Get(context.Background(), "test-session")
    assert.Len(t, session.Messages, 2) // user + assistant
}

func TestGateway_HandleChat_ExecutesToolCalls(t *testing.T) {
    // Arrange
    fakeProvider := &FakeProvider{
        Results: []domain.ProviderResult{
            domain.ToolCalls{Calls: []domain.ToolCall{{Name: "read_file", Args: `{"path":"test.txt"}`}}},
            domain.Success{Text: "File contents: hello"},
        },
    }
    fakeStore := NewInMemorySessionStore()
    gateway := app.NewGateway(fakeProvider, fakeStore)
    gateway.RegisterTool("read_file", func(ctx context.Context, args json.RawMessage) (string, error) {
        return "hello", nil
    })

    // Act
    resp, err := gateway.HandleChat(context.Background(), app.ChatRequest{
        SessionKey: "test-session",
        Message:    "Read test.txt",
    })

    // Assert
    assert.NoError(t, err)
    assert.Contains(t, resp.Text, "hello")
}
```

### Fake vs Mock (Chicago School)

```go
// test/fakes.go - State-based fakes, not mocks

type FakeProvider struct {
    Results []domain.ProviderResult
    calls   int
}

func (f *FakeProvider) Complete(ctx context.Context, req CompletionRequest) (domain.ProviderResult, error) {
    if f.calls >= len(f.Results) {
        return nil, errors.New("no more results configured")
    }
    result := f.Results[f.calls]
    f.calls++
    return result, nil
}

type InMemorySessionStore struct {
    sessions map[domain.SessionKey]*domain.Session
    mu       sync.RWMutex
}

func (s *InMemorySessionStore) Get(ctx context.Context, key domain.SessionKey) (*domain.Session, error) {
    s.mu.RLock()
    defer s.mu.RUnlock()
    if sess, ok := s.sessions[key]; ok {
        return sess, nil
    }
    return nil, ErrNotFound
}

func (s *InMemorySessionStore) Save(ctx context.Context, session *domain.Session) error {
    s.mu.Lock()
    defer s.mu.Unlock()
    s.sessions[session.Key] = session
    return nil
}
```

---

## Implementation Order (TDD Red-Green-Refactor)

### Phase 1: Domain Types

1. `domain/protocol.go` - Frame types with JSON tags
2. `domain/message.go` - Message, ToolCall types
3. `domain/session.go` - Session aggregate
4. `domain/provider.go` - Result sum types

### Phase 2: Gateway Core (TDD)

1. Test: `gateway.HandleChat` returns completion
2. Test: `gateway.HandleChat` executes tool calls
3. Test: `gateway.HandleChat` handles provider errors
4. Implement `app/gateway.go`

### Phase 3: Tool Registry (TDD)

1. Test: Register and invoke tools
2. Test: Tool policy filtering
3. Implement `app/tools.go`

### Phase 4: WebSocket Server

1. Test: Connection handshake
2. Test: Request/response correlation
3. Test: Event broadcasting
4. Implement `infra/ws/server.go`

### Phase 5: HTTP APIs

1. Test: `/v1/chat/completions` OpenAI-compatible
2. Test: `/tools/invoke` direct invocation
3. Implement `infra/http/server.go`

### Phase 6: ACP Bridge

1. Test: Stdio NDJSON parsing
2. Test: Session mapping
3. Test: Event translation
4. Implement `infra/acp/stdio.go` + `app/acp.go`

### Phase 7: Provider Adapters

1. Implement `infra/provider/anthropic.go`
2. Implement `infra/provider/openai.go`

---

## Dependencies

```go
// go.mod
module github.com/user/moatbot

go 1.22

require (
    github.com/gorilla/websocket v1.5.1
    github.com/stretchr/testify v1.9.0
)
```

**Minimal dependencies:**
- `gorilla/websocket` - WebSocket server
- `testify` - Test assertions
- Standard library for HTTP, JSON, stdio

---

## Verification

```bash
# Run all tests
go test ./...

# Run with coverage
go test -cover ./...

# Run specific package
go test ./app/...

# Build
go build ./cmd/moatbot

# Run
./moatbot --port 18789
```

---

## Key Design Decisions

1. **Simple 3-layer structure:** `domain/`, `app/`, `infra/` - no deeper nesting
2. **Interfaces at boundaries only:** Provider and SessionStore are the only interfaces
3. **State-based fakes:** Chicago School - test behavior through public APIs
4. **Sum types via interfaces:** Go pattern for algebraic data types
5. **No framework:** Standard library + gorilla/websocket only
6. **Context propagation:** All operations accept `context.Context` for cancellation

---

## Test Coverage

| Layer | Coverage |
|-------|----------|
| app | 92.8% |
| domain | 95.2% |
| infra/store | 86.7% |
| infra/http | 72.0% |
| infra/acp | 61.3% |

---

## Usage

```bash
cd /Users/hanrenwei/github/learning/moatbot

# HTTP/WebSocket mode (default)
ANTHROPIC_API_KEY=sk-... ./moatbot --port 18789

# With OpenAI
OPENAI_API_KEY=sk-... ./moatbot --provider openai

# ACP stdio mode (for subprocess integration)
ANTHROPIC_API_KEY=sk-... ./moatbot --acp

# With session persistence
./moatbot --sessions ~/.moatbot/sessions
```

---

## Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/health` | Health check |
| POST | `/v1/chat/completions` | OpenAI-compatible chat API |
| POST | `/tools/invoke` | Direct tool invocation |
| DELETE | `/sessions/{key}` | Delete session |
| WS | `ws://localhost:18789/ws` | WebSocket connection |

---

## Examples

### Simple Chat Request

```bash
curl -X POST http://localhost:18789/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "claude-sonnet-4-20250514",
    "messages": [{"role": "user", "content": "Hello, who are you?"}]
  }'
```

### With Session Persistence

```bash
curl -X POST http://localhost:18789/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "claude-sonnet-4-20250514",
    "messages": [{"role": "user", "content": "What is 2+2?"}],
    "session_id": "my-session"
  }'
```

### Health Check

```bash
curl http://localhost:18789/health
```

### WebSocket

```bash
# Install websocat (if needed)
brew install websocat

# Connect and send messages
websocat ws://localhost:18789/ws

# Then type JSON messages:
{"type":"req","id":"1","method":"chat","params":{"message":"Hello!"}}
```

### Quick Test

```bash
curl -s -X POST http://localhost:18789/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model":"claude","messages":[{"role":"user","content":"Say hi"}]}' | jq .
```