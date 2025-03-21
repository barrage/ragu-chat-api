# LLMAO Kotlin API

## Table of contents

- [Overview](#overview)
    - [Rag](#rag)
    - [Agents](#agents)
    - [Chat messages evaluation](#chat-messages-evaluation)
- [Authentication](#authentication)
    - [Google](#google)
    - [Chonkit](#chonkit-authorization)
- [User Management](#user-management)
    - [CSV format](#csv-format)
- [Setup](#setup)
    - [Migrations and seeders](#migrations-and-seeders)
    - [Building and running](#building-and-running)
- [WebSocket](#websocket)
- [Specialists](#specialists)
- [WhatsApp](#whatsapp)
    - [Configuration](#configuration)
    - [Infobip setup](#infobip-setup)
- [Agent tools](#agent-tools)
    - [Defining custom tools](#defining-custom-tools)

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

Admin users can create agents, manage their configurations, their vector collections, and assign them avatars.

### Chat messages evaluation

In order to improve the quality of the agent's responses, users can evaluate messages generated by the agent. *Like* and
*dislike* are the two options available for evaluation and are represented as `true` and `false` respectively. Users can
also provide feedback for the evaluation.

The evaluations and feedbacks are visible to the admin on the agent's page. The admin can then change the agent's
configuration to improve the quality of the agent's responses.

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
via the `features.specialists` properties.

### JiraKira

JiraKira is a specialist agent that is used to interact with Jira. It can be used to perform various actions with Jira.
JiraKira can be enabled by setting the `features.specialists.jirakira` property to `true` in the `application.conf`
file.

A few things must be configured in order for JiraKira to work.

#### Administrators

The Jira endpoint _must_ be configured in the `application.conf` file. The endpoint can be set with the
`jirakira.endpoint` property.

An administrator _should_ configure the default billing account **key** for issues if the Jira instance has billing
accounts configured and worklogs should have billing accounts associated with them.

The billing account key is the key of a custom attribute sent in worklog creation requests that specifies the customer
time slot account. The key can be configured via the `PUT /admin/settings` endpoint and setting the
`JIRA_TIME_SLOT_ATTRIBUTE_KEY`. Only the key is required, the value is obtained from the Jira API based on the issue's
available billing accounts.

Example request body for `PUT /admin/settings`:

```json
{
  "updates": [
    {
      "key": "JIRA_TIME_SLOT_ATTRIBUTE_KEY",
      "value": "_Customer_"
    }
  ]
}
```

Worklogs can contain additional custom attributes. In order to set those attributes in API calls we must know what
attributes are available and what values they can take. Since the LLM behind JiraKira is the one that ultimately calls
the API, it must be made aware of the attributes and what values they can take.

JiraKira obtains a list of custom attributes from the Jira API every time it is instantiated. This list will
contain _all_ custom attributes worklogs can have. These attributes are then filtered based on the attributes that are
present in the database. Only the attributes that are present in the database will be included in the tool definitions.

JiraKira only accepts custom attributes that are of the "static list" type. These attributes have a predefined list of
values that can be used, essentially making them enums which can be passed as function arguments to the LLM.

For example, a worklog can have a custom attribute called "WorkCategory". The attribute is a static list type attribute
that can have the values "A", "B", and "C". If you want to include this attribute in the tool definitions, you must add
it to the database as a Jira worklog attribute. This can be done via the `POST /admin/jirakira/attributes` endpoint.

Every attribute in the database must have its `key`, which is the attribute identifier, and its `description` that will
be used in the tool definition. Attributes can also be marked as `required` which will make them required in the tool
definition.

In the above example, it should look something like this:

```json
{
  "key": "WorkCategory",
  "description": "The category of work done on the issue. Assume the category to be A, unless the user's instruction says otherwise.",
  "required": true
}
```

Descriptions should convey what the attribute is and what it is used for and they should be as descriptive
as possible to cause less confusion for the LLM.

When attribute keys are matched with the keys from the database, the attribute will be included in the JSON schema
describing the tool. The LLM will then be able to call the tool and pass the attribute value as an argument when
creating worklog entries.

#### Users

Users must register their Jira API keys in Kappi. These can be generated on the `Profile` page of Jira. On the side will
be a tab called Personal Access Tokens. The token can then be set via the `POST /jirakira/key` endpoint and can also be
deleted via the `DELETE /jirakira/key` endpoint.

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

## Agent tools

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

### Defining custom tools (TODO)

Most of the flexibility comes from defining custom tools. Custom tools are defined in python.
These tools are defined globally and are also assigned to agents as necessary.

Each custom tool can contain an `imports`, `environment` and `functions` section.

The `imports` section contains a list of python modules to import. The imports are available to all functions.
The `environment` section contains a list of environment variables to set in the python process.
The `functions` section contains a list of function definitions to use as tools. Each function must also
have its corresponding JSON schema. LLMs should be used to generate the JSON schema on client apps to speed
this process up and this API exposes a route to do so.

Each of these sections will ultimately end up in a single python script. A `main` function will be appended to the end
of the script and a separate process will be used run the script. The script will also contain plumbing for unix sockets
to communicate with the main process.

Since the python process will run as a child process of the main process, it can access the same environment
variables and files. The script can import any python libraries, so long as they are installed in the container.

### Python execution

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

