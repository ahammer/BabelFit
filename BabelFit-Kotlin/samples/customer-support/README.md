# Customer Support Sample

An agentic customer support framework built with BabelFit, demonstrating multi-agent routing, tool-based transfers, and specialist agent context isolation.

## Architecture

```
User Message → RoutingAgent (classifies) → SupportSession → Specialist Agent
                                                                  ↕
                                              Specialist can also transfer
                                              via transfer_to_agent tool
```

### Agents

| Agent | Role | Tools |
|-------|------|-------|
| **Router** | Classifies incoming requests and routes to the right specialist | — |
| **Technical** | Hardware/software troubleshooting, setup, firmware, diagnostics | `lookup_troubleshooting_steps`, `check_firmware_info`, `run_diagnostics` |
| **Billing** | Warranty, returns/RMA, order status, refunds | `lookup_warranty_status`, `create_rma_request`, `check_order_status` |
| **General** | Product info, FAQ, general questions | `search_faq`, `lookup_product_info` |
| **Escalation** | Unresolved issues, angry customers, manager requests | `collect_escalation_details`, `create_escalation_ticket` |

All specialist agents also have a `transfer_to_agent` tool for inter-agent transfers.

### Key Patterns Demonstrated

- **Multi-agent routing** — RoutingAgent classifies and dispatches to specialists
- **Tool-based transfers** — Any agent can transfer to another via a tool call
- **Summary handoff** — `@Memorize`-annotated `summarizeConversation()` provides context to the next agent
- **Context isolation** — Each agent receives only relevant sections of the company knowledge base via `CompanyContextInterceptor`
- **Conversation history** — `ConversationHistoryInterceptor` maintains multi-turn chat per agent
- **Vendor-agnostic** — Models and API modules have no vendor dependency

## Module Structure

```
customer-support/
├── models/     # AI interfaces, data models, interceptors (no vendor deps)
├── api/        # SupportSession, EventListener, ToolProviders (no vendor deps)
├── cli/        # CLI entry point with REPL (OpenAI + Debug)
└── compose/    # Compose Desktop UI (all vendors)
```

## Running

### CLI

```bash
# Set your API key
export OPENAI_API_KEY=sk-...

# Run the CLI
./gradlew :samples-customer-support:cli:run --console=plain
```

### Compose Desktop

```bash
# Set at least one API key
export OPENAI_API_KEY=sk-...
# Optional:
export ANTHROPIC_API_KEY=sk-ant-...
export GEMINI_API_KEY=...

# Run the Compose Desktop app
./gradlew :samples-customer-support:compose:run
```

## Company Template

The agents use `company_template.json` as their knowledge base — a WidgetCo support document containing product overview, troubleshooting guides, FAQ, warranty/returns policy, technical specs, and support process documentation.
