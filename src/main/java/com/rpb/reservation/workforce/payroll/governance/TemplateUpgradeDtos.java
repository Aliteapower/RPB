package com.rpb.reservation.workforce.payroll.governance;

import com.rpb.reservation.workforce.payroll.PayrollDtos.Rule;
import static com.rpb.reservation.workforce.payroll.governance.TemplatePublicationDtos.*;
import java.time.LocalDate;
import java.util.*;

public final class TemplateUpgradeDtos {
 private TemplateUpgradeDtos(){}
 public record Resolution(String path,String choice){}
 public record Selection(Long targetReleaseVersion,Long expectedLatestStoreVersion,String previewHash,List<Resolution> resolutions){}
 public record UpgradeWrite(Long expectedRevision,UUID companyId,UUID adoptionId,Long targetReleaseVersion,LocalDate effectiveDate,Long expectedLatestStoreVersion,String previewHash,Rule derivedRule,List<Resolution> resolutions,String reason){}
 public record Difference(String path,String label,String oldBase,String local,String target,boolean localChanged,boolean targetChanged,boolean conflict){}
 public record Preview(Adoption baseAdoption,Release oldRelease,Release targetRelease,long expectedLatestStoreVersion,LocalDate effectiveDate,List<Difference> differences,boolean hasLocalChanges,String previewHash){}
 public record Context(Release oldRelease,Release targetRelease,long expectedLatestStoreVersion,LocalDate effectiveDate,String previewHash,List<Resolution> resolutions){}
}
