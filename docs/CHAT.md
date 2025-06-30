# Chat plugin

The Ragu chat plugin provides a chat interface for users to interact with agents.
It allows Ragu administrators to create custom RAG powered LLM agents.

## Configuration

It is the default plugin that all Ragu instances come with by default and cannot be disabled.

It is automatically configured, but can be additionally configured with the following settings:

- `CHAT_MAX_HISTORY_TOKENS`: Maximum number of tokens to keep in chat histories. Used to prevent context windows from
  growing too large. Always applies to all chats.
- `CHAT_AGENT_PRESENCE_PENALTY`: Used to penalize tokens that are already present in the context window. The global
  value applies to all chat agents, unless overridden by their configuration.
- `CHAT_AGENT_TITLE_MAX_COMPLETION_TOKENS`: Maximum number of tokens to generate when creating a chat title.

## Workflow

The chat plugin requires an agent ID to be passed in its `workflow.new` parameters.

```json
{
  "type": "workflow.new",
  "workflowType": "CHAT",
  "params": {
    "agentId": "agent_uuid"
  }
}
```

## Chat agents

An agent can be thought of as the combination of an LLM and its provider, its embedding model and its provider,
and its vector collections. It also holds the context formatting configuration, which dictates how the LLM
will be prompted.

When a chat is opened, all the configuration for it is derived from the agent.

Admin users can create agents, manage their configurations, their vector collections, and assign them avatars.

### Agent collections

Vector collections can be assigned to agents for context enrichment. These are assigned with a collection name and
provider combo, and additional query options.

Collections can have `groups` assigned to them, which are used to determine which users can access the collection.
If no groups are assigned, the collection is available to everyone. If groups are assigned, the user must be in one
of the groups to access the collection.

### Agent tools

Agent tools are functions whose definitions are included in the prompt to the underlying LLM. The LLM can use the
definitions to determine whether to call the function or not. For example, given the user query "What is the weather
like today?", the LLM can decide to call the function "getWeather" to get the weather information. You can read more
about it [here](https://platform.openai.com/docs/guides/gpt/function-calling).

### Predefined tools

Predefined tools are compiled into the application and are available to all agents.
Tools can be assigned to agents as necessary. All predefined tools are defined in
the [ToolRegistry.kt](src/main/kotlin/net/barrage/llmao/core/llm/ToolRegistry.kt).

The registry is responsible for providing tool definitions and implementations to the rest of the application.
Whenever a workflow agent is created, a [Toolchain](src/main/kotlin/net/barrage/llmao/core/llm/Toolchain.kt) is
constructed based on its configuration.

Every tool must be defined as a [JSON schema](https://json-schema.org/understanding-json-schema/).

## Chat messages

### Incoming

Chats use the [default workflow input](./MESSAGES.md#default-workflow-input).

### Outgoing

- `chat.title`

```json
{
  "type": "chat.title",
  "chatId": "chat_uuid",
  "title": "chat_title"
}
```
