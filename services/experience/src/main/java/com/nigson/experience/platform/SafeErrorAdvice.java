package com.nigson.experience.platform;
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
  List<Problem.Violation> violations=failure instanceof Problem.Failure safe?safe.violations:List.of();
  if(failure instanceof MethodArgumentNotValidException invalid){Class<?> type=invalid.getBindingResult().getTarget()==null?Object.class:invalid.getBindingResult().getTarget().getClass();violations=invalid.getBindingResult().getFieldErrors().stream().limit(100).map(e->new Problem.Violation(ApiValues.field(type,e.getField()),"INVALID_VALUE","Supply a valid value.")).distinct().toList();}
  if(failure instanceof com.nigson.shared.security.ProviderHttp.Failure provider){
   String guidance=switch(provider.getMessage()){case "TERMII_CREDENTIALS_NOT_CONFIGURED"->"Termii messaging credentials are not configured.";case "SMTP_CREDENTIALS_NOT_CONFIGURED"->"Email SMTP credentials are not configured.";default->null;};
   if(guidance!=null)violations=List.of(new Problem.Violation("provider","CONFIGURATION_REQUIRED",guidance));
  }
  problems.write(request,response,problems.classify(failure),violations);
 }
}
