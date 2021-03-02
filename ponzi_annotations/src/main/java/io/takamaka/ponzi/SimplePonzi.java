package io.takamaka.ponzi;

import static io.takamaka.code.lang.Takamaka.require;

import java.math.BigInteger;

import io.takamaka.code.lang.Contract;
import io.takamaka.code.lang.FromContract;
import io.takamaka.code.lang.Payable;

public class SimplePonzi extends Contract {
  private final BigInteger _10 = BigInteger.valueOf(10L);
  private final BigInteger _11 = BigInteger.valueOf(11L);
  private Contract currentInvestor;
  private BigInteger currentInvestment = BigInteger.ZERO;

  public @Payable @FromContract void invest(BigInteger amount) {
    // new investments must be at least 10% greater than current
    BigInteger minimumInvestment = currentInvestment.multiply(_11).divide(_10);
    require(amount.compareTo(minimumInvestment) >= 0,
      () -> "you must invest at least " + minimumInvestment);

    // document new investor
    currentInvestor = caller();
    currentInvestment = amount;
  }
}