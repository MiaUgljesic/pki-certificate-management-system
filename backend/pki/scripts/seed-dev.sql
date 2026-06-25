-- Optional: If you only need login and orgs (no certificate issuance), use this SQL script.
-- For certificate issuance, run scripts/seed-dev.sh so org keys are generated.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Organizations
INSERT INTO organizations (name, created_at)
VALUES
  ('OrgOne', NOW()),
  ('OrgTwo', NOW())
ON CONFLICT (name) DO NOTHING;

-- Users (passwords hashed with bcrypt)
INSERT INTO users (email, password_hash, first_name, last_name, role, created_at, active, two_factor_enabled, organization_id)
VALUES
  ('admin@example.com', crypt('123123', gen_salt('bf')), 'Admin', 'User', 'ADMIN', NOW(), true, false, NULL),
  ('ca1@orgone.com', crypt('123123', gen_salt('bf')), 'CA', 'OrgOne', 'CA_USER', NOW(), true, false, (SELECT id FROM organizations WHERE name='OrgOne')),
  ('ca2@orgtwo.com', crypt('123123', gen_salt('bf')), 'CA', 'OrgTwo', 'CA_USER', NOW(), true, false, (SELECT id FROM organizations WHERE name='OrgTwo')),
  ('user1@orgone.com', crypt('123123', gen_salt('bf')), 'User', 'OrgOne', 'USER', NOW(), true, false, (SELECT id FROM organizations WHERE name='OrgOne')),
  ('user2@orgtwo.com', crypt('123123', gen_salt('bf')), 'User', 'OrgTwo', 'USER', NOW(), true, false, (SELECT id FROM organizations WHERE name='OrgTwo'))
ON CONFLICT (email) DO NOTHING;
