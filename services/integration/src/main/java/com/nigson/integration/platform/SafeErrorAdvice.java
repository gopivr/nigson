package com.nigson.integration.platform;
import com.nigson.shared.problem.*;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import java.io.IOException;
import java.util.*;
@RestControllerAdvice
public class SafeErrorAdvice {
 private final ProblemWriter problems;public SafeErrorAdvice(ProblemWriter problems){this.problems=problems;}
 @ExceptionHandler(Exception.class) public void failed(Exception failure,HttpServletRequest request,HttpServletResponse response)throws IOException{
  List<Problem.Violation> violations=List.of();
  if(failure instanceof MethodArgumentNotValidException invalid){Class<?> type=invalid.getBindingResult().getTarget()==null?Object.class:invalid.getBindingResult().getTarget().getClass();violations=invalid.getBindingResult().getFieldErrors().stream().limit(100).map(e->new Problem.Violation(ApiValues.field(type,e.getField()),"INVALID_VALUE","Supply a valid value.")).distinct().toList();}
  problems.write(request,response,problems.classify(failure),violations);
 }
}
