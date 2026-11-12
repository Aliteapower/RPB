package com.rpb.reservation.workforce;
import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import com.rpb.reservation.workforce.payroll.governance.EffectivePayGovernance;
import java.time.*;import java.util.*;
import org.junit.jupiter.api.Test;

class WageEventCompatibilityTest {
 final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
 WageEvents.Evidence evidence(String classification,LocalDate from,LocalDate to,LocalDate due){return new WageEvents.Evidence(UUID.randomUUID(),"bonus",classification,from,to,null,due,"2026-10","2026-10","contract #1","period evidence");}
 @Test void oldItemSerializationAndGuardCanonicalStayByteIdentical()throws Exception{String raw="{\"label\":\"奖金\",\"kind\":\"ow_additional\",\"amount\":\"100.00\"}";var item=json.readValue(raw,Item.class);assertThat(json.writeValueAsString(item)).isEqualTo(raw);assertThat(item.eventEvidence()).isNull();var input=new LineInput(UUID.randomUUID(),1,"26","26",true,false,List.of(item),"");assertThat(EffectivePayGovernance.inputEconomic(input).toString()).doesNotContain("eventEvidence");}
 @Test void evidenceIsEconomicAndLabelsCannotReclassify(){var e=evidence("AW",LocalDate.of(2026,1,1),LocalDate.of(2026,10,31),LocalDate.of(2026,10,31));assertThat(WageEvents.validate(e,"wage_event")).isEqualTo(e);var a=new LineInput(UUID.randomUUID(),1,"26","26",true,false,List.of(new Item("A","wage_event","100",null,null,null,e)),"");var changed=new WageEvents.Evidence(e.eventId(),e.businessType(),e.classification(),e.serviceFrom(),e.serviceTo(),e.decisionDate(),e.contractualPayableDate(),e.contributionMonth(),e.settlementMonth(),"different contract",e.classificationReason());var b=new LineInput(a.employeeId(),1,"26","26",true,false,List.of(new Item("A","wage_event","100",null,null,null,changed)),"");assertThat(EffectivePayGovernance.inputEconomic(a)).isNotEqualTo(EffectivePayGovernance.inputEconomic(b));}
 @Test void classificationAndPeriodMustMatchFactsAndRecurringEventsAreRejected(){var wrong=evidence("AW",LocalDate.of(2026,10,1),LocalDate.of(2026,10,31),LocalDate.of(2026,11,10));assertThatThrownBy(()->WageEvents.validate(wrong,"wage_event")).hasMessage("WAGE_CLASSIFICATION_EVIDENCE_MISSING");assertThatThrownBy(()->WageEvents.validate(null,"wage_event")).hasMessage("WAGE_CLASSIFICATION_EVIDENCE_MISSING");var e=evidence("AW",LocalDate.of(2026,1,1),LocalDate.of(2026,10,31),LocalDate.of(2026,10,31));assertThatThrownBy(()->PayrollRules.normalize(new Rule("monthly","1000","8","44",false,"1.5",List.of(new Item("bonus","wage_event","100",null,null,null,e))))).hasMessage("WORKFORCE_INVALID");}
}
