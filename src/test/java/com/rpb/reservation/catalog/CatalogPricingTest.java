package com.rpb.reservation.catalog;

import com.rpb.reservation.catalog.pricing.TaxCalculator;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CatalogPricingTest {
 @Test void exclusiveAndInclusivePricesConserveCents() {
  var exclusive=TaxCalculator.calculate(10000,1,"exclusive",new BigDecimal("0.10"));
  assertThat(exclusive.netCents()).isEqualTo(10000);assertThat(exclusive.taxCents()).isEqualTo(1000);assertThat(exclusive.grossCents()).isEqualTo(11000);
  var inclusive=TaxCalculator.calculate(11000,1,"inclusive",new BigDecimal("0.10"));
  assertThat(inclusive).isEqualTo(exclusive);
 }
 @Test void roundingAndFreeProductsAreDeterministic() {
  var q=TaxCalculator.calculate(5,1,"exclusive",new BigDecimal("0.10"));assertThat(q.taxCents()).isEqualTo(1);assertThat(q.grossCents()).isEqualTo(6);
  assertThat(TaxCalculator.calculate(0,3,"inclusive",BigDecimal.ZERO).grossCents()).isZero();
  assertThatThrownBy(()->TaxCalculator.calculate(-1,1,"exclusive",BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
 }
}
