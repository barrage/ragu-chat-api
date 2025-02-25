# Kappi Session Messages

## System

Generic messages used to control sessions and broadcast system related events.
These messages indicate events outside workflows and are mainly used to control them.

### Incoming

- `workflow.new`

Start a new workflow with the given configuration. The configuration must be provided depending on the type of workflow.
The server will respond with a `system.workflow.open` message, depending on the type of workflow opened.

`agentId` is optional and is only necessary when instantiating `CHAT` workflows.
`workflowType` is optional and defaults to `CHAT` if not provided.

```json
{
  "type": "system",
  "payload": {
    "type": "workflow.new",
    "agentId": "agent_uuid",
    "workflowType": "CHAT | JIRAKIRA"
  }
}
```

- `workflow.existing`

Open an existing workflow. The workflow ID must be provided in the payload. The server will respond with a
`system.workflow.open` message, depending on the type of workflow opened.

```json
{
  "type": "system",
  "payload": {
    "type": "workflow.existing",
    "workflowId": "workflow_uuid"
  }
}
```

- `workflow.close`

Close the current workflow. The server will respond with a `system.workflow.closed` message.

```json
{
  "type": "system",
  "payload": {
    "type": "workflow.close"
  }
}
```

- `workflow.cancel_stream`

Cancel the current workflow stream. The message handling and response will be workflow specific.

```json
{
  "type": "system",
  "payload": {
    "type": "workflow.cancel_stream"
  }
}
```

### Outgoing

- `system.workflow.open`

Sent when a workflow is opened manually by the client.

```json
{
  "type": "system.workflow.open",
  "id": "workflow_uuid"
}
```

- `system.workflow.closed`

Sent when a workflow is closed manually by the client.

```json
{
  "type": "system.workflow.closed",
  "id": "workflow_uuid"
}
```

#### System Events

System events are sent to all connected clients regardless of whether they are participating in a workflow.

- `system.event.agent_deactivated`

Sent when an administrator deactivates an agent via the service and is used to indicate that any workflow using it
is no longer available.

```json
{
  "type": "system.event.agent_deactivated",
  "agentId": "agent_uuid"
}
```

## Workflow

### Chat

Chats are the most basic of workflows involving a single agent.
Chat workflows do not have a clear indication of an end.

#### Incoming

- `chat`

Send a message to the chat workflow. The workflow must be open for this message to be accepted.

```json
{
  "type": "chat",
  "text": "message_text"
}
```

#### Outgoing

- `chat.stream_chunk`

Sent when a chat workflow is streaming a response. The response is sent in chunks.

```json
{
  "type": "chat.stream_chunk",
  "chunk": "chunk_text"
}
```

- `chat.stream_complete`

Sent when a chat workflow has finished streaming a response. The response is sent in chunks.

```json
{
  "type": "chat.stream_complete",
  "chatId": "chat_uuid",
  "reason": "FinishReason",
  "messageId": "message_uuid"
}
```

- `chat.title`

Sent when a chat workflow has generated a title for the chat.

```json
{
  "type": "chat.title",
  "chatId": "chat_uuid",
  "title": "chat_title"
}
```

### JiraKira

Jira Kira conversations are not streaming.

- `jirakira.response`

Obtained as the final response from the LLM.

```json
{
  "type": "jirakira.response",
  "content": "response_content"
}
```

- `jirakira.worklog_created`

Sent when a worklog entry is created for a Jira issue.
The worklog field is the original Jira worklog entry model.

```json
{
  "type": "jirakira.worklog_created",
  "worklog": "JIRA_WORKLOG_ENTRY_MODEL"
}
```

- `jirakira.worklog_updated`

Sent when a worklog entry is updated for a Jira issue.
The worklog field is the original Jira worklog entry model.

```json
{
  "type": "jirakira.worklog_updated",
  "worklog": "JIRA_WORKLOG_ENTRY_MODEL"
}
```

## Tools

All workflows can emit tool events. These can be used to notify clients of tool calls and results.

- `tool.call`

Sent whenever an agent calls a tool.

```json
{
  "type": "tool.call",
  "data": {
    "id": "tool_call_id",
    "name": "tool_name",
    "arguments": "tool_arguments"
  }
}
```

- `tool.result`

Sent whenever an agent receives a tool result.
The content field will usually be in the format of `tool_name: success`. Errors in tools are not sent back to the
client.

```json
{
  "type": "tool.result",
  "result": {
    "id": "tool_call_id",
    "content": "tool_result_content"
  }
}
```

## Error

- `error`

Catch-all type for emitting errors from arbitrary sources. Any workflow emitting an error will use this format.
It contains the type of error, the reason it occurred, and an optional description.

```json
{
  "errorType": "API | Internal",
  "errorReason": "reason",
  "errorDescription": "description"
}
```
