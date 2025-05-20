-- Travel managers
CREATE TABLE bonvoyage_travel_managers(
    user_id TEXT PRIMARY KEY,
    user_full_name TEXT NOT NULL,
    user_email TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

SELECT manage_updated_at('bonvoyage_travel_managers');
CREATE INDEX ON bonvoyage_travel_managers (user_id);
CREATE INDEX ON bonvoyage_travel_managers (user_email);

-- Travel manager -> user mappings
-- Used for notifying travel managers of new travel requests.
CREATE TABLE bonvoyage_travel_manager_user_mappings(
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    travel_manager_id TEXT NOT NULL REFERENCES bonvoyage_travel_managers(user_id) ON DELETE CASCADE,
    user_id TEXT NOT NULL,
    -- Type of notification delivery mechanism, e.g. email, push, etc.
    delivery TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_travel_manager_user_mapping UNIQUE(travel_manager_id, user_id, delivery)
);

SELECT manage_updated_at('bonvoyage_travel_manager_user_mappings');
CREATE INDEX ON bonvoyage_travel_manager_user_mappings (travel_manager_id);
CREATE INDEX ON bonvoyage_travel_manager_user_mappings (user_id);

-- A Bonvoyage trip is an entry for a business trip that has been approved by a travel manager
-- and whose travel order has been created.
CREATE TABLE bonvoyage_trips(
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    travel_order_id TEXT NOT NULL,

    -- Traveler parameters
    user_id TEXT NOT NULL,
    user_full_name TEXT NOT NULL,
    user_email TEXT NOT NULL,

    -- Immutable trip parameters
    start_location TEXT NOT NULL,
    stops TEXT NOT NULL,
    end_location TEXT NOT NULL,
    transport_type TEXT NOT NULL,
    description TEXT NOT NULL,

    start_date DATE NOT NULL,
    end_date DATE NOT NULL,

    -- Parameters for reminder
    start_reminder_time TIMETZ,
    end_reminder_time TIMETZ,

    start_reminder_sent_at TIMESTAMPTZ,
    end_reminder_sent_at TIMESTAMPTZ,

    -- Actual trip parameters used for the report
    start_time TIMETZ,
    end_time TIMETZ,

    -- Which version of this trip was sent to accounting.
    version_sent INT,
    version INT NOT NULL DEFAULT 0,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Optional fields when the trip is with a personal vehicle
    vehicle_type TEXT,
    vehicle_registration TEXT,
    start_mileage TEXT,
    end_mileage TEXT,
    is_driver BOOLEAN NOT NULL DEFAULT FALSE
);

SELECT manage_message_group_parent('bonvoyage_trips');
SELECT manage_version('bonvoyage_trips');
SELECT manage_updated_at('bonvoyage_trips');

CREATE INDEX ON bonvoyage_trips (user_id);
CREATE INDEX ON bonvoyage_trips (travel_order_id);
CREATE INDEX ON bonvoyage_trips (start_date);
CREATE INDEX ON bonvoyage_trips (end_date);
CREATE INDEX ON bonvoyage_trips (start_reminder_sent_at);
CREATE INDEX ON bonvoyage_trips (end_reminder_sent_at);

-- When a travel request is approved and a trip is created, this message is created.
CREATE TABLE bonvoyage_trip_welcome_messages(
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    trip_id UUID NOT NULL REFERENCES bonvoyage_trips(id) ON DELETE CASCADE,
    content TEXT NOT NULL
);

CREATE INDEX ON bonvoyage_trip_welcome_messages (trip_id);

-- A travel request is issued by a traveler to a travel manager.
CREATE TABLE bonvoyage_travel_requests(
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

    -- Traveler parameters
    user_id TEXT NOT NULL,
    user_full_name TEXT NOT NULL,
    user_email TEXT NOT NULL,

    -- Immutable trip parameters
    start_location TEXT NOT NULL,
    stops TEXT NOT NULL,
    end_location TEXT NOT NULL,
    transport_type TEXT NOT NULL,
    description TEXT NOT NULL,

    -- Personal vehicle fields
    vehicle_type TEXT,
    vehicle_registration TEXT,
    is_driver BOOLEAN NOT NULL DEFAULT FALSE,

    start_date DATE NOT NULL,
    end_date DATE NOT NULL,

    -- Best guess, can differ from actual trip report, used for reminders
    expected_start_time TIMETZ,
    expected_end_time TIMETZ,

    -- Review parameters
    status TEXT NOT NULL,
    reviewer_id TEXT,
    review_comment TEXT,

    -- Only non-null if the status is APPROVED
    trip_id UUID REFERENCES bonvoyage_trips(id) ON DELETE SET NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ON bonvoyage_travel_requests (user_id);
CREATE INDEX ON bonvoyage_travel_requests (trip_id);
CREATE INDEX ON bonvoyage_travel_requests (status);

CREATE TABLE bonvoyage_travel_expenses(
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    trip_id UUID NOT NULL REFERENCES bonvoyage_trips(id) ON DELETE CASCADE,
    message_group_id UUID NOT NULL REFERENCES message_groups(id) ON DELETE CASCADE,
    amount FLOAT NOT NULL,
    currency TEXT NOT NULL,
    image_path TEXT NOT NULL,
    image_provider TEXT NOT NULL,
    description TEXT NOT NULL,
    expense_created_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

SELECT manage_updated_at('bonvoyage_travel_expenses');
CREATE INDEX ON bonvoyage_travel_expenses (trip_id);
CREATE INDEX ON bonvoyage_travel_expenses (expense_created_at);
CREATE INDEX ON bonvoyage_travel_expenses (created_at);