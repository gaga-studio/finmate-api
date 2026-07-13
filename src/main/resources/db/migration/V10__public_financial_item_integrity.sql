ALTER TABLE finmate_public_financial_item
    ADD CONSTRAINT finmate_public_financial_item_required_values_check CHECK (
        (field_name = 'ASSETS'
            AND balance_krw IS NOT NULL
            AND as_of_date IS NOT NULL)
        OR (field_name IN ('INCOME', 'SPENDING', 'SAVING')
            AND amount_krw IS NOT NULL
            AND as_of_date IS NOT NULL)
        OR (field_name = 'FINANCIAL_PRODUCTS'
            AND balance_krw IS NOT NULL
            AND as_of_date IS NOT NULL)
        OR (field_name = 'INVESTMENT_HOLDINGS'
            AND balance_krw IS NOT NULL
            AND ticker IS NOT NULL
            AND allocation_bps IS NOT NULL
            AND quantity IS NOT NULL
            AND as_of_date IS NOT NULL)
        OR (field_name = 'TRADES'
            AND amount_krw IS NOT NULL
            AND ticker IS NOT NULL
            AND action IS NOT NULL
            AND quantity IS NOT NULL
            AND occurred_at IS NOT NULL)
    ),
    ADD CONSTRAINT finmate_public_financial_item_non_negative_values_check CHECK (
        (amount_krw IS NULL OR amount_krw >= 0)
        AND (balance_krw IS NULL OR balance_krw >= 0)
        AND (quantity IS NULL OR quantity >= 0)
    );
