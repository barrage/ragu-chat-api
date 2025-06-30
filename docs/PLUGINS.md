# Ragu API plugins

The Ragu chat API (further referred to as Ragu) consists of a core library and a server runtime. The server runtime is
responsible for registering and hosting plugins. The default chat plugin can be used as a reference to see how plugins
can be implemented.

## Table of contents

- [Creating a plugin](#creating-a-plugin)
- [Testing plugins](#testing-plugins)
- [Ragu core](#ragu-core)

## Creating a plugin

Ragu plugins are Kotlin libraries that extend Ragu with additional functionality.

A Ragu plugin is defined by 2 mandatory components;

- a class that implements the `net.barrage.llmao.core.Plugin` interface
- an arbitrarily named Kotlin file (usually `Module.kt`) serving as a Ktor module that registers the plugin to the
  `Plugins` registry.

The `Plugin` implementation will define how it behaves in the context of Ragu, you can read more about how to implement
it by reading the `Plugin` interface documentation.

The Ktor module function should be a file-level function that takes an `Application` instance as a parameter and calls
`Plugins.register` with an instance of the `Plugin` implementation.

For most plugins, the following will suffice

```
// Module.kt
package my.plugin.namespace

import io.ktor.server.application.Application
import net.barrage.llmao.core.Plugins

fun Application.plugin() = Plugins.register(YourPlugin())
```

By defining the plugin as a Ktor module, the main server binary's Ktor plugin will find it and load it automatically.

For example, the configuration

```
# application.conf
ktor {
  application {
    modules = [
      "my.plugin.namespace.ModuleKt.plugin",
      "net.barrage.llmao.app.ApplicationKt.module",
    ]
  }
}
```

loads the plugin module and registers the plugin to the `Plugins` object.

## Testing plugins

Ragu plugins can be tested in isolation by using the `IntegrationTest` class found in the `net.barrage.llmao.test`
module.

By themselves, plugins are not executable; Plugins only _run_ when they are loaded into the main application and picked
up by the `Plugins` registry. This means that when we are testing plugins, we need to load them into a test application
context. This is done by the `IntegrationTest` class.

Good tests don't mock application code - which is why Ragu's test suite is designed
around [testcontainers](https://testcontainers.com/) and [wiremock](https://wiremock.org/).
These testing powerhouses provide us with everything we need to run meaningful tests - meaningful here referring to
testing the actual code that runs in production.

When developing a test suite, all it takes is to extend the `IntegrationTest` class and set the `plugin`
property with an instance of your plugin. `IntegrationTest` is configurable with various flags that can spin
up containers that are needed for testing.

When inheriting from `IntegrationTest`, you can use the `test` function to run tests. This function
sets up the test application and provides you with a `Ktor` client that you can use to interact with the
test server.

You can also use the `wsTest` function to test Websocket endpoints and interactions.

The chat plugin provides many examples of how to test plugins, including REST and Websockets.

## Ragu core

You can think of Ragu's core as being an extension of Ktor with additional abstractions for LLM interaction and
book keeping (token usage, message groups, etc.). Here we will go through the most important entities in the core.

- [Infrastructure](#infrastructure)
    - [User](#user)
    - [Database](#database)
    - [BLOBs](#blobs)
    - [Vector database provider](#vector-database-provider)
    - [Inference provider](#inference-provider)
    - [Embedding provider](#embedding-provider)
    - [Settings](#settings)
- [Agents](#agents)
    - [Message group](#message-group)
    - [Workflow agent](#workflow-agent)
    - [Context enrichment](#context-enrichment)
    - [Token tracking](#token-tracking)
    - [Workflow](#workflow)

### Infrastructure

#### User

The main entity which interacts with anything in Ragu. Users are obtained solely from JWTs, meaning this API does not
handle user management. Instead, it delegates authorization to an external authorization server (using Ktor auth). This
can be configured in the `application.conf` file.

Currently it recognizes 2 roles (entitlements): `admin` and `user`. How plugins use these roles is up to them.
The chat plugin, for example, uses `admin` to allow administrators to manage agents and `user` to allow users to chat
with agents.

Regarding Ktor:

- current request users can be obtained via the `ApplicationCall.user()` extension function.
- routes can be wrapped in `authenticate("user")` or `authenticate("admin")` to restrict access to them only with a
  valid JWT and the appropriate role (entitlement).

Entitlements can also be used to restrict access to certain parts of the infrastructure, such as vector collections.

#### Database

---

*If your plugin does not interact with the database, then this section can be skipped and the `ragu-plugin` gradle
plugin can be omitted.*

---

Ragu uses Postgres and [Jooq](https://www.jooq.org/) with coroutines as its main data source. This cannot be changed.
The main interface for interacting with the DB is Jooq's `DSLContext` and a reference to it can be obtained via
`ApplicationState`.

Jooq classes are automatically generated based on the database schema present when calling `generateJooq`, and the regex
you provide Jooq with. The `generateJooq` task is provided via the `jooq` gradle plugin. In order to streamline this
process and reduce a ton of boilerplate, plugin developers can use the `ragu-plugin` gradle plugin. This plugin provides
the tasks needed set up the necessary database components (table classes, migrations). You can read more about it in
its [README](../ragu-plugin/README.md).

---

**For ease of use, plugins should always be shipped with the generated Jooq classes checked into version control.**

---

Other than plugin migrations, the `core` module comes with a set of migrations that set up the tables it needs to
function. Core migrations are always executed, regardless of plugin configuration.

#### BLOBs

BLOB storage is used to store user message attachments.

A single BLOB storage provider is used per app instance and interfacing with it is done via the `BlobStorage` interface.
Currently the only implemented blob storage is for images, and the only concrete implementation is Minio.

#### Vector database provider

Vector databases are used to store embeddings and associated content, they are dynamic in the runtime. Which provider
to use is determined by plugins. Interfacing with vector databases is done via the `VectorDatabase` interface.

For example, the chat plugin allows users to associate collections with agents. This is done by providing the collection
name and the collection's provider. This unique combination will determine which vector database will be used during
context enrichment in runtime.

Every provider that is intended to be used must be configured in the `application.conf` file.
Currently the only implemented vector database is Weaviate.

#### Inference provider

Inference providers are used to interact with LLMs. They are configured in the `application.conf` file.
Interacting with LLMs is done via the `InferenceProvider` interface, although better abstractions for interacting with
these are provided by `WorkflowAgent`.

The currently supported providers are Azure, OpenAI, and VLLM.

#### Embedding provider

Embedding providers are used to embed text into vectors and are used during context enrichment. They are configured in
the `application.conf` file. Interacting with embedding providers is done via the `Embedder` interface - the interface
used in `RagContextEnrichment`.

#### Settings

A key value storage that can be used to store plugin settings. The core module provides routes for managing the KV
storage. Namely `GET /admin/settings` and `PUT /admin/settings`.

### Agents

#### Message group

Message groups are complete interactions between a user and an LLM and typically store the user's message, the
assistant's response, and any tool calls that were made in between. They are the main entity that is queried
when loading messages because they bundle complete interactions, since any amount of tool calls can occur in between
a user and assistant message.

User messages can contain attachments. Attachments are stored in BLOB storage and are linked to the message
they belong to.

Keep in mind interacting with LLMs does not necessarily mean chatting with it;
An interaction with an LLM can occur in any action made by the user that initiated inference.

*Messages* are encapsulated by message groups. Each message has a `role`, indicating to the LLM how it should process
it. It is guaranteed that message groups stored in the database will have a `user` message as the first one and the
`assistant` message as the last one. Any message in a message group between these must either be an `assistant` message
that is calling tools, or a `tool` message responding to the tool call.

*Message group evaluations* are evaluations of the assistant's response. Evaluation is done on complete message groups
in order to preserve the context of the inquiry. They can give you insight on how well people are reacting to an agent's
responses.

*Message attachments* are one or more instances of binary large objects (BLOB) sent along with messages.
The types of BLOBs that can be processed is the determined by the downstream LLM as not all LLMs support them.

#### Workflow agent

A workflow agent is the main abstraction for interacting with LLMs. It provides various methods for completion and
streaming. It handles token usage tracking by default. It can also manage chat histories, context enrichment and tool
calls.

The chat plugin contains examples on how to construct these with everything mentioned above.

#### Context enrichment

Context enrichment is performed on the user message at time of inference. The `ContextEnrichment` interface provides
a single method `enrich` which take an input and produces the enriched output. The formatting of the output is up to
the implementation.

Currently the only implemented context enrichment is RAG, which is performed by `RagContextEnrichment`.

#### Token tracking

A `TokenTracker` keeps track of token usage whenever inference happens via `WorkflowAgent`. It can be initialized
via `TokenUsageTrackerFactory.newTracker`. If not using `WorkflowAgent`, you can use the token tracker directly to
write usage to storage.

#### Workflow

Workflows are the main form of interaction between a user and an agent. They are introduced to Ragu via plugins.

For real time workflows, implement `WorkflowRealTime`. This manages the workflow coroutines and makes its handler
cancellable.
