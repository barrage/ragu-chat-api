# JiraKira

JiraKira is a specialist agent that is used to interact with Jira. It can be used to perform various actions with Jira.
JiraKira can be enabled by setting the `features.specialists.jirakira` property to `true` in the `application.conf`
file.

A few things must be configured in order for JiraKira to work.

## Users

Users must register their Jira API keys in Kappi. These can be generated on the `Profile` page of Jira. On the side will
be a tab called Personal Access Tokens. The token can then be set via the `POST /jirakira/key` endpoint and can also be
deleted via the `DELETE /jirakira/key` endpoint.

## Administrators

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
