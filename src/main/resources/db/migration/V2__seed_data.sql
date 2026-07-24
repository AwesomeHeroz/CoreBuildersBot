INSERT IGNORE INTO achievements(code, name, description, metric, category, threshold, reward_cxp, reward_credits) VALUES
('FIRST_STONE', 'First Stone', 'Earn your first 100 Building CXP.', 'CATEGORY_XP', 'BUILDING', 100, 50, 25),
('MASTER_BUILDER', 'Master Builder', 'Earn 10,000 Building CXP.', 'CATEGORY_XP', 'BUILDING', 10000, 500, 250),
('SPAWN_HELPER_I', 'Helping Hand', 'Complete 10 approved Spawn Help contributions.', 'APPROVED_CONTRIBUTIONS', 'SPAWN_HELP', 10, 100, 50),
('SPAWN_GUARDIAN', 'Spawn Guardian', 'Complete 100 approved Spawn Help contributions.', 'APPROVED_CONTRIBUTIONS', 'SPAWN_HELP', 100, 500, 250),
('ENGINEER', 'Engineer', 'Earn 5,000 Infrastructure CXP.', 'CATEGORY_XP', 'INFRASTRUCTURE', 5000, 300, 150),
('CORE_VETERAN', 'Core Veteran', 'Reach 4,000 lifetime Core XP.', 'TOTAL_XP', NULL, 4000, 250, 100),
('CORE_ELITE', 'Core Elite', 'Reach 8,000 lifetime Core XP.', 'TOTAL_XP', NULL, 8000, 400, 200),
('CORE_NOBLE', 'Core Noble', 'Reach 15,000 lifetime Core XP.', 'TOTAL_XP', NULL, 15000, 750, 350),
('CORE_LEGEND', 'Core Legend', 'Reach 30,000 lifetime Core XP.', 'TOTAL_XP', NULL, 30000, 1500, 750);

INSERT IGNORE INTO shop_items(code, name, description, price, stock) VALUES
('BASIC_GEAR', 'Basic Gear Shulker', 'A basic gear package for group members.', 250, NULL),
('SURVIVAL_KIT', 'Survival Kit', 'A complete survival resupply kit.', 400, NULL),
('XP_SHULKER', 'XP Bottle Shulker', 'A shulker of XP bottles.', 500, NULL),
('BUILDING_PACKAGE', 'Building Material Package', 'A standard package of building materials.', 750, NULL),
('LARGE_MATERIAL_REQUEST', 'Large Material Request', 'A larger custom material allocation.', 2000, NULL),
('CUSTOM_MAPART', 'Custom Mapart Request', 'Request a custom group mapart project.', 3000, NULL),
('PROJECT_ASSISTANCE', 'Group Project Assistance', 'Request organized group assistance for a project.', 5000, NULL);
