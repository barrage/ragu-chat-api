# Chat plugin

The Ragu chat plugin provides a chat interface for users to interact with agents.
It allows Ragu administrators to create custom RAG powered LLM agents.

It is the default plugin that all Kappi instances come with by default and cannot be disabled.

## Agents

An agent can be thought of as the combination of an LLM and its provider, its embedding model and its provider,
and its vector collections. It also holds the context formatting configuration, which dictates how the LLM
will be prompted.

When a chat is opened, all the configuration for it is derived from the agent.

Admin users can create agents, manage their configurations, their vector collections, and assign them avatars.

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