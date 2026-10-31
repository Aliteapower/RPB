package com.rpb.reservation.workforce.payroll.governance;

import com.rpb.reservation.workforce.payroll.PayrollDtos.Rule;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/** Explicit API snapshots. Release and adoption records are append-only. */
public final class TemplatePublicationDtos {
 private TemplatePublicationDtos(){}
 public record DraftWrite(Long expectedVersion,String name,Rule rule,List<UUID> companyIds,List<UUID> storeIds,Boolean includeFutureStores){}
 public record PublicationDirectory(List<com.rpb.reservation.workforce.application.WorkforceDtos.Option> companies,List<com.rpb.reservation.workforce.application.WorkforceDtos.StoreOption> stores){}
 public record Draft(UUID id,long version,String name,Rule rule,List<UUID> companyIds,List<UUID> storeIds,boolean includeFutureStores){}
 public record Publish(UUID commandId,Long expectedDraftVersion){}
 public record Release(UUID masterId,long version,long draftVersion,String name,Rule rule,List<UUID> companyIds,List<UUID> storeIds,boolean includeFutureStores,List<UUID> existingStoreIds,Instant publishedAt,UUID publishedBy){}
 public record Enable(UUID commandId,Long releaseVersion,Long expectedAdoptionVersion){}
 public record Adoption(UUID id,UUID masterId,long releaseVersion,UUID companyId,UUID storeId,long storeVersion,UUID materializedTemplateId,String name,Rule rule,UUID enabledBy,Instant enabledAt){}
 public record ScheduledAdoption(Adoption adoption,LocalDate effectiveDate){}
 public record LibraryEntry(UUID masterId,String name,long latestReleaseVersion,Adoption adoption,String state,List<ScheduledAdoption> scheduledAdoptions){public LibraryEntry(UUID masterId,String name,long latestReleaseVersion,Adoption adoption,String state){this(masterId,name,latestReleaseVersion,adoption,state,List.of());}}
 public record Preview(Release release,Adoption adoption,boolean updateAvailable,List<ScheduledAdoption> scheduledAdoptions){public Preview(Release release,Adoption adoption,boolean updateAvailable){this(release,adoption,updateAvailable,List.of());}}
}
