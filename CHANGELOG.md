# Changelog

## 0.5.0

- Refactor codebase to multi-module project. Add the concept of Ragu plugins.
- Added gradle plugin for developing Ragu plugins
- Implemented test framework that can be used by plugins.

## 0.4.0

- Restructure codebase to prepare for plugin based refactor

## 0.3.0

- Refactor authorization to use JWKs and an auth server
- Improve codebase abstractions and make them easier to use
- Add experimental specialist agents
- Add support for images in message payloads
- Updated structure of incoming messages and make them opaque to session manager
- General app bug fixes and dependency updates
- Improved settings API
- Add VLLM support and fix Azure LLM and embedding provider
