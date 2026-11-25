package com.nigson.commerce.fulfillment;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import com.nigson.commerce.inventory.InventoryAuthority;
import com.nigson.commerce.pricing.PricingValues;
import com.nigson.shared.problem.ApiValues;
import com.nigson.shared.problem.Problem;
import com.nigson.shared.problem.ProblemCode;
import com.nigson.shared.security.ActorAssertion;

/** Staff-only allocation view used to construct a valid shipment command. */
@RestController
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED", havingValue="true")
public final class FulfillmentReadController {
  public record Line(UUID orderLineId, UUID balanceId, String quantity) {}
  public record Shipment(UUID id, String status, long version, String trackingUrl) {}
  public record View(UUID orderId, List<Line> lines, Shipment shipment) {}

  private final JdbcTemplate jdbc;
  private final InventoryAuthority authority;

  public FulfillmentReadController(JdbcTemplate jdbc, InventoryAuthority authority) {
    this.jdbc = jdbc;
    this.authority = authority;
  }

  @GetMapping(value="/api/v1/staff/orders/{id}/fulfillment", produces="application/json")
  public ResponseEntity<View> view(HttpServletRequest request, @PathVariable String id) {
    if (!request.getParameterMap().isEmpty()) throw ApiValues.invalid();
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof ActorAssertion.Actor actor)) {
      throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
    }
    authority.require(actor);
    UUID orderId = ApiValues.uuid(id);
    Boolean exists = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM commerce.sales_order WHERE id=?)", Boolean.class, orderId);
    if (!Boolean.TRUE.equals(exists)) throw new Problem.Failure(ProblemCode.NOT_FOUND);
    var lines = jdbc.query("SELECT l.id, r.balance_id, r.quantity FROM commerce.inventory_reservation r JOIN commerce.inventory_balance b ON b.id=r.balance_id JOIN commerce.order_line l ON l.order_id=r.order_id AND l.sku_id=b.sku_id WHERE r.order_id=? AND r.status='ALLOCATED' ORDER BY l.id,r.balance_id", (rs, row) -> new Line(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), PricingValues.decimal(rs.getBigDecimal(3))), orderId);
    var shipments = jdbc.query("SELECT id,status,version,tracking_url FROM commerce.shipment WHERE order_id=? AND status<>'CANCELLED' ORDER BY created_at DESC,id DESC LIMIT 1", (rs, row) -> new Shipment(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3), rs.getString(4)), orderId);
    return ResponseEntity.ok().header("Cache-Control", "no-store").body(new View(orderId, List.copyOf(lines), shipments.isEmpty() ? null : shipments.getFirst()));
  }
}
