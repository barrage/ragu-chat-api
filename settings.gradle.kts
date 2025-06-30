rootProject.name = "llmao"

include(
  ":core",
  ":adapters",
  ":app",
  ":plugins:bonvoyage",
  ":plugins:chat",
  ":plugins:jirakira",
  ":plugins:hgk",
  ":test",
)

includeBuild("ragu-plugin")
