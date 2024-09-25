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