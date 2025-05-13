# Chat plugin

The Ragu chat plugin provides a chat interface for users to interact with agents.
It allows Ragu administrators to create custom RAG powered LLM agents.

It is the default plugin that all Kappi instances come with by default and cannot be disabled.

## Agents

An agent can be thought of as the combination of an LLM and its provider, its embedding model and its provider,
and its vector collections. It also holds the context formatting configuration, which dictates how the LLM
will be prompted.

A *large language model* (LLM) is a neural net designed to gene[SessionManager.kt](../src/main/kotlin/net/barrage/llmao/core/workflow/SessionManager.kt)rate text.

An *LLM provider* is a service used for LLM inference.

An *embedding model* is a neural net designed to transform text into arrays of floating point numbers or ***vectors***,
also known as ***embeddings***.

An *embedding provider* is a service that provides access to *embedding models*.

A *vector collection* stores these vectors and associates the original content they represent with them. Every vector
collection can only hold vectors of the same size, which is determined upon its creation.

A *vector provider* is a services that provides access to a *vector database*.

When a chat is opened, all the configuration for it is derived from the agent.

Admin users can create agents, manage their configurations, their vector collections, and assign them avatars.

## Input

The chat plugin only supports the [DefaultWorkflowInput](./MESSAGES.md#default-workflow-input) message.