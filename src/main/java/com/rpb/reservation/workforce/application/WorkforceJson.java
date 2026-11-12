package com.rpb.reservation.workforce.application;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
@Component
public class WorkforceJson {
    private final ObjectMapper mapper;
    public WorkforceJson(ObjectMapper mapper){this.mapper=mapper;}
    public String write(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("workforce_json",e);}}
    public <T>T read(String value,Class<T> type){try{return mapper.readValue(value,type);}catch(Exception e){throw new IllegalStateException("workforce_json",e);}}
}
