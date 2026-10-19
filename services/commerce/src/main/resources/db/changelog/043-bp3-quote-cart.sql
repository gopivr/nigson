ALTER TABLE commerce.cart ADD COLUMN source_quote_revision_id uuid;
ALTER TABLE commerce.cart ADD CONSTRAINT cart_source_quote_revision_id_nonnil_ck CHECK (source_quote_revision_id <> '00000000-0000-0000-0000-000000000000'::uuid);
ALTER TABLE commerce.cart ADD CONSTRAINT cart_source_quote_revision_id_fk FOREIGN KEY (source_quote_revision_id) REFERENCES commerce.quote_revision (id) ON DELETE RESTRICT;
DROP INDEX commerce.cart_active_context_uq;
CREATE UNIQUE INDEX cart_active_context_uq ON commerce.cart (party_id,coalesce(organization_id,'00000000-0000-0000-0000-000000000000'::uuid),market_id) WHERE status='ACTIVE' AND source_quote_revision_id IS NULL;
CREATE UNIQUE INDEX cart_quote_revision_uq ON commerce.cart (source_quote_revision_id) WHERE source_quote_revision_id IS NOT NULL;
CREATE INDEX cart_source_quote_revision_id_fk_ix ON commerce.cart (source_quote_revision_id);

CREATE OR REPLACE FUNCTION commerce.guard_quote_cart_revision() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.source_quote_revision_id IS DISTINCT FROM OLD.source_quote_revision_id AND (OLD.source_quote_revision_id IS NOT NULL OR NEW.source_quote_revision_id IS NULL) THEN
    RAISE EXCEPTION 'QUOTE_CART_REVISION_IMMUTABLE';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER cart_quote_revision_guard BEFORE UPDATE ON commerce.cart FOR EACH ROW EXECUTE FUNCTION commerce.guard_quote_cart_revision();
REVOKE ALL ON FUNCTION commerce.guard_quote_cart_revision() FROM PUBLIC,nigson_commerce_app;

CREATE FUNCTION commerce.guard_quote_revision() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'QUOTE_REVISION_IMMUTABLE'; END IF;
  IF TG_OP = 'INSERT' THEN
    IF NEW.approval_status <> 'DRAFT' OR NEW.approved_by IS NOT NULL OR NEW.revision < 1 OR NEW.total < 0 THEN RAISE EXCEPTION 'QUOTE_REVISION_INVALID'; END IF;
    RETURN NEW;
  END IF;
  IF NEW.quote_id IS DISTINCT FROM OLD.quote_id OR NEW.revision IS DISTINCT FROM OLD.revision OR NEW.currency_code IS DISTINCT FROM OLD.currency_code OR NEW.valid_until IS DISTINCT FROM OLD.valid_until OR NEW.terms IS DISTINCT FROM OLD.terms OR NEW.total IS DISTINCT FROM OLD.total OR NEW.id IS DISTINCT FROM OLD.id OR NEW.created_at IS DISTINCT FROM OLD.created_at OR NEW.created_by IS DISTINCT FROM OLD.created_by THEN RAISE EXCEPTION 'QUOTE_REVISION_IMMUTABLE'; END IF;
  IF OLD.approval_status <> 'DRAFT' OR NEW.approval_status NOT IN ('APPROVED','REJECTED') OR NEW.approved_by IS NULL OR NEW.version <> OLD.version + 1 THEN RAISE EXCEPTION 'QUOTE_APPROVAL_TRANSITION_INVALID'; END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER quote_revision_guard BEFORE INSERT OR UPDATE OR DELETE ON commerce.quote_revision FOR EACH ROW EXECUTE FUNCTION commerce.guard_quote_revision();
REVOKE ALL ON FUNCTION commerce.guard_quote_revision() FROM PUBLIC,nigson_commerce_app;

CREATE FUNCTION commerce.guard_quote_line() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP <> 'INSERT' THEN RAISE EXCEPTION 'QUOTE_LINE_IMMUTABLE'; END IF;
  IF NOT EXISTS (SELECT 1 FROM commerce.quote_revision r WHERE r.id=NEW.revision_id AND r.approval_status='DRAFT') THEN RAISE EXCEPTION 'QUOTE_LINE_REVISION_NOT_DRAFT'; END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER quote_line_guard BEFORE INSERT OR UPDATE OR DELETE ON commerce.quote_line FOR EACH ROW EXECUTE FUNCTION commerce.guard_quote_line();
REVOKE ALL ON FUNCTION commerce.guard_quote_line() FROM PUBLIC,nigson_commerce_app;

CREATE FUNCTION commerce.guard_quote() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE revision_quote uuid; revision_state varchar(20); revision_until timestamptz;
BEGIN
  IF TG_OP = 'INSERT' THEN
    IF NEW.status <> 'DRAFT' OR NEW.current_revision_id IS NOT NULL OR NEW.accepted_order_id IS NOT NULL THEN RAISE EXCEPTION 'QUOTE_INITIAL_STATE_INVALID'; END IF;
    RETURN NEW;
  END IF;
  IF NEW.party_id IS DISTINCT FROM OLD.party_id OR NEW.organization_id IS DISTINCT FROM OLD.organization_id OR NEW.market_id IS DISTINCT FROM OLD.market_id OR NEW.source_lead_id IS DISTINCT FROM OLD.source_lead_id OR NEW.id IS DISTINCT FROM OLD.id OR NEW.created_at IS DISTINCT FROM OLD.created_at OR NEW.created_by IS DISTINCT FROM OLD.created_by THEN RAISE EXCEPTION 'QUOTE_IDENTITY_IMMUTABLE'; END IF;
  IF NEW.version < OLD.version OR NEW.version > OLD.version + 1 THEN RAISE EXCEPTION 'QUOTE_VERSION_INVALID'; END IF;
  IF NEW.current_revision_id IS DISTINCT FROM OLD.current_revision_id THEN
    SELECT quote_id,approval_status,valid_until INTO revision_quote,revision_state,revision_until FROM commerce.quote_revision WHERE id=NEW.current_revision_id;
    IF revision_quote IS DISTINCT FROM NEW.id OR revision_state <> 'DRAFT' OR NEW.status <> 'DRAFT' OR NEW.accepted_order_id IS NOT NULL THEN RAISE EXCEPTION 'QUOTE_REVISION_POINTER_INVALID'; END IF;
    IF OLD.current_revision_id IS NOT NULL AND NEW.version <> OLD.version + 1 THEN RAISE EXCEPTION 'QUOTE_VERSION_INVALID'; END IF;
  ELSIF NEW.status IS DISTINCT FROM OLD.status THEN
    IF NEW.version <> OLD.version + 1 THEN RAISE EXCEPTION 'QUOTE_VERSION_INVALID'; END IF;
    IF NEW.status='OFFERED' AND OLD.status='DRAFT' THEN
      SELECT quote_id,approval_status,valid_until INTO revision_quote,revision_state,revision_until FROM commerce.quote_revision WHERE id=NEW.current_revision_id;
      IF revision_quote IS DISTINCT FROM NEW.id OR revision_state <> 'APPROVED' OR NOT revision_until > now() THEN RAISE EXCEPTION 'QUOTE_OFFER_NOT_APPROVED_OR_EXPIRED'; END IF;
    ELSIF NEW.status='ACCEPTED' AND OLD.status='OFFERED' THEN
      IF NEW.accepted_order_id IS NULL OR NEW.current_revision_id IS DISTINCT FROM OLD.current_revision_id THEN RAISE EXCEPTION 'QUOTE_ACCEPTANCE_INVALID'; END IF;
    ELSIF NEW.status='DRAFT' AND OLD.status='OFFERED' THEN
      IF NEW.current_revision_id IS NOT DISTINCT FROM OLD.current_revision_id THEN RAISE EXCEPTION 'QUOTE_REVISION_REQUIRED'; END IF;
    ELSE RAISE EXCEPTION 'QUOTE_TRANSITION_INVALID'; END IF;
  ELSIF NEW.accepted_order_id IS DISTINCT FROM OLD.accepted_order_id THEN RAISE EXCEPTION 'QUOTE_ACCEPTANCE_INVALID';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER quote_guard BEFORE INSERT OR UPDATE ON commerce.quote FOR EACH ROW EXECUTE FUNCTION commerce.guard_quote();
REVOKE ALL ON FUNCTION commerce.guard_quote() FROM PUBLIC,nigson_commerce_app;
