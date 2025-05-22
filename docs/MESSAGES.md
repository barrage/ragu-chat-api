# Kappi session messages

Table of contents:

- [Session messages](#session-messages)
    - [Incoming](#incoming)
    - [Outgoing](#outgoing)
- [Default workflow input](#default-workflow-input)
    - [Attachments](#attachments)
- [Tools](#tools)
- [Error](#error)

## Session messages

Messages that open and close workflows, and send inputs to them.

### Incoming

#### `workflow.new`

Start a new workflow with the given configuration. The configuration must be provided depending on the type of workflow.
The server will respond with a `workflow.open` message, depending on the type of workflow opened.

`workflowType` is required. Supported workflow types depend on plugins installed.
`params` depends on the `workflowType`. More info can be found in the workflow's documentation.

```json
{
  "type": "workflow.new",
  "workflowType": "CHAT | JIRAKIRA | BONVOYAGE | ...",
  "params": "T?"
}
```

#### `workflow.existing`

Open an existing workflow. The workflow ID must be provided in the payload. The server will respond with a
`workflow.open` message, depending on the type of workflow opened.

```json
{
  "type": "workflow.existing",
  "workflowType": "CHAT | JIRAKIRA | BONVOYAGE | ...",
  "workflowId": "workflow_uuid"
}
```

#### `workflow.close`

Close the current workflow. The server will respond with a `system.workflow.closed` message.

```json
{
  "type": "workflow.close"
}
```

#### `workflow.cancel_stream`

Cancel the current workflow stream. The message handling and response will be workflow specific.

```json
{
  "type": "workflow.cancel_stream"
}
```

#### `workflow.input`

Send input to the currently active workflow. The type `T` depends on the workflow.
This is the only way to send inputs to real time workflows.

```json
{
  "type": "workflow.input",
  "input": "T"
}
```

### Outgoing

#### `workflow.open`

Sent when a workflow is opened manually by the client.

```json
{
  "type": "workflow.open",
  "id": "workflow_uuid"
}
```

#### `workflow.closed`

Sent when a workflow is closed manually by the client.

```json
{
  "type": "workflow.closed",
  "id": "workflow_uuid"
}
```

#### `workflow.stream_chunk`

Sent when a workflow is streaming a response. The response is sent in chunks.

```json
{
  "type": "workflow.stream_chunk",
  "chunk": "chunk_text"
}
```

#### `workflow.stream_complete`

Sent when a workflow has finished streaming a response. If a workflow is not streaming, then the `content` field will
contain the final response.

```json
{
  "type": "workflow.stream_complete",
  "workflowId": "workflow_uuid",
  "reason": "FinishReason",
  "messageGroupId": "message_uuid",
  "attachmentPaths": [
    {
      "type": "ATTACHMENT_TYPE",
      "provider": "minio | url",
      "order": 0,
      "url": "attachment_url"
    }
  ],
  "content": "Response content if not streaming, null otherwise."
}
```

## Default Workflow Input

The default workflow input can be used by workflows that are conversation based, i.e. do not have structured input and
only accept text messages with attachments. Note, if workflows use it directly, then it can be sent as the below
structure. Some workflows may wrap it in a custom payload and might need additional type discriminators.

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

- `text` - The text contents of the message. This is the only required field.
- `attachments` - Optional binary attachments to send with the message. If empty, no attachments will be processed.
    - `type` - The type of attachment. See below section for more information.
    - `data` - Depends on `type`. See below section for more information.

### Attachments

The following `ATTACHMENT_TYPE`s are supported:

- `image` - An image attachment.

The following are data structures associated with attachment types:

#### Image URL (`ATTACHMENT_TYPE == image`)

When attaching public URLs, only the image URL is required.

```json
{
  "type": "url",
  "url": "image_url"
}
```

#### Image raw (`ATTACHMENT_TYPE == image`)

When attaching raw image data, the data should be a base64 data URI.

```json
{
  "type": "raw",
  "data": "data:<MIME_TYPE>;base64,<BASE_64_ENCODED_IMAGE_DATA>"
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
  "displayMessage": "Optional error display message of the agent."
}
```
