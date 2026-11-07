INSERT INTO sectors (id, name, notification_email, active, created_at, updated_at, version)
VALUES
    ('10000000-0000-0000-0000-000000000001', 'Agriculture and Food', 'agriculture@example.invalid', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('10000000-0000-0000-0000-000000000002', 'Digital Technology', 'digital@example.invalid', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('10000000-0000-0000-0000-000000000003', 'Health and Life Sciences', 'health@example.invalid', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO sector_members (id, sector_id, subject)
VALUES
    ('11000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'demo-sector-agriculture'),
    ('11000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002', 'demo-sector-digital'),
    ('11000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000003', 'demo-sector-health')
ON CONFLICT (id) DO NOTHING;

INSERT INTO submissions (
    id, code, category, title, purpose, problem_addressed, registered_by,
    receiving_officer, assigned_reviewer_subject, student_number, programme,
    supervisor, recommendation_letter_status, responsible_sector_id, stage,
    received_at, forwarded_at, created_at, updated_at, version
)
VALUES
    ('20000000-0000-0000-0000-000000000001', 'STU-2026-900001', 'STUDENT', 'Solar irrigation monitor', 'Improve smallholder irrigation decisions', 'Farmers lack timely soil and water readings', 'demo-submitter-1', 'demo-reception', 'demo-reviewer-1', 'DEMO-001', 'Agricultural Engineering', 'Dr. Amina Nsubuga', 'SUBMITTED', '10000000-0000-0000-0000-000000000001', 'FORWARDED', CURRENT_TIMESTAMP - INTERVAL '10 days', CURRENT_TIMESTAMP - INTERVAL '9 days', CURRENT_TIMESTAMP - INTERVAL '10 days', CURRENT_TIMESTAMP - INTERVAL '9 days', 0),
    ('20000000-0000-0000-0000-000000000002', 'STU-2026-900002', 'STUDENT', 'Low-cost diagnostic kit', 'Prototype a field diagnostic kit', 'Rural clinics have limited rapid testing tools', 'demo-submitter-2', 'demo-reception', 'demo-reviewer-2', 'DEMO-002', 'Biomedical Engineering', 'Dr. Peter Okello', 'VERIFIED', '10000000-0000-0000-0000-000000000003', 'IN_PROGRESS', CURRENT_TIMESTAMP - INTERVAL '12 days', CURRENT_TIMESTAMP - INTERVAL '11 days', CURRENT_TIMESTAMP - INTERVAL '12 days', CURRENT_TIMESTAMP - INTERVAL '2 days', 0),
    ('20000000-0000-0000-0000-000000000003', 'GEN-2026-900001', 'GENERAL', 'Community cold storage', 'Explore an affordable cold-chain solution', 'Market vendors lose produce to unreliable cooling', 'demo-submitter-3', 'demo-reception', 'demo-reviewer-1', NULL, NULL, NULL, NULL, NULL, 'RECEIVED', CURRENT_TIMESTAMP - INTERVAL '3 days', NULL, CURRENT_TIMESTAMP - INTERVAL '3 days', CURRENT_TIMESTAMP - INTERVAL '3 days', 0),
    ('20000000-0000-0000-0000-000000000004', 'GEN-2026-900002', 'GENERAL', 'Accessible learning platform', 'Connect learners to accessible course materials', 'Students with disabilities lack localized resources', 'demo-submitter-4', 'demo-reception', 'demo-reviewer-3', NULL, NULL, NULL, NULL, '10000000-0000-0000-0000-000000000002', 'FORWARDED', CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '5 days', 0),
    ('20000000-0000-0000-0000-000000000005', 'STU-2026-900003', 'STUDENT', 'Waste sorting assistant', 'Build a practical waste classification tool', 'Small institutions lack reliable waste sorting guidance', 'demo-submitter-5', 'demo-reception', 'demo-reviewer-2', 'DEMO-005', 'Computer Science', 'Dr. Sarah Namukasa', 'ONE_FILE', '10000000-0000-0000-0000-000000000002', 'COMPLETED', CURRENT_TIMESTAMP - INTERVAL '30 days', CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '30 days', CURRENT_TIMESTAMP - INTERVAL '1 day', 0),
    ('20000000-0000-0000-0000-000000000006', NULL, 'GENERAL', 'Maker-space booking system', 'Coordinate shared equipment access', 'Innovation teams cannot see equipment availability', 'demo-submitter-6', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'DRAFT', NULL, NULL, CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP - INTERVAL '1 day', 0),
    ('20000000-0000-0000-0000-000000000007', 'STU-2026-900004', 'STUDENT', 'Water quality sensor', 'Monitor water quality near schools', 'Water testing is infrequent and expensive', 'demo-submitter-7', 'demo-reception', 'demo-reviewer-3', 'DEMO-007', 'Environmental Science', 'Dr. James Kato', 'NOT_REQUIRED', '10000000-0000-0000-0000-000000000003', 'FORWARDED', CURRENT_TIMESTAMP - INTERVAL '7 days', CURRENT_TIMESTAMP - INTERVAL '6 days', CURRENT_TIMESTAMP - INTERVAL '7 days', CURRENT_TIMESTAMP - INTERVAL '6 days', 0),
    ('20000000-0000-0000-0000-000000000008', 'GEN-2026-900004', 'GENERAL', 'Micro-logistics network', 'Improve last-mile delivery coordination', 'Small enterprises face expensive fragmented delivery', 'demo-submitter-8', 'demo-reception', 'demo-reviewer-1', NULL, NULL, NULL, NULL, '10000000-0000-0000-0000-000000000002', 'IN_PROGRESS', CURRENT_TIMESTAMP - INTERVAL '15 days', CURRENT_TIMESTAMP - INTERVAL '14 days', CURRENT_TIMESTAMP - INTERVAL '15 days', CURRENT_TIMESTAMP - INTERVAL '4 days', 0),
    ('20000000-0000-0000-0000-000000000009', 'STU-2026-900005', 'STUDENT', 'Renewable energy dashboard', 'Visualize campus energy use', 'Facilities teams lack actionable consumption data', 'demo-submitter-9', 'demo-reception', 'demo-reviewer-2', 'DEMO-009', 'Electrical Engineering', 'Dr. Grace Atim', 'SUBMITTED', NULL, 'RECEIVED', CURRENT_TIMESTAMP - INTERVAL '2 days', NULL, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days', 0),
    ('20000000-0000-0000-0000-000000000010', NULL, 'GENERAL', 'Youth enterprise toolkit', 'Support youth-led innovation projects', 'Early-stage founders need practical startup guidance', 'demo-submitter-10', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'DRAFT', NULL, NULL, CURRENT_TIMESTAMP - INTERVAL '4 hours', CURRENT_TIMESTAMP - INTERVAL '4 hours', 0)
ON CONFLICT (id) DO NOTHING;
