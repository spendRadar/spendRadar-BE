-- 기존 데모 DB의 수입 거래에도 expense_kind를 생략할 수 있도록 제약만 완화합니다.
ALTER TABLE IF EXISTS ledger ALTER COLUMN expense_kind DROP NOT NULL;
