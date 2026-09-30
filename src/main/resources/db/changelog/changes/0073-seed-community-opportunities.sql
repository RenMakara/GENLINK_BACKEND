--liquibase formatted sql

--changeset genlink:0073-seed-community-opportunities
-- Seed initial opportunities across various categories (INTERNSHIP, SCHOLARSHIP, JOB, HACKATHON, VOLUNTEER)
-- so that students have active, functional opportunities to browse, bookmark, and apply for.
INSERT INTO opportunities (
    opportunity_id,
    title,
    organization,
    type,
    description,
    deadline,
    location,
    apply_link,
    created_at,
    created_by,
    updated_at,
    updated_by
) VALUES
    (
        'd0000000-0000-4000-8000-000000000001',
        'Mobile Software Engineer Intern (Flutter)',
        'ACLEDA Bank Plc.',
        'INTERNSHIP',
        'Join ACLEDA Bank digital engineering team to build innovative campus banking and youth payment solutions. You will collaborate with senior mobile developers, build smooth Flutter widgets, integrate RESTful APIs, and participate in code reviews.',
        CURRENT_DATE + INTERVAL '45 days',
        'Phnom Penh (Headquarters)',
        'https://www.acledabank.com.kh/careers',
        CURRENT_TIMESTAMP,
        'GenLinkKH',
        CURRENT_TIMESTAMP,
        'GenLinkKH'
    ),
    (
        'd0000000-0000-4000-8000-000000000002',
        'FinTech Excellence Undergraduate Scholarship',
        'ACLEDA University of Business',
        'SCHOLARSHIP',
        'Full 100% tuition grant awarded to high-achieving undergraduate students pursuing studies in Financial Technology, Software Engineering, and Digital Banking. Applicants must maintain a minimum GPA of 3.2 and submit a 500-word statement of purpose.',
        CURRENT_DATE + INTERVAL '60 days',
        'Phnom Penh Campus',
        'https://aub.edu.kh/scholarships',
        CURRENT_TIMESTAMP,
        'GenLinkKH',
        CURRENT_TIMESTAMP,
        'GenLinkKH'
    ),
    (
        'd0000000-0000-4000-8000-000000000003',
        'Junior Backend Developer (Java & Spring Boot)',
        'GenLink Technologies',
        'JOB',
        'We are seeking a motivated Junior Backend Developer to design, optimize, and maintain high-performance microservices. Experience with Java 21, Spring Boot 3, PostgreSQL, and Docker is preferred. Fresh graduates with strong portfolios are encouraged to apply.',
        CURRENT_DATE + INTERVAL '30 days',
        'Phnom Penh / Hybrid',
        'https://genlink.kh/careers/backend-dev',
        CURRENT_TIMESTAMP,
        'GenLinkKH',
        CURRENT_TIMESTAMP,
        'GenLinkKH'
    ),
    (
        'd0000000-0000-4000-8000-000000000004',
        'Cambodia National Student FinTech Hackathon 2026',
        'Ministry of Post and Telecoms (MPTC) & ACLEDA',
        'HACKATHON',
        'A 48-hour competitive innovation sprint bringing together student teams across Cambodia to build practical digital finance, smart savings, and financial literacy solutions. Top 3 teams win cash prizes up to $5,000 and direct interview fast-tracks.',
        CURRENT_DATE + INTERVAL '25 days',
        'CADT Innovation Center, Phnom Penh',
        'https://cadt.edu.kh/hackathon2026',
        CURRENT_TIMESTAMP,
        'GenLinkKH',
        CURRENT_TIMESTAMP,
        'GenLinkKH'
    ),
    (
        'd0000000-0000-4000-8000-000000000005',
        'Youth Financial Literacy Student Ambassador',
        'Cambodia Digital Youth Network',
        'VOLUNTEER',
        'Volunteer opportunity for energetic student leaders to organize peer financial wellness workshops, teach budgeting best practices, and introduce safe digital banking habits across university campuses in Phnom Penh.',
        CURRENT_DATE + INTERVAL '90 days',
        'Phnom Penh & Regional Campuses',
        'https://digitalyouth.kh/ambassadors',
        CURRENT_TIMESTAMP,
        'GenLinkKH',
        CURRENT_TIMESTAMP,
        'GenLinkKH'
    );

--rollback DELETE FROM opportunities WHERE opportunity_id IN ('d0000000-0000-4000-8000-000000000001', 'd0000000-0000-4000-8000-000000000002', 'd0000000-0000-4000-8000-000000000003', 'd0000000-0000-4000-8000-000000000004', 'd0000000-0000-4000-8000-000000000005');
