# LLMAO Kotlin API

## Table of contents

- [Overview](#overview)
    - [Rag](#rag)
    - [Agents](#agents)
- [Authentication](#authentication)
    - [Google](#google)
- [Setup](#setup)
    - [Migrations and seeders](#migrations-and-seeders)
    - [Building and running](#building-and-running)

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

Authentication is performed using OAuth with
3rd party providers. When this application is initially
deployed, it requires a manual entry in the administrator
list (TBD on how we do this). This initial entry specifies the email of the
superadmin, which can then log in via an external provider
and extend the list as they see fit.

In addition to the standard parameters in the OAuth flow (code, grant_type, and redirect_uri),
2 parameters are required:

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

Flyway is responsible for migrating the database schema.
It is embedded into the application and will run migrations on app startup.

To seed the database with the initial agent and users, run

```bash
psql -h localhost -p 5454 -U postgres -f src/main/resources/db/seed/initial.sql -d kappi 
```

### Building and running

To build the application, run

```bash
./gradlew buildFatJar
```

This will create a fat jar in the `build/libs` directory.

To run the application, run

```bash
java -jar build/libs/llmao.jar -config=config/application.yaml
```

You have to provide a configuration file with the application configuration. The configuration file example can be found
in `config/application.example.yaml`.