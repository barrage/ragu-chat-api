# LLMAO Kotlin API

## Table of contents

- [Overview](#overview)
    - [Rag](#rag)
    - [Agents](#agents)
- [Authentication](#authentication)
    - [Google](#google)
    - [Chonkit](#chonkit-authorization)
- [Setup](#setup)
    - [Migrations and seeders](#migrations-and-seeders)
    - [Building and running](#building-and-running)
- [WebSocket](#websocket)
    - [Client messages](#client-messages)
        - [System messages](#system-messages)
        - [Chat messages](#chat-messages)
    - [Server responses](#server-responses)
    - [WS Diagrams](#ws-diagrams)
        - [Chatting](#chatting)
        - [Changing chats](#changing-chats)

## Overview

Kappi is an application used to create chat assistants, or `agents` for short.
Together with [chonkit](https://git.barrage.net/llmao/chonkit), it is a fully featured application
for creating RAG(Retrieval augmented generation) pipelines.

### Rag

Powerful as they are, LLM(Large language model)s are limited by the information they receive in the training data.
Fine-tuning is one technique where one can adjust the weights and biases of the neural net of an LLM by passing
custom data to them to make them more fit for the task in hand. The problem with fine-tuning is that these models
are very large and have been trained on enormous data sets, so one would theoretically need a lot of fine-tuning data
just to barely scratch the surface of an LLM's "behaviour".

By using RAG, we can provide LLMs the additional information they need to correctly respond to prompts with as
little hallucinations as possible.

### Agents

An agent can be thought of as the combination of an LLM and its provider, its embedding model and its provider,
and its vector collections. It also holds the context formatting configuration, which dictates how the LLM
will be prompted.

An *LLM* is a neural net designed to generate text.
An *LLM provider* is a service used for LLM inference.

An *embedding model* is a neural net designed to transform text into arrays of floating point numbers or ***vectors***,
also known as ***embeddings***.
An *embedding provider* is a service used for embedding text.

A *vector collection* stores these vectors and associates the original content they represent with them. Every vector
collection can only hold vectors of the same size, which is determined upon its creation. This is why it's important
to use the same embedding model that's associated with the collection. Agents can only "use" collections which are
compatible with their configured embedding model.

When a chat is opened, all the configuration for it is derived from the agent.

## Authentication

Application authentication is performed using OAuth with third-party providers,
incorporating [Proof Key for Code Exchange (PKCE)](https://datatracker.ietf.org/doc/html/rfc7636) to enhance security
against man-in-the-middle attacks.

When this application is initially deployed, it requires a manual entry in the administrator list (TBD on how we do
this). This initial entry specifies the email of the superadmin, which can then log in via an external provider and
extend the list as they see fit.

In addition to the standard parameters in the OAuth flow (code, grant_type, and redirect_uri), 2 parameters are
required:

- `provider` - which provider to use for the OAuth flow.
- `source` - the client identifier, i.e. where the authentication attempt is coming from.
  This can be one of `web`, `ios`, or `android`.

### Google

When requesting an authorization code from Google,
the following scopes are required:

```
https://www.googleapis.com/auth/userinfo.profile
https://www.googleapis.com/auth/userinfo.email
openid
```

The postman collection contains examples of the URLs used for the initial
OAuth flow start.

### Chonkit Authorization

Kappi serves as an authorization server for Chonkit.
Both Chonkit and Kappi use
the [Vault transit engine](https://developer.hashicorp.com/vault/docs/secrets/transit) to verify and sign tokens,
respectively.

Chonkit access tokens are generated in Kappi. Web clients should rely on the browser mechanisms to forward
the token to Chonkit, since they will be deployed on the same domain. The following steps outline the
general flow:

1. User logs in to Kappi.
2. The web client sends a request to the Kappi `/auth/chonkit/token` endpoint. This step is optional as
   web clients will automatically get an access token (in the body AND cookie) on the `/auth/login` route.
3. An access and refresh token are generated and returned to the client.
4. The web client stores the access token in an HTTP only, secure cookie and forwards it to Chonkit.
5. Chonkit verifies the token signature and, if valid, allows the request.

It is the responsibility of the web client to refresh the access token when it expires by sending a request to
the `/auth/chonkit/refresh` endpoint.

## Setup

To set up the project run `sh setup.sh` in the root directory.
This will set up git hooks, docker containers, and initial application and gradle properties that you will
need to configure manually (ask your local teammate).
Hooks will be set up to run formating and linting checks on `git push` commands,
aborting the push if any formatting errors are found.

If you work in IntelliJ IDEA, you can use the following steps to additionally set up the project:

- If you don't have the `ktfmt` plugin installed, install it, and go to `Settings -> Editor -> ktfmt Settings`,
  enabling it, and set it to `Code style: Google (internal)`, this will force Google code styling on code reformat.
- Go to `Settings -> Editor -> Code Style -> Kotlin` and set the following:
    - Set the `Tab Size` to 2
    - Set the `Indent` to 2
    - Set the `Continuation Indent` to 2
- Setup automatic formating and linting on saving by going to `Settings -> Tools -> Actions on Save`
  and turning on `Reformat Code` and `Optimize Imports` on any save action.

This will ensure that your code is always formatted correctly, and has optimized imports.

### Migrations and seeders

Liquibase is responsible for migrating the database schema.
It is embedded into the application and will run migrations on app startup.

Migrations are located in the `src/main/resources/db/migrations` directory.
Every migration folder should have a name in the format `{migration_number}_{migration_description}`, with `up.sql` and
`down.sql` files that will handle migration and migration rollback.
When creating new migrations to add to the database, make sure to insert incremented version tag in the `changelog.yaml`
in `src/main/resources/db`.

Updated `changelog.yaml` should look like this:

```yaml
- changeSet:
  id: { migration_number }
  author: barrage
  changes:
    - tagDatabase:
        tag: { migration_tag }
    - sqlFile:
        encoding: utf8
        path: migration/{ migration_number }_{ migration_name }/up.sql
        relativeToChangelogFile: true
        splitStatements: false
  rollback:
    - sqlFile:
        encoding: utf8
        path: migration/{ migration_number }_{ migration_name }/down.sql
        relativeToChangelogFile: true
        splitStatements: false
```

To rollback migrations to the tag, run the following command:

```bash
./gradlew liquibaseRollback -PliquibaseCommandValue={version_tag}
```

To seed the database with the initial agent and users, run

```bash
psql postgresql://postgres:postgres@localhost:5454/kappi -f src/main/resources/db/seed/initial.sql 
```

### Building and running

To build the application, run

```bash
./gradlew buildFatJar
```

This will create a fat jar in the `build/libs` directory.

To run the application, run

```bash
java -jar build/libs/llmao.jar -config=config/application.conf
```

You have to provide a configuration file with the application configuration. The configuration file example can be found
in `config/application.example.conf`.

## WebSocket

This application uses WebSocket for real-time communication between the client and GPT providers.

To open a WebSocket connection with the server, the client must first create a one-time token by sending a `GET` request
to the `/ws` endpoint and pass it to the WebSocket connection as a `token={token}` query parameter. This token is used
to authorize the client and establish a connection with the server. Otherwise, due to lack of authorization, the
connection will be instantly closed with the reason: `Policy Violation`.

### Client messages

WebSocket server expects two types of messages from client: `system` and `chat`.

#### System messages

System messages are used to send system commands to the server. The following system messages are supported:

- `chat_open_new`

Opens a new chat with the specified agent. The agent UUID must be provided in the payload.
Server will always respond with a `chat_open` message of newly generated chat.

```json
{
  "type": "system",
  "payload": {
    "type": "chat_open_new",
    "agentId": "agent_uuid"
  }
}
```

- `chat_open_existing`

Opens an existing chat with the specified chat UUID. If no chat is found with the provided UUID, the server will respond
by throwing an error. If the chat is found, the server will respond with a `chat_open` message, but if user is already
found to have an opened chat, the server will respond with a `chat_open` message for that existing chat.

```json
{
  "type": "system",
  "payload": {
    "type": "chat_open_existing",
    "chatId": "chat_uuid"
  }
}
```

- `chat_close`

Closes users opened chat. This message removes chat assigned to the user and stops the streaming of the response
generated by the agent. It should be called every time the user closes the chat window or wants to change the active
chat.

```json
{
  "type": "system",
  "payload": {
    "type": "chat_close"
  }
}
```

- `chat_stop_stream`

Stops the streaming of the response generated by the agent.

```json
{
  "type": "system",
  "payload": {
    "type": "chat_stop_stream"
  }
}
```

#### Chat messages

After sending a system message to open a chat, the client is allowed to send chat messages to the server for them to be
answered by the assigned agent. Chat messages are sent in the following format:

```json
{
  "type": "chat",
  "text": "message_text"
}
```

### Server responses

After receiving a message from the client, the server will respond with a message of the following types:

- `chat_open`

This message is sent after the client sends a `open_chat_new` or `open_chat_existing` system message. It contains the id
of the opened chat. For `open_chat_new`, the chat id is generated by the server, and for `open_chat_existing`, the chat
id is the one provided by the client, should it exist.

```json
{
  "type": "chat_open",
  "chatId": "chat_uuid"
}
```

- `chat_title`

This message is sent after the client sends a chat message to the server after creating a new chat. It contains id and
the title of the chat.

```json
{
  "type": "chat_title",
  "chatId": "chat_uuid",
  "title": "chat_title"
}
```

- `chat_closed`

This message is sent after the client sends a `chat_close` system message. It indicates that the chat has been closed.

```json
{
  "type": "chat_closed"
}
```

- `finish_event`

This message is sent after processing the last message in the chat. It indicates that the chat response generation has
been finished. It contains chat id, message id, reason of the response generation finish, and optionally if the response
to the client is not done by streaming it,<!-- read: never --> its content is sent.

```json
{
  "type": "finish_event",
  "chatId": "chat_uuid",
  "messageId": "message_uuid",
  "reason": "reason",
  "content": "content_or_null"
}
```

- `error`

This message is sent when an error occurs. It contains the type of error, api or internal, its reason, and optional
description.

```json
{
  "type": "error",
  "reason": "reason",
  "description": "description"
}
```

### WS Diagrams

#### Chatting

Sequence diagram explaining the process of chatting with the server.

<img title="New Chat" src="md/new_chat_sequence.svg" alt="New Chat" />

#### Changing chats

Sequence diagram explaining the process of changing chats.

<img title="Changing chats" src="md/changing_chats_sequence.svg" alt="Changing chats">

State diagram explaining assignment of users current chat.

<img title="Changing chats" src="md/changing_chats_state.svg" alt="Changing chats">
