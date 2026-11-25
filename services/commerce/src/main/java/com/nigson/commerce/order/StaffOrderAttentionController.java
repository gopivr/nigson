package com.nigson.commerce.order;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.nigson.shared.problem.Problem;
import com.nigson.shared.problem.ProblemCode;
import com.nigson.shared.problem.ApiValues;
import com.nigson.shared.security.ActorAssertion;

/** Compact after-sales state for the staff order list. */
@RestController
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED", havingValue="true")
public final class StaffOrderAttentionController {
  public record Item(UUID orderId, String returnState, String refundState, Instant updatedAt) {}
  public record View(List<Item> items) {}

  private final JdbcTemplate jdbc;
  private final OrderAuthority authority;

  public StaffOrderAttentionController(JdbcTemplate jdbc, OrderAuthority authority) {
    this.jdbc = jdbc;
    this.authority = authority;
  }

  @GetMapping(value="/api/v1/staff/orders/after-sales", produces="application/json")
  public ResponseEntity<View> list(HttpServletRequest request) {
    if (!request.getParameterMap().isEmpty()) throw ApiValues.invalid();
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);
    if (!(authentication.getPrincipal() instanceof ActorAssertion.Actor actor)) throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
    authority.staff(actor, false, false);
    var rows = jdbc.query("SELECT rr.order_id,rr.state,rf.state,rr.updated_at FROM commerce.return_request rr LEFT JOIN commerce.refund rf ON rf.id=rr.refund_id WHERE rr.state<>'REJECTED' ORDER BY rr.updated_at DESC,rr.id DESC LIMIT 1000", (rs, row) -> new Item(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant()));
    return ResponseEntity.ok().header("Cache-Control", "no-store").body(new View(List.copyOf(rows)));
  }
}
