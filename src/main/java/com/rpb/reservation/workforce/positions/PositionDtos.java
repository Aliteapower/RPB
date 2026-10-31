package com.rpb.reservation.workforce.positions;
import java.util.UUID;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
public final class PositionDtos {
 private PositionDtos(){}
 public record Position(UUID id,String name,boolean enabled,long version,@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) String attendanceMode,@JsonInclude(NON_NULL) List<String> attendanceModes){public Position(UUID id,String name,boolean enabled,long version,String attendanceMode){this(id,name,enabled,version,attendanceMode,null);}public Position(UUID id,String name,boolean enabled,long version){this(id,name,enabled,version,null);}}
 public record PositionWrite(Long expectedVersion,String name,Boolean enabled,String attendanceMode,@JsonInclude(NON_NULL) List<String> attendanceModes){public PositionWrite(Long expectedVersion,String name,Boolean enabled,String attendanceMode){this(expectedVersion,name,enabled,attendanceMode,null);}public PositionWrite(Long expectedVersion,String name,Boolean enabled){this(expectedVersion,name,enabled,null);}}
 public record DeleteWrite(Long expectedVersion){}
 public record Deleted(UUID id,boolean deleted,long version){}
}
