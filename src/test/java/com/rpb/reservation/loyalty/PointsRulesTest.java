package com.rpb.reservation.loyalty;
import com.rpb.reservation.loyalty.domain.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class PointsRulesTest {
 @Test void grantDeductAndReversalNeverOverdrawOrOverflow(){
  assertThat(PointsRules.balance(100,30,false)).isEqualTo(70);
  assertThat(PointsRules.balance(70,30,true)).isEqualTo(100);
  assertThatThrownBy(()->PointsRules.balance(10,11,false)).isInstanceOf(PointsException.class).hasMessageContaining("不足");
  assertThatThrownBy(()->PointsRules.balance(Integer.MAX_VALUE,1,true)).isInstanceOf(PointsException.class);
 }
 @Test void quantitiesMustBePositiveAndVersionsExact(){
  assertThatThrownBy(()->PointsRules.balance(0,0,true)).isInstanceOf(PointsException.class);
  assertThatThrownBy(()->PointsRules.version(2,1)).isInstanceOf(PointsException.class);
  assertThatThrownBy(()->PointsRules.version(0,null)).isInstanceOf(PointsException.class);
  PointsRules.version(2,2);
 }
}
