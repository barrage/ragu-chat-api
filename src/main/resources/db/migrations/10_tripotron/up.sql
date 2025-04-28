-- A Tripotron workflow is essentially an entry for a business trip.
CREATE TABLE tripotron_workflows(
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id TEXT NOT NULL,
    -- Includes first and last names
    user_full_name TEXT NOT NULL,
    travel_order_id TEXT NOT NULL,
    start_location TEXT NOT NULL,
    end_location TEXT NOT NULL,
    start_date_time TIMESTAMPTZ NOT NULL,
    end_date_time TIMESTAMPTZ NOT NULL,
    transport_type TEXT NOT NULL,
    description TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Optional fields when the trip is with a personal vehicle
    vehicle_type TEXT,
    vehicle_registration TEXT,
    start_mileage TEXT,
    end_mileage TEXT
);

CREATE TABLE tripotron_travel_expenses(
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workflow_id UUID NOT NULL REFERENCES tripotron_workflows(id) ON DELETE CASCADE,
    amount FLOAT NOT NULL,
    currency TEXT NOT NULL,
    image_path TEXT NOT NULL,
    image_provider TEXT NOT NULL,
    description TEXT NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

SELECT manage_updated_at('tripotron_workflows');
SELECT manage_updated_at('tripotron_travel_expenses');