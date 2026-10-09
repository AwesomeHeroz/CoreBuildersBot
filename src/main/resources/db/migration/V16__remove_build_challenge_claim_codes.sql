ALTER TABLE build_challenge_winners
    DROP INDEX uq_build_challenge_claim_code,
    DROP COLUMN claim_code;
