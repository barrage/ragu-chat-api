-- A Tripotron workflow is essentially an entry for a business trip.
CREATE TABLE bonvoyage_workflows(
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id TEXT NOT NULL,
    -- Includes first and last names
    user_full_name TEXT NOT NULL,
    travel_order_id TEXT NOT NULL,
    start_location TEXT NOT NULL,
    destination TEXT NOT NULL,
    end_location TEXT NOT NULL,
    start_date_time TIMESTAMPTZ NOT NULL,
    end_date_time TIMESTAMPTZ NOT NULL,
    transport_type TEXT NOT NULL,
    description TEXT NOT NULL,
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

CREATE TABLE bonvoyage_travel_expenses(
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workflow_id UUID NOT NULL REFERENCES bonvoyage_workflows(id) ON DELETE CASCADE,
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

CREATE INDEX ON bonvoyage_workflows (user_id);
CREATE INDEX ON bonvoyage_workflows (travel_order_id);
CREATE INDEX ON bonvoyage_workflows (start_date_time);
CREATE INDEX ON bonvoyage_workflows (end_date_time);
CREATE INDEX ON bonvoyage_workflows (completed);
CREATE INDEX ON bonvoyage_workflows (created_at);

CREATE INDEX ON bonvoyage_travel_expenses (workflow_id);
CREATE INDEX ON bonvoyage_travel_expenses (expense_created_at);
CREATE INDEX ON bonvoyage_travel_expenses (verified);
CREATE INDEX ON bonvoyage_travel_expenses (created_at);

SELECT manage_updated_at('bonvoyage_workflows');
SELECT manage_updated_at('bonvoyage_travel_expenses');