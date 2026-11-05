package com.rpb.reservation.workforce.scheduling;
import java.util.UUID;
public final class ScheduleDeletionDtos {
 private ScheduleDeletionDtos(){}
 public record Request(Long expectedVersion){}
 public record Result(UUID id,long version,boolean deleted){}
}
