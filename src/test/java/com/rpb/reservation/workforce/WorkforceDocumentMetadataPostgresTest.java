package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.documents.*;
import com.rpb.reservation.workforce.documents.DocumentDtos.*;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import com.rpb.reservation.workforce.statutory.StatutoryDtos.CpfProfile;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.time.*;import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class WorkforceDocumentMetadataPostgresTest extends WorkforcePayrollPostgresTest {
 EmployeeDocumentService docs;UUID type;
 @BeforeEach void metadata(){docs=proxy(new EmployeeDocumentService(access,repository,new DocumentRepository(jdbc),new WorkforceAudit(logs,new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules())));type=docs.types(admin,store).getFirst().id();}
 Write document(long version,String name,LocalDate approval,LocalDate pr){return new Write(version,type,"S0000000A",LocalDate.of(2030,1,1),"Synthetic",false,name,approval,pr);}
 Document pr(){return docs.save(admin,store,employee,UUID.randomUUID(),document(0,"Synthetic Worker",LocalDate.of(2025,9,20),LocalDate.of(2025,10,1)));}
 @Test void metadataSurvivesLegacyWritesAndCanBeClearedExplicitly(){
  var d=pr();assertThat(d.holderName()).isEqualTo("Synthetic Worker");assertThat(d.approvedOn()).isEqualTo(LocalDate.of(2025,9,20));assertThat(d.prEffectiveOn()).isEqualTo(LocalDate.of(2025,10,1));
  var legacy=docs.save(admin,store,employee,d.id(),new Write(1L,type,d.number(),d.expiresOn(),"Legacy edit",false));assertThat(legacy.approvedOn()).isEqualTo(d.approvedOn());assertThat(legacy.prEffectiveOn()).isEqualTo(d.prEffectiveOn());assertThat(legacy.holderName()).isEqualTo(d.holderName());
  var clear=docs.save(admin,store,employee,d.id(),document(2,"",null,null));assertThat(clear.approvedOn()).isNull();assertThat(clear.prEffectiveOn()).isNull();assertThat(clear.holderName()).isEmpty();assertThatThrownBy(()->docs.save(admin,store,employee,d.id(),document(2,"",null,null))).isInstanceOf(WorkforceException.class);
 }
 @Test void invalidDateOrderingIsRejectedWithoutUpdatingDocument(){
  var d=pr();assertThatThrownBy(()->docs.save(admin,store,employee,d.id(),document(1,"Wrong",LocalDate.of(2031,1,1),d.prEffectiveOn()))).isInstanceOf(WorkforceException.class);assertThat(docs.list(admin,store,employee).items().getFirst()).isEqualTo(d);
 }
 @Test void salaryPlanRequiresEmployeeOwnedExplicitPrDateAndOrdinaryApprovalIsNotPr(){
  var ordinary=docs.save(admin,store,employee,UUID.randomUUID(),document(0,"Worker",LocalDate.of(2025,9,20),null));assertThat(configuration.profiles(admin,store,company).employeePrDocuments()).doesNotContainKey(employee);
  var d=pr();assertThat(configuration.profiles(admin,store,company).employeePrDocuments().get(employee)).extracting(PrDocument::id).containsExactly(d.id());assertThat(configuration.profiles(admin,store,company).employeePrDocuments().get(employee).getFirst().number()).isEqualTo("****000A");
  assertThatThrownBy(()->configuration.saveProfile(admin,store,profile,new ProfileWrite(1L,company,employee,LocalDate.of(2026,10,1),template,rule(),new CpfProfile("spr",LocalDate.of(1990,1,1),ordinary.approvedOn(),"GG","",ordinary.id())))).isInstanceOf(WorkforceException.class);
  assertThatThrownBy(()->configuration.saveProfile(admin,store,profile,new ProfileWrite(1L,company,employee,LocalDate.of(2026,10,1),template,rule(),new CpfProfile("spr",LocalDate.of(1990,1,1),d.approvedOn(),"GG","",d.id())))).isInstanceOf(WorkforceException.class);
  var saved=configuration.saveProfile(admin,store,profile,new ProfileWrite(1L,company,employee,LocalDate.of(2026,10,1),template,rule(),new CpfProfile("spr",LocalDate.of(1990,1,1),d.prEffectiveOn(),"GG","",d.id())));assertThat(saved.cpf().prDocumentId()).isEqualTo(d.id());
 }
 @Test void changedPrEvidenceRequiresReviewWithoutRewritingConfirmedCpf(){
  var d=pr();configuration.saveProfile(admin,store,profile,new ProfileWrite(1L,company,employee,LocalDate.of(2026,10,1),template,rule(),new CpfProfile("spr",LocalDate.of(1990,1,1),d.prEffectiveOn(),"GG","",d.id())));completed();time.at("2026-11-02T01:00:00Z");var b=action(create(),"calculate");assertThat(b.calculation().totalEmployeeCpf()).isEqualTo("40.00");assertThat(b.calculation().issues()).isEmpty();b=action(b,"confirm");var frozen=b.calculation();
  docs.save(worker,store,employee,d.id(),document(d.version(),d.holderName(),LocalDate.of(2024,9,20),LocalDate.of(2024,10,1)));
  var current=sources.capture(tenant,company,YearMonth.of(2026,10),List.of());assertThat(current.lines().getFirst().issues()).contains("CPF_DOCUMENT_REVIEW_REQUIRED");assertThat(current.lines().getFirst().profile().cpf().prDate()).isEqualTo(LocalDate.of(2025,10,1));var detail=batches.detail(admin,store,b.batch().id());assertThat(detail.sourceChanged()).isTrue();assertThat(detail.calculation()).isEqualTo(frozen);
 }
 @Test void deletedEvidenceBlocksNewCalculationsAndCrossEmployeeReferenceIsRejected(){
  var d=pr();configuration.saveProfile(admin,store,profile,new ProfileWrite(1L,company,employee,LocalDate.of(2026,10,1),template,rule(),new CpfProfile("spr",LocalDate.of(1990,1,1),d.prEffectiveOn(),"GG","",d.id())));docs.save(admin,store,employee,d.id(),new Write(1L,type,d.number(),d.expiresOn(),d.notes(),true));assertThat(sources.capture(tenant,company,YearMonth.of(2026,10),List.of()).lines().getFirst().issues()).contains("CPF_DOCUMENT_REVIEW_REQUIRED");
  assertThatThrownBy(()->configuration.saveProfile(admin,store,profile,new ProfileWrite(2L,company,employee,LocalDate.of(2026,10,1),template,rule(),new CpfProfile("spr",LocalDate.of(1990,1,1),d.prEffectiveOn(),"GG","",UUID.randomUUID())))).isInstanceOf(WorkforceException.class);
 }
}
