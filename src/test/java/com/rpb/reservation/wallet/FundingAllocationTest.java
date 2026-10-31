package com.rpb.reservation.wallet;
import com.rpb.reservation.wallet.funding.domain.FundingAllocation;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class FundingAllocationTest {
 @Test void bothPoliciesAreExact(){assertThat(FundingAllocation.debit("proportional",10000,1000,0,0,10000,1000,5500)).isEqualTo(new FundingAllocation.Split(5000,500));assertThat(FundingAllocation.debit("principal_first",10000,1000,0,0,10000,1000,5500)).isEqualTo(new FundingAllocation.Split(5500,0));}
 @Test void elevenThousandOneCentPurchasesExhaustBothComponentsWithoutBias(){long p=10000,g=1000,sp=0,sg=0;for(int i=0;i<11000;i++){var s=FundingAllocation.debit("proportional",10000,1000,sp,sg,p,g,1);p-=s.principal();g-=s.bonus();sp+=s.principal();sg+=s.bonus();assertThat(p).isNotNegative();assertThat(g).isNotNegative();}assertThat(p+g).isZero();assertThat(sp).isEqualTo(10000);assertThat(sg).isEqualTo(1000);}
 @Test void partialRefundsRestoreTheOriginalSplitExactly(){long p=0,g=0;for(int i=0;i<5500;i++){var s=FundingAllocation.refund(5000,500,p,g,1);p+=s.principal();g+=s.bonus();}assertThat(p).isEqualTo(5000);assertThat(g).isEqualTo(500);}
 @Test void multiplicationDoesNotOverflowLong(){var s=FundingAllocation.debit("proportional",9000000000000L,999999999999L,0,0,9000000000000L,999999999999L,9999999999999L);assertThat(s.principal()).isEqualTo(9000000000000L);}
 @Test void cannotOverdrawOrInventRefunds(){assertThatThrownBy(()->FundingAllocation.debit("proportional",100,10,0,0,100,10,111)).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->FundingAllocation.refund(100,10,100,10,1)).isInstanceOf(IllegalArgumentException.class);}
}
