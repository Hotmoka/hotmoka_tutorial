package io.takamaka.ponzi;

import static io.takamaka.code.lang.Takamaka.require;

import java.math.BigInteger;

import io.takamaka.code.lang.Contract;
import io.takamaka.code.lang.FromContract;
import io.takamaka.code.lang.Payable;
import io.takamaka.code.lang.PayableContract;
import io.takamaka.code.util.StorageLinkedList;
import io.takamaka.code.util.StorageList;

public class GradualPonzi extends Contract {
  public final BigInteger MINIMUM_INVESTMENT = BigInteger.valueOf(1_000L);

  /**
   * All investors up to now. This list might contain the same investor many times,
   * which is important to pay him back more than investors who only invested once.
   */
  private final StorageList<PayableContract> investors = new StorageLinkedList<>();

  public @FromContract(PayableContract.class) GradualPonzi() {
    investors.add((PayableContract) caller());
  }

  public @Payable @FromContract(PayableContract.class) void invest(BigInteger amount) {
    require(amount.compareTo(MINIMUM_INVESTMENT) >= 0,
      () -> "you must invest at least " + MINIMUM_INVESTMENT);
    BigInteger eachInvestorGets = amount.divide(BigInteger.valueOf(investors.size()));
    investors.stream().forEachOrdered(investor -> investor.receive(eachInvestorGets));
    investors.add((PayableContract) caller());
  }
}