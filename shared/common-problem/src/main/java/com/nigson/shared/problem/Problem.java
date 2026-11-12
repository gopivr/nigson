package com.nigson.shared.problem;
import java.time.Instant;
import java.util.*;
public record Problem(String type,String title,int status,String detail,String instance,String code,
                      Instant timestamp,UUID correlationId,String traceId,List<Violation> violations) {
 public record Violation(String field,String code,String message) {}
 public Problem { violations=List.copyOf(violations); }
 public static final class Failure extends RuntimeException {
  public final ProblemCode code;
  public final List<Violation> violations;
  public Failure(ProblemCode code){this(code,List.of());}
  public Failure(ProblemCode code,List<Violation> violations){super(code.name());this.code=Objects.requireNonNull(code);this.violations=List.copyOf(violations);}
 }
}
