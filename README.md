# Ragu Kotlin API (Kappi)

## Table of contents

- [Overview](#overview)
- [Authorization](#authorization)
- [Setup](#setup)
    - [Migrations and seeders](#migrations-and-seeders)
    - [Building and running](#building-and-running)
- [WebSocket](#websocket)
- [Specialists](#specialists)
- [WhatsApp](#whatsapp)
    - [Configuration](#configuration)
    - [Infobip setup](#infobip-setup)

## Overview

Kappi is a plugin based application framework for quickly integrating with various AI models.
It is built on top of Ktor.

Kappi consists of two main components; 
- Core
  The library that provides a set of abstractions for LLM interaction and agent creation.
- Server
  The runtime that serves as a host for plugins and is responsible for their execution.

Kappi plugins are implementations of arbitrary features that are designed to run in the server runtime.
They can range from small applets to full blown applications.

## Core concepts

### Providers

Providers allow us to define business logic without worrying about their implementation details.
The idea behind this is to make switching providers very flexible when creating agents.

A *large language model* (LLM) is a neural net designed to generate text.

An *inference provider* is a service used for LLM inference.

An *embedding model* is a neural net designed to transform text into arrays of floating point numbers or ***vectors***,
also known as ***embeddings***.

An *embedding provider* is a service that provides access to *embedding models*.

A *vector collection* stores these vectors and associates the original content they represent with them. Every vector
collection can only hold vectors of the same size, which is determined upon its creation.

A *vector provider* is a services that provides access to *vector collections* in a *vector database*.

A *blob storage* is a service that provides access to binary large objects (BLOBs). Currently, only image storage is
supported and only one image storage can be used per Kappi instance.

## Workflows

*Workflows* are the main form of interaction between a *user* and an *agent*. They are introduced to Kappi via plugins.

A workflow will usually consist of one or more message groups.

## Message groups

*Message groups* represent a complete interaction between a user and an
assistant. They **always** contains the user's message, the assistant's response,
and any tool calls that were made in between.

*Messages* are encapsulated by message groups. Each message has a `role`, indicating to the LLM how it should process
it. It is guaranteed that message groups stored in the database will have a `user` message as the first one and the
`assistant` message as the last one. Any message in a message group between these must either be an `assistant` message
that is calling tools, or a `tool` message responding to the tool call.

*Message group evaluations* are evaluations of the assistant's response. Evaluation is done on complete message groups
in order to preserve the context of the inquiry. They can give you insight on how well people are reacting to an agent's
responses.

*Message attachments* are one or more instances of binary large objects (BLOB) sent along with messages.
The types of BLOBs that can be processed is the determined by the downstream LLM as not all LLMs support them.

## Authorization

Kappi uses JWT access tokens for authorization. It can be configured to accept tokens from any OAuth 2.0 + OIDC
compatible authorization server.

Specifically, the following fields are used to extract user info:

```json
{
  "sub": "user_id",
  "email": "user_email",
  "given_name": "user_name"
}
```

Kappi's users are grouped into two groups: `admin` and `user`. These groups (also known as entitlements) must be present
in the access token because they are used to reason about a user's permissions. Administrators have full access to the
API, including the "backoffice", whereas users can only access the chat endpoints related to them.

The following is a list of all configurable parameters for JWT authorization:

- `jwt.issuer` - The issuer of the JWT, i.e. the issuer that is expected to be found in the `iss` claim.
- `jwt.jwksEndpoint` - The endpoint where the JWT's public key can be found.
- `jwt.leeway` - The leeway for `nbf`, `iat`, and `exp` claims in seconds. Configurable to make testing easier.
- `jwt.entitlementsClaim` - The claim in the access JWT that holds the user's entitlements (groups).

## Setup

To set up the project run `sh setup.sh` in the root directory.
This will set docker containers, and initial application and gradle properties that you will
need to configure manually.

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

To seed the database with the initial agent, run

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

Kappi uses WebSockets for real-time communication between clients and LLM providers.

To open a WebSocket connection with the server, the client must first create a one-time token by sending a `GET` request
to the `/ws` endpoint and pass it to the WebSocket connection as a `token={token}` query parameter. This token is used
to authorize the client and establish a connection with the server. Otherwise, due to lack of authorization, the
connection will be instantly closed with the reason: `Policy Violation`.

For a full list of session messages and their formats, see [MESSAGES.md](docs/MESSAGES.md).

## Specialists

Specialists are agents that are configured for particular integrations. They are not customizable in the same sense
chat agents are. These agents perform specific workflows with downstream applications by utilising predefined tools (
agent functions). Specialists can be enabled by setting the corresponding feature flag in the `application.conf` file
via the `features.specialists` properties. See [JIRAKIRA.md](docs/JIRAKIRA.md) for an example of a specialist.

## WhatsApp

To use WhatsApp as a chat provider, set the `ktor.features.whatsApp` flag to `true`.
This feature uses Infobip as the downstream service that forwards messages to Kappi.

### Configuration

To make an agent available for WhatsApp, set the `WHATSAPP_AGENT_ID` application setting to the desired agent's ID. This
can be done via the `PUT /admin/settings` endpoint. Only a single agent can be used as the active WhatsApp agent and it
is recommended to use a dedicated agent for this purpose.

Whatsapp is usually accessed from mobile clients, so message size plays an important role as we don't want users to
receive messages that are too long. The maximum message size is limited to 135 tokens by default. This can be configured
via the `WHATSAPP_AGENT_MAX_COMPLETION_TOKENS` application setting.

Example configuration `PUT /admin/settings`:

```json
{
  "updates": [
    {
      "key": "WHATSAPP_AGENT_ID",
      "value": "00000000-0000-0000-0000-000000000000"
    },
    {
      "key": "WHATSAPP_AGENT_MAX_COMPLETION_TOKENS",
      "value": "135"
    }
  ]
}
```

### Infobip setup

Before you can use WhatsApp, you need to create an account on [Infobip](https://www.infobip.com/).

You will need to create an API key, a WhatsApp sender number, a WhatsApp template, and an inbound configuration. The
template will be used to send a welcome message to users, so that the users know the number of the chat provider.

#### Api Key

You can create an API key from the Infobip dashboard by navigating to https://portal.infobip.com/dev/api-keys.
There you will see your API Base URL. You will need to set the `infobip.endpoint` and `infobip.apiKey`
configuration values to the API Base URL and API key respectively.
When creating a new API key, make sure to grant it the General API scopes ("inbound-message:read", "message:send").

#### Sender Number

You can create a new WhatsApp sender number from the Infobip dashboard by navigating to
https://portal.infobip.com/channels-and-numbers/numbers. There you will see your WhatsApp sender number. You will need
to set the `infobip.sender` configuration value to the WhatsApp sender number.
More info can be found [here](https://www.infobip.com/docs/numbers).

#### Inbound configuration

You can configure the inbound configuration from the Infobip dashboard by navigating to your WhatsApp sender number:
https://portal.infobip.com/channels-and-numbers/channels/whatsapp/senders. Click on the 3 dots at the right of your
sender number and select "Edit configuration". Under "Inbound configuration" edit the "Default configuration". Set
Forwarding action to "Forward to HTTP" and set URL to "https://your-kappi-url/chats/whatsapp".

#### Template

You can create a new WhatsApp template from the Infobip dashboard by navigating to
https://portal.infobip.com/channels-and-numbers/channels/whatsapp/templates. There you will see your WhatsApp templates.
You will need to set the `infobip.template` configuration value to the WhatsApp template name.
More info can be found [here](https://www.infobip.com/docs/whatsapp/message-types#template-registration).

## License

This repository contains Kotlin API, a part of Ragu, covered under the [Apache License 2.0](LICENSE), except where
noted (any Ragu logos or trademarks are not covered under the Apache License, and should be explicitly noted by a
LICENSE file.)

Kotlin API, a part of Ragu, is a product produced from this open source software, exclusively by Barrage d.o.o. It is
distributed under our commercial terms.

Others are allowed to make their own distribution of the software, but they cannot use any of the Ragu trademarks, cloud
services, etc.

We explicitly grant permission for you to make a build that includes our trademarks while developing Ragu itself. You
may not publish or share the build, and you may not use that build to run Ragu for any other purpose.

