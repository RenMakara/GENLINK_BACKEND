--liquibase formatted sql

--changeset genlink:0008-create-finance-tables
CREATE TABLE IF NOT EXISTS expenses (
    expense_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    title VARCHAR(160) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    category VARCHAR(80),
    description TEXT,
    expense_date DATE NOT NULL,
    payment_method VARCHAR(80),
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_expenses_user_id FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS budgets (
    budget_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    month INTEGER NOT NULL,
    year INTEGER NOT NULL,
    total_budget NUMERIC(19,2) NOT NULL,
    spent_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    remaining_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_budgets_user_id FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT uk_budgets_user_month_year UNIQUE (user_id, month, year)
);

CREATE TABLE IF NOT EXISTS budget_categories (
    budget_category_id UUID PRIMARY KEY,
    budget_id UUID NOT NULL,
    category VARCHAR(80) NOT NULL,
    allocated_amount NUMERIC(19,2) NOT NULL,
    spent_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    remaining_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_budget_categories_budget_id FOREIGN KEY (budget_id) REFERENCES budgets(budget_id),
    CONSTRAINT uk_budget_categories_budget_category UNIQUE (budget_id, category)
);

CREATE TABLE IF NOT EXISTS saving_goals (
    saving_goal_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    title VARCHAR(160) NOT NULL,
    target_amount NUMERIC(19,2) NOT NULL,
    current_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    target_date DATE,
    status VARCHAR(50) NOT NULL,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_saving_goals_user_id FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS saving_deposits (
    deposit_id UUID PRIMARY KEY,
    saving_goal_id UUID NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    note TEXT,
    deposit_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_saving_deposits_saving_goal_id FOREIGN KEY (saving_goal_id) REFERENCES saving_goals(saving_goal_id)
);

CREATE TABLE IF NOT EXISTS financial_lessons (
    lesson_id UUID PRIMARY KEY,
    title_en VARCHAR(180) NOT NULL,
    title_km VARCHAR(180),
    content_en TEXT NOT NULL,
    content_km TEXT,
    topic VARCHAR(80),
    content_type VARCHAR(50),
    estimated_time INTEGER,
    xp_reward INTEGER NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH'
);

CREATE TABLE IF NOT EXISTS financial_quiz_questions (
    question_id UUID PRIMARY KEY,
    lesson_id UUID NOT NULL,
    question_text_en TEXT NOT NULL,
    question_text_km TEXT,
    option_a_en TEXT NOT NULL,
    option_a_km TEXT,
    option_b_en TEXT NOT NULL,
    option_b_km TEXT,
    option_c_en TEXT,
    option_c_km TEXT,
    option_d_en TEXT,
    option_d_km TEXT,
    correct_answer VARCHAR(10) NOT NULL,
    explanation_en TEXT,
    explanation_km TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_financial_quiz_questions_lesson_id FOREIGN KEY (lesson_id) REFERENCES financial_lessons(lesson_id)
);

CREATE TABLE IF NOT EXISTS demo_wallets (
    wallet_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    balance NUMERIC(19,2) NOT NULL DEFAULT 0,
    currency VARCHAR(10) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_demo_wallets_user_id FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT uk_demo_wallets_user UNIQUE (user_id)
);

CREATE TABLE IF NOT EXISTS demo_transactions (
    transaction_id UUID PRIMARY KEY,
    wallet_id UUID NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_demo_transactions_wallet_id FOREIGN KEY (wallet_id) REFERENCES demo_wallets(wallet_id)
);

--rollback DROP TABLE IF EXISTS demo_transactions; DROP TABLE IF EXISTS demo_wallets; DROP TABLE IF EXISTS financial_quiz_questions; DROP TABLE IF EXISTS financial_lessons; DROP TABLE IF EXISTS saving_deposits; DROP TABLE IF EXISTS saving_goals; DROP TABLE IF EXISTS budget_categories; DROP TABLE IF EXISTS budgets; DROP TABLE IF EXISTS expenses;
