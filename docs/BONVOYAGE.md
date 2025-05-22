# Bonvoyage Ragu Plugin

The Bonvoyage plugin is a business trip management solution for streamlining the creation
of travel orders and travel expense reports.

## Configuration

The plugin mandates the existence of the `email` and `given_name` fields in the access token.
The `email` field is used to send travel reports to users and the `given_name` field is used to address users
in official documents (travel orders and reports).

The following entry needs to exist in the `application.conf` file.

```conf
bonvoyage = {
    # Email address from which travel reports and notifications are sent
    emailSender = "my@email.sender"
    # Path to the logo used in travel reports
    logoPath = "path-to-logo.png"
    # Path to the font used in travel reports
    fontPath = "path-to-font.ttf"
}
```

The following application settings must be set:

- `BONVOYAGE_LLM_PROVIDER` - the provider used for all agents
- `BONVOYAGE_MODEL` - the model used for all agents, must be compatible with the provider

## Workflow

A Bonvoyage workflow is created from an approved travel request, therefore it cannot be created with `workflow.new`,
only with `workflow.existing` by providing the trip ID as the `workflowId`.

## Entities

Bonvoyage consists of the following entities:

### **Travel managers**

A subset of Ragu users who can approve *travel order requests*. Created by administrators.
Any travel manager can accept or reject any travel request.

### **Traveler to travel manager mappings**

Notification mappings that represent which manager should be notified of travel requests from a specific user.
There can be multiple mappings for a single user-manager pair, e.g. one for email and one for push notifications.

### **Travel requests**

Issued by **Bonvoyage users** to **travel managers**. They are the first step in the Bonvoyage flow and represent
a user's intent to register a business trip. The management of travel orders is usually enforced by law in some
way which is why it is important to not let users create travel orders on their own, since mistakes can be costly.

### **Trips**

A Bonvoyage workflow - termed trip - is the main stage of the flow. It is an approved *travel request* for which
a corresponding *travel order* has been created. This is the main entity with which *Bonvoyage users* interact in the
system. Through this entity users can upload expenses and generate trip reports.

When a travel request is approved, a trip is created. Multiple trips can exist for a single user at any given time.

### **Travel expenses**

Represent any expenses made on *trips* that are eligible for reimbursement. Expense receipt images are uploaded by
*Bonvoyage users* and are processed by multi-modal LLMs to create database entries.
Since this is mostly an automatic process, expenses must be verified by users who uploaded them and their
database entries can be updated if necessary.

### **Traveler notifications**

Traveler notifications are sent to users at various points in the trip lifecycle. They are used to remind users
of upcoming trips, to notify them when their trip has started, and to notify them when their trip has ended.

Note, travel orders are not a part of this plugin and are handled externally by a 3rd party. The only connection is
the travel order ID which is used to link the *trip* to the external system.

## Flow overview

1. A *Bonvoyage user* issues a *travel request* to a *travel manager*.
   Multiple travel requests can exist for the same user at the same time.
2. The *travel manager* is notified of the request and either:
    - approves the request, in which case a *trip* is created upon
      successfully creating a travel order.
    - rejects the request, in which case the flow ends here.
3. The traveler is notified of the approved request and the newly created *trip*.
4. The traveler edits the start and end times of the trip and uploads receipts of expenses made on the trip.
5. The traveler verifies, i.e. makes sure all the expenses made on the trip have the correct amount, currency,
   and description. They then generate the trip report which gets delivered to them via email, which they can then
   submit to their company accounting department for reimbursement.

### Step 1

Since a travel order is ultimately created from a travel request, the request's parameters should be 1:1 with the travel
order being issued, as a travel order is an official and legally required document.

In the request creation flow, travelers can enter an _expected_ start and end time that will enable start and end
reminders, respectively.

### Step 2

Travel managers get notified via [mappings](#traveler-to-travel-manager-mappings).

Travel managers should be mapped to travelers with which they have a close work relationship.
In other words, they know the context of the trip and can make informed decisions about whether to approve it or not.
It is the manager's responsibility to verify the correctness of the request's parameters.

When approved, a request to a 3rd party is sent to create a travel order using the request's parameters.
Travel orders do not contain times, only dates of when the trip is starting/ending.
Travelers can enter an _expected_ start and end time when creating the request that will enable start and end reminders,
respectively.

In this step, managers can override the _expected_ start and end time that will enable start and end
reminders, respectively.

### Step 3

The traveler is notified of the approval or rejection via email and push notifications. These notifications should link
the traveler to the trip overview screen (if approved).

Travel managers can create a trip directly without going through the process of issuing a travel request, skipping
steps 1 and 2.

### Step 4

If the trip has a _reminder start time, a reminder is sent at that time to the traveler via push notification.
This reminder has a link for the traveler to go the trip overview screen.

Since the idea is to streamline the process of creating a report, travelers can update the trip's parameters at any
time. They can edit expenses and their descriptions.

PDF previews of the report can be generated to get an overview of how it will eventually be sent to accounting.

### Step 5

Once the trip has ended, the traveler verifies the start time, end time, and expenses.
If they are traveling with a personal vehicle and haven't provided the mileage yet, they
must do so in order to send the report to accounting.

Once everything is verified, they can send the report to accounting and their trip is considered complete.

If for some reason accounting rejects the report and asks for changes, the traveler can make them and
regenerate the report at any time.