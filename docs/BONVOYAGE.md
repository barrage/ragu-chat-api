# Bonvoyage Ragu Plugin

The Bonvoyage plugin provides business trip management solutions for streamlining the creation
of travel orders and travel expense reports.

## Entities

Bonvoyage consists of the following entities:

- **Bonvoyage users**
  In order to use Bonvoyage, Ragu users must be registered as Bonvoyage users. This must be done by an administrator.
  Bonvoyage users issue *travel order requests* to *travel managers*. In further text, these may also be referred to as
  *travelers*.
 
- **Travel managers**
  A subset of *Bonvoyage users* who can approve *travel order requests*. These are also assigned by administrators.
  A *Bonvoyage user* cannot be created if there are no available *travel managers*.

- **Travel requests**
  Issued by **Bonvoyage users** to **travel managers**. They are the first step in the Bonvoyage flow and represent
  a user's intent to register a business trip. The management of travel orders is usually enforced by law in some
  way which is why it is important to not let users create travel orders on their own, since mistakes can be costly.

- **Trips**
  A Bonvoyage workflow - termed trip - is the main stage of the flow. It is an approved *travel request* for which 
  a corresponding *travel order* has been created. This is the main entity with which *Bonvoyage users* interact in the
  system. Through this entity users can upload expenses and generate trip reports. Once a workflow has been finalized,
  it becomes **completed** and read-only. Note that generating a trip report does not finalize the workflow.

- **Travel expenses**
  Represent any expenses made on *trips* that are eligible for reimbursement. Expense receipt images are uploaded by 
  *Bonvoyage users* and are processed by multi-modal LLMs to create database entries. 
  Since this is mostly an automatic process, expenses must be verified by users who uploaded them and their 
  database entries can be updated if necessary.

- **Traveler events**
  Immutable entities that get created whenever there is some interaction between a user and their *trip*. They represent specific events during the
  time the trip is active. For example, the time when the traveler was notified that their trip was created, the time when they confirmed
  their departure time, etc.

Note, travel orders are not a part of this plugin and are handled externally by a 3rd party. The only connection is
the travel order ID which is used to link the *trip* to the external system.

## Flow overview

1. A *Bonvoyage user* issues a *travel request* to a *travel manager*.
2. The *travel manager* is notified of the request and either:
     - approves the request in which case a *trip* is created upon 
       successfully creating a travel order via some 3rd party.
     - rejects the request in which case the flow ends here.
3. The traveler is notified of the approved request and the newly created *trip*.
   In this step, the system will set up notification handlers which will aptly remind the traveler that their trip
   is starting and ending at a specific administrator-defined interval before the official start and end of the trip, 
   respectively.
