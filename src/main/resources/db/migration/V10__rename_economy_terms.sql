-- Keep existing progression and marketplace data while updating user-facing achievement text.
UPDATE achievements
SET description = 'Earn your first 100 Building points.'
WHERE code = 'FIRST_STONE';

UPDATE achievements
SET description = 'Earn 10,000 Building points.'
WHERE code = 'MASTER_BUILDER';

UPDATE achievements
SET description = 'Earn 5,000 Infrastructure points.'
WHERE code = 'ENGINEER';

UPDATE achievements
SET description = 'Reach 4,000 lifetime points.'
WHERE code = 'CORE_VETERAN';

UPDATE achievements
SET description = 'Reach 8,000 lifetime points.'
WHERE code = 'CORE_ELITE';

UPDATE achievements
SET description = 'Reach 15,000 lifetime points.'
WHERE code = 'CORE_NOBLE';

UPDATE achievements
SET description = 'Reach 30,000 lifetime points.'
WHERE code = 'CORE_LEGEND';
