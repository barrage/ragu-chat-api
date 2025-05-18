# Bonvoyage Ragu Plugin

The Bonvoyage plugin is a business trip management solution for streamlining the creation
of travel orders and travel expense reports.

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

An **active trip** is a trip whose `actualStartDatetime` property has been set, and whose `actualEndDateTime` is not
yet set. Only a single active trip can exist at a time per user.

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
     - approves the request in which case a *trip* is created upon 
       successfully creating a travel order via some 3rd party.
     - rejects the request in which case the flow ends here.
3. The traveler is notified of the approved request and the newly created *trip*.
   In this step, the system will set up notification handlers which will aptly remind the traveler that their trip
   is starting and ending. At this point the trip is read-only, meaning the user can chat with their
   assistant about it, but cannot make any changes to the trip's properties until they start it.
4. The traveler starts the trip and, if using a personal vehicle, provides the start mileage.
5. The traveler uploads receipts of expenses made on the trip.
6. The traveler ends the trip and, if using a personal vehicle, provides the end mileage.
7. The traveler verifies, i.e. makes sure all the expenses made on the trip have the correct amount, currency, 
   and description. They then generate the trip report which gets delivered to them via email, which they can then
   submit to their company accounting department for reimbursement.

Travel managers can create a trip directly without going through the process of issuing a travel request, skipping
steps 1 and 2.
