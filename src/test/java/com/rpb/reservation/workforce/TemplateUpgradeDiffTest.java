package com.rpb.reservation.workforce;
import static org.assertj.core.api.Assertions.*;
import com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import com.rpb.reservation.workforce.payroll.governance.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class TemplateUpgradeDiffTest {
 Rule rule(String rate,List<Item> items){return new Rule("monthly",rate,"8.00","44.00",false,"1.50",items);}
 @Test void conflictingEconomicFieldIsExplicitAndNotMerged(){var diff=TemplateUpgradeDiff.compare(rule("3000.00",List.of()),rule("3500.00",List.of()),rule("4000.00",List.of()));assertThat(diff).hasSize(1);assertThat(diff.getFirst().path()).isEqualTo("rate");assertThat(diff.getFirst().label()).isEqualTo("基本工资标准");assertThat(diff.getFirst().conflict()).isTrue();}
 @Test void missingItemIdentityIsConservativeWholeListConflict(){var a=new Item("A","ow_allowance","10.00");var b=new Item("A","ow_allowance","20.00");var c=new Item("B","ow_allowance","30.00");var diff=TemplateUpgradeDiff.compare(rule("3000.00",List.of(a)),rule("3000.00",List.of(b)),rule("3000.00",List.of(c)));assertThat(diff).hasSize(1);assertThat(diff.getFirst().path()).isEqualTo("items");assertThat(diff.getFirst().conflict()).isTrue();}
 @Test void stableIdsAvoidArrayIndexMatching(){UUID id=UUID.randomUUID(),other=UUID.randomUUID();var a=new Item("A","ow_allowance","10.00","full",id,null);var b=new Item("B","ow_allowance","20.00","full",other,null);var changed=new Item("A","ow_allowance","15.00","full",id,null);var diff=TemplateUpgradeDiff.compare(rule("3000.00",List.of(a,b)),rule("3000.00",List.of(b,changed)),rule("3000.00",List.of(a,b)));assertThat(diff).hasSize(1);assertThat(diff.getFirst().path()).isEqualTo("items."+id+".amount");assertThat(diff.getFirst().conflict()).isFalse();}
 @Test void duplicateIdsCannotSilentlyDropItems(){UUID id=UUID.randomUUID();var a=new Item("A","ow_allowance","10.00","full",id,null);var b=new Item("B","ow_allowance","20.00","full",id,null);var diff=TemplateUpgradeDiff.compare(rule("1.00",List.of(a)),rule("1.00",List.of(a,b)),rule("1.00",List.of(b)));assertThat(diff.getFirst().path()).isEqualTo("items");assertThat(diff.getFirst().conflict()).isTrue();}
 @Test void itemAmountLabelIdentifiesTheHumanItemInsteadOfOnlyUuid(){UUID id=UUID.randomUUID();var before=new Item("交通津贴","ow_allowance","10.00","full",id,null);var after=new Item("交通津贴","ow_allowance","15.00","full",id,null);var diff=TemplateUpgradeDiff.compare(rule("3000.00",List.of(before)),rule("3000.00",List.of(before)),rule("3000.00",List.of(after)));assertThat(diff.getFirst().label()).contains("交通津贴","金额");}
}
