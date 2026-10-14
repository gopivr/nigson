package com.nigson.commerce.fulfillment;
import java.util.*;
import com.nigson.shared.problem.*;
/** Provider truth cannot debit stock or manufacture an operational physical handover. */
public final class CarrierResultStates {
 private CarrierResultStates(){}
 public static String next(String current,CarrierResultValues.Result input,boolean older,boolean booked){
  if(older)return current;
  if("BOOKED".equals(input.bookingState()))return Set.of("CREATED","EXCEPTION").contains(current)?"READY":current;
  if(Set.of("UNKNOWN","FAILED").contains(Objects.requireNonNullElse(input.bookingState(),"")))return Set.of("CREATED","EXCEPTION").contains(current)?"EXCEPTION":current;
  if(input.status().equals("CANCELLED")){if(Set.of("SHIPPED","DELIVERED").contains(current))throw new Problem.Failure(ProblemCode.INVALID_TRANSITION);return "CANCELLED";}
  if(current.equals("CANCELLED")||current.equals("DELIVERED"))return current;
  if(!booked)throw FulfillmentValues.rule();
  return switch(input.status()){
   case "SHIPPED" -> current.equals("EXCEPTION")?"READY":current;
   case "DELIVERED" -> {if(!current.equals("SHIPPED"))throw new Problem.Failure(ProblemCode.INVALID_TRANSITION);yield "DELIVERED";}
   case "EXCEPTION" -> current.equals("READY")?"EXCEPTION":current;
   case "CREATED" -> current;
   default -> throw FulfillmentValues.rule();
  };
 }
}
