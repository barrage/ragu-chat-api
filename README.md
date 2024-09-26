# LLMAO Kotlin API

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

### Setup

For this project you will need to have Docker installed on your machine. To set up the project
run `sh setup.sh` in the root directory. This will set up the project hooks, docker containers,
and initial application and gradle properties that you will need to configure additionally.
Hooks will be set up to run formating and linting checks on `git push` commands, failing to
execute them if application isn't formated correctly.

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
## Migrations and seeders

Flyway is responsible for migrating the database schema.
It is embedded into the application and will run migrations on app startup.

To seed the database with the initial agent and users, run

```bash
psql -h localhost -p 5454 -U postgres -f src/main/resources/db/seed/initial.sql -d kappi 
```
