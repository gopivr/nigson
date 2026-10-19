package com.nigson.commerce.reporting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import com.nigson.shared.problem.Problem;
import com.nigson.shared.problem.ProblemCode;
import com.nigson.shared.problem.ApiValues;
import com.nigson.shared.security.ActorAssertion;

/** Bounded, owner-local sanitized aggregates. All monetary values are in one requested currency. */
public final class JdbcReportRepository implements ReportRepository {
    private static final int MAX_ROWS = 100;
    private final JdbcTemplate jdbc;
    public JdbcReportRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public ReportValues.Summary commerce(ActorAssertion.Actor actor, ReportValues.Query q) {
        if (actor.organization() != null && !actor.organization().equals(q.organizationId()))
            throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
        if (q.organizationId() != null) {
            Boolean exists = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM commerce.organization WHERE id=? AND status='ACTIVE')", Boolean.class, q.organizationId());
            if (!Boolean.TRUE.equals(exists)) throw new Problem.Failure(ProblemCode.NOT_FOUND);
        }
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM commerce.currency WHERE code=?)",Boolean.class,q.currency()))) throw ApiValues.invalid();
        var args = new ArrayList<Object>();
        args.add(q.currency()); args.add(Timestamp.from(q.from())); args.add(Timestamp.from(q.to()));
        String org = q.organizationId() == null ? "" : " AND o.organization_id=?";
        if (q.organizationId() != null) args.add(q.organizationId());
        var total = jdbc.queryForMap("SELECT COALESCE(sum(o.grand_total-o.refunded_total),0) sales, count(*) orders, count(DISTINCT o.party_id) customers FROM commerce.sales_order o WHERE o.currency_code=? AND o.payment_status IN ('PAID','PARTIALLY_REFUNDED','REFUNDED') AND o.created_at>=? AND o.created_at<?"+org, args.toArray());
        var minorUnit=jdbc.queryForObject("SELECT minor_unit FROM commerce.currency WHERE code=?",Integer.class,q.currency());
        String productSql="WITH eligible AS MATERIALIZED (SELECT id FROM commerce.sales_order WHERE currency_code=? AND payment_status IN ('PAID','PARTIALLY_REFUNDED','REFUNDED') AND created_at>=? AND created_at<?"+(q.organizationId()==null?"":" AND organization_id=?")+") SELECT l.sku_id,s.quantity_scale,sum(l.quantity-COALESCE(rf.quantity,0)) quantity,sum(l.line_total-COALESCE(rf.amount,0)) revenue FROM eligible e JOIN commerce.order_line l ON l.order_id=e.id JOIN commerce.sku s ON s.id=l.sku_id LEFT JOIN (SELECT rl.order_line_id,sum(rl.quantity) quantity,sum(rl.amount) amount FROM commerce.refund_line rl JOIN commerce.refund r ON r.id=rl.refund_id JOIN commerce.order_line source_line ON source_line.id=rl.order_line_id JOIN eligible re ON re.id=source_line.order_id WHERE r.state='SUCCEEDED' GROUP BY rl.order_line_id) rf ON rf.order_line_id=l.id GROUP BY l.sku_id,s.quantity_scale ORDER BY quantity DESC,revenue DESC,l.sku_id LIMIT ?";
        var productArgs=new ArrayList<>(args);productArgs.add(MAX_ROWS+1);
        var products = jdbc.query(productSql, (r,n)->new ReportValues.Product(r.getObject("sku_id",java.util.UUID.class), r.getBigDecimal("quantity").setScale(r.getInt("quantity_scale"),RoundingMode.UNNECESSARY).toPlainString(), money(r.getBigDecimal("revenue"),q.currency(),minorUnit)), productArgs.toArray());
        if (products.size() > MAX_ROWS) throw new Problem.Failure(ProblemCode.REPORTING_RULE_VIOLATION);
        String stockSql = "SELECT b.sku_id,b.location_id,b.on_hand,b.reserved,b.allocated,b.version,s.quantity_scale FROM commerce.inventory_balance b JOIN commerce.sku s ON s.id=b.sku_id JOIN commerce.stock_location l ON l.id=b.location_id WHERE l.active=true ORDER BY b.sku_id,b.location_id LIMIT ?";
        var stockRows = jdbc.query(stockSql,(r,n)->new ReportValues.Stock(r.getObject("sku_id",java.util.UUID.class),r.getObject("location_id",java.util.UUID.class),scale(r.getBigDecimal("on_hand"),r.getInt("quantity_scale")),scale(r.getBigDecimal("reserved"),r.getInt("quantity_scale")),scale(r.getBigDecimal("allocated"),r.getInt("quantity_scale")),scale(r.getBigDecimal("on_hand").subtract(r.getBigDecimal("reserved")).subtract(r.getBigDecimal("allocated")),r.getInt("quantity_scale")),r.getLong("version")),MAX_ROWS+1);
        if(stockRows.size()>MAX_ROWS) throw new Problem.Failure(ProblemCode.REPORTING_RULE_VIOLATION);
        return new ReportValues.Summary(new ReportValues.Period(q.from(),q.to()),q.currency(),money((BigDecimal)total.get("sales"),q.currency(),minorUnit),((Number)total.get("orders")).longValue(),((Number)total.get("customers")).longValue(),products,stockRows);
    }
    private static String scale(BigDecimal value,int scale){return value.setScale(scale,RoundingMode.UNNECESSARY).toPlainString();}
    private static ReportValues.Money money(BigDecimal amount,String currency,int minorUnit){return new ReportValues.Money(amount.setScale(minorUnit,RoundingMode.UNNECESSARY).toPlainString(),currency);}
}
