CREATE FUNCTION commerce.guard_affiliate_bank() RETURNS trigger LANGUAGE plpgsql SET search_path=pg_catalog,commerce AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Affiliate bank history is immutable'; END IF;
 IF TG_OP='INSERT' THEN
  IF NEW.status<>'PENDING' OR NEW.version<>0 OR NEW.reviewed_by IS NOT NULL OR NEW.reviewed_at IS NOT NULL OR NEW.review_reason IS NOT NULL THEN RAISE EXCEPTION 'New affiliate bank details require review'; END IF;
  IF EXISTS(SELECT 1 FROM commerce.affiliate_payout WHERE party_id=NEW.party_id AND state='PREPARED') THEN RAISE EXCEPTION 'Prepared payout locks bank details'; END IF;
  RETURN NEW;
 END IF;
 IF (NEW.id,NEW.party_id,NEW.revision,NEW.previous_id,NEW.payload_ciphertext,NEW.content_hash,NEW.cipher_mode,NEW.created_at,NEW.created_by) IS DISTINCT FROM (OLD.id,OLD.party_id,OLD.revision,OLD.previous_id,OLD.payload_ciphertext,OLD.content_hash,OLD.cipher_mode,OLD.created_at,OLD.created_by) THEN RAISE EXCEPTION 'Affiliate bank details are immutable'; END IF;
 IF OLD.status<>'PENDING' OR NEW.status NOT IN ('APPROVED','REJECTED') OR NEW.version<>OLD.version+1 OR NEW.reviewed_by IS NULL OR NEW.reviewed_at IS NULL OR length(trim(NEW.review_reason))<3 OR NEW.review_reason IS NULL THEN RAISE EXCEPTION 'Invalid affiliate bank review transition'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER affiliate_bank_guard BEFORE INSERT OR UPDATE OR DELETE ON commerce.affiliate_bank_detail FOR EACH ROW EXECUTE FUNCTION commerce.guard_affiliate_bank();

CREATE FUNCTION commerce.guard_affiliate_payout() RETURNS trigger LANGUAGE plpgsql SET search_path=pg_catalog,commerce AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Affiliate payout history is immutable'; END IF;
 IF TG_OP='INSERT' THEN
  IF NEW.state<>'PREPARED' OR NEW.version<>0 OR NEW.transfer_reference IS NOT NULL OR NEW.paid_at IS NOT NULL OR NEW.resolved_by IS NOT NULL OR NEW.cancel_reason IS NOT NULL THEN RAISE EXCEPTION 'New affiliate payout must be prepared'; END IF;
  IF NOT EXISTS(SELECT 1 FROM commerce.affiliate_bank_detail WHERE id=NEW.bank_detail_id AND party_id=NEW.party_id AND status='APPROVED') THEN RAISE EXCEPTION 'Reviewed affiliate bank details required'; END IF;
  RETURN NEW;
 END IF;
 IF (NEW.id,NEW.party_id,NEW.bank_detail_id,NEW.period,NEW.amount,NEW.created_at,NEW.created_by) IS DISTINCT FROM (OLD.id,OLD.party_id,OLD.bank_detail_id,OLD.period,OLD.amount,OLD.created_at,OLD.created_by) THEN RAISE EXCEPTION 'Affiliate payout financial snapshot is immutable'; END IF;
 IF OLD.state<>'PREPARED' OR NEW.state NOT IN ('PAID','CANCELLED') OR NEW.version<>OLD.version+1 OR NEW.resolved_by IS NULL THEN RAISE EXCEPTION 'Invalid affiliate payout transition'; END IF;
 IF NEW.state='PAID' AND (NEW.transfer_reference IS NULL OR length(NEW.transfer_reference)=0 OR NEW.paid_at IS NULL OR NEW.cancel_reason IS NOT NULL) THEN RAISE EXCEPTION 'Confirmed transfer reference required'; END IF;
 IF NEW.state='CANCELLED' AND (NEW.transfer_reference IS NOT NULL OR NEW.paid_at IS NOT NULL OR NEW.cancel_reason IS NULL OR length(trim(NEW.cancel_reason))<3) THEN RAISE EXCEPTION 'No-transfer cancellation reason required'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER affiliate_payout_guard BEFORE INSERT OR UPDATE OR DELETE ON commerce.affiliate_payout FOR EACH ROW EXECUTE FUNCTION commerce.guard_affiliate_payout();
