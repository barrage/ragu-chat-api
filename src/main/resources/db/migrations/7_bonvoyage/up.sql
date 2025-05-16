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

-- A Bonvoyage workflow is an entry for a business trip that has been approved by a travel manager
-- and whose travel order has been created.
CREATE TABLE bonvoyage_workflows(
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

    start_date_time TIMESTAMPTZ NOT NULL,
    actual_start_date_time TIMESTAMPTZ,
    end_date_time TIMESTAMPTZ NOT NULL,
    actual_end_date_time TIMESTAMPTZ,

    completed BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Optional fields when the trip is with a personal vehicle
    vehicle_type TEXT,
    vehicle_registration TEXT,
    start_mileage TEXT,
    end_mileage TEXT
);

SELECT manage_message_group_parent('bonvoyage_workflows');
SELECT manage_updated_at('bonvoyage_workflows');
CREATE INDEX ON bonvoyage_workflows (user_id);
CREATE INDEX ON bonvoyage_workflows (travel_order_id);
CREATE INDEX ON bonvoyage_workflows (start_date_time);
CREATE INDEX ON bonvoyage_workflows (end_date_time);
CREATE INDEX ON bonvoyage_workflows (created_at);

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

    -- Mandatory fields when the trip is with a personal vehicle
    vehicle_type TEXT,
    vehicle_registration TEXT,

    -- Best guess, can differ from actual trip report
    start_date_time TIMESTAMPTZ NOT NULL,
    end_date_time TIMESTAMPTZ NOT NULL,

    -- Review parameters
    status TEXT NOT NULL,
    reviewer_id TEXT,
    review_comment TEXT,
    workflow_id UUID REFERENCES bonvoyage_workflows(id) ON DELETE SET NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ON bonvoyage_travel_requests (user_id);
CREATE INDEX ON bonvoyage_travel_requests (workflow_id);
CREATE INDEX ON bonvoyage_travel_requests (status);

CREATE TABLE bonvoyage_travel_expenses(
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workflow_id UUID NOT NULL REFERENCES bonvoyage_workflows(id) ON DELETE CASCADE,
    message_group_id UUID NOT NULL REFERENCES message_groups(id) ON DELETE CASCADE,
    amount FLOAT NOT NULL,
    currency TEXT NOT NULL,
    image_path TEXT NOT NULL,
    image_provider TEXT NOT NULL,
    description TEXT NOT NULL,
    expense_created_at TIMESTAMPTZ NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

SELECT manage_updated_at('bonvoyage_travel_expenses');
CREATE INDEX ON bonvoyage_travel_expenses (workflow_id);
CREATE INDEX ON bonvoyage_travel_expenses (expense_created_at);
CREATE INDEX ON bonvoyage_travel_expenses (verified);
CREATE INDEX ON bonvoyage_travel_expenses (created_at);