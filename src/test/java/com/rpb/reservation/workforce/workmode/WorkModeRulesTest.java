package com.rpb.reservation.workforce.workmode;
import static org.assertj.core.api.Assertions.*;
import static com.rpb.reservation.workforce.workmode.WorkModeDtos.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.time.*;
import org.junit.jupiter.api.Test;
class WorkModeRulesTest {
 final Instant now=Instant.parse("2026-10-29T01:00:00Z");
 final Point point=new Point("Office",0d,0d,100d);
 Location location(double lat,double lon,double accuracy,Instant at){return new Location(lat,lon,accuracy,at,"");}
 @Test void verifiesDistanceFreshnessAndAccuracyOnServer(){WorkModeRules.locate(point,location(0,0,10,now),now);assertThat(WorkModeRules.distance(0,0,0,0.001)).isBetween(110d,112d);assertThatThrownBy(()->WorkModeRules.locate(point,location(0,0.01,10,now),now)).extracting("code").isEqualTo("WORKFORCE_LOCATION_OUTSIDE");assertThatThrownBy(()->WorkModeRules.locate(point,location(0,0,150,now),now)).extracting("code").isEqualTo("WORKFORCE_LOCATION_ACCURACY");assertThatThrownBy(()->WorkModeRules.locate(point,location(0,0,10,now.minusSeconds(121)),now)).extracting("code").isEqualTo("WORKFORCE_LOCATION_STALE");}
 @Test void rejectsNonFiniteCoordinatesAndMissingEvidence(){assertThatThrownBy(()->WorkModeRules.point(new Point("Office",Double.NaN,0d,100d))).isInstanceOf(WorkforceException.class);assertThatThrownBy(()->WorkModeRules.point(new Point("Office",91d,0d,100d))).isInstanceOf(WorkforceException.class);assertThatThrownBy(()->WorkModeRules.locate(point,null,now)).extracting("code").isEqualTo("WORKFORCE_LOCATION_REQUIRED");assertThatThrownBy(()->WorkModeRules.locate(point,location(0,0,10,now.plusSeconds(31)),now)).extracting("code").isEqualTo("WORKFORCE_LOCATION_STALE");}
 @Test void recognizesOnlyFourNewModesAndLegacy(){for(var mode:new String[]{"onsite","field","home","free","legacy"})assertThat(WorkModeRules.mode(mode)).isEqualTo(mode);assertThatThrownBy(()->WorkModeRules.mode("unknown")).isInstanceOf(WorkforceException.class);}
}
