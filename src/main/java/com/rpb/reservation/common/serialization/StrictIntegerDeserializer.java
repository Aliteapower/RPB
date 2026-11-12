package com.rpb.reservation.common.serialization;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

/** Opt-in JSON contract: never silently truncate counts or coerce text into bottles/months. */
public class StrictIntegerDeserializer extends StdDeserializer<Integer> {
 public StrictIntegerDeserializer(){super(Integer.class);}
 @Override public Integer deserialize(JsonParser parser,DeserializationContext context)throws IOException{
  if(parser.currentToken()!=JsonToken.VALUE_NUMBER_INT)throw JsonMappingException.from(parser,"数量必须为整数");
  return parser.getIntValue();
 }
 @Override public Integer getNullValue(DeserializationContext context)throws JsonMappingException{throw JsonMappingException.from(context,"数量不能为空");}
}
