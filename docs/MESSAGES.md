# Kappi Session Messages

Table of contents:

- [System](#system)
    - [Incoming](#incoming)
    - [Outgoing](#outgoing)
        - [System Events](#system-events)
- [Workflow](#workflow)
    - [Chat](#chat)
        - [Incoming](#incoming-1)
        - [Outgoing](#outgoing-1)
    - [JiraKira](#jirakira)
- [Tools](#tools)
- [Error](#error)

## System

Generic messages used to control sessions and broadcast system related events.
These messages indicate events outside workflows and are mainly used to control them.

### Incoming

- `workflow.new`

Start a new workflow with the given configuration. The configuration must be provided depending on the type of workflow.
The server will respond with a `system.workflow.open` message, depending on the type of workflow opened.

`workflowType` is required. If given and not `CHAT`, the agentId has to be a specialist ID.
`agentId` is optional and is only necessary when instantiating `CHAT` workflows.

```json
{
  "type": "workflow.new",
  "agentId": "agent_uuid",
  "workflowType": "CHAT | JIRAKIRA"
}
```

- `workflow.existing`

Open an existing workflow. The workflow ID must be provided in the payload. The server will respond with a
`system.workflow.open` message, depending on the type of workflow opened.

```json
{
  "type": "workflow.existing",
  "workflowType": "CHAT | JIRAKIRA",
  "workflowId": "workflow_uuid"
}
```

- `workflow.close`

Close the current workflow. The server will respond with a `system.workflow.closed` message.

```json
{
  "type": "workflow.close"
}
```

- `workflow.cancel_stream`

Cancel the current workflow stream. The message handling and response will be workflow specific.

```json
{
  "type": "workflow.cancel_stream"
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

Send a message to a chat workflow. The workflow must be open for this message to be accepted.

- `text` - The text contents of the message. This is the only required field.
- `attachments` - Optional binary attachments to send with the message. If empty, no attachments will be processed.
    - `type` - The type of attachment. See below section for more information.
    - `data` - Depends on `type`. See below section for more information.

```json
{
  "text": "message_text",
  "attachments": [
    {
      "type": "ATTACHMENT_TYPE",
      "data": "T"
    }
  ]
}
```

##### Attachments

The following `ATTACHMENT_TYPE`s are supported:

- `image` - An image attachment.

The following are data structures associated with attachment types:

###### Image URL (`ATTACHMENT_TYPE == image`)

When attaching public URLs, only the image URL is required.

```json
{
  "type": "url",
  "url": "image_url"
}
```

###### Image raw (`ATTACHMENT_TYPE == image`)

When attaching raw image data, the data should be a base64 data URI.

```json
{
  "type": "raw",
  "data": "data:image/<IMAGE_TYPE>;base64,<BASE_64_ENCODED_IMAGE_DATA>"
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

#### Incoming

Jira Kira incoming messages are the same as [Chat](#chat), without the attachments.

#### Outgoing

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
The content field will usually contain the output of the tool.

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
  "errorDescription": "description",
  "displayMessage": "Optional error display message (instruction) of the agent."
}
```
