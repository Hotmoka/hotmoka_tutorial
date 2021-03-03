/*
    A smart contract example in Takamaka.
    Copyright (C) 2021 Fausto Spoto (fausto.spoto@gmail.com)

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package io.takamaka.redgreen;

import java.math.BigInteger;

import io.takamaka.code.lang.FromContract;
import io.takamaka.code.lang.Payable;
import io.takamaka.code.lang.RedGreenContract;
import io.takamaka.code.lang.RedGreenPayableContract;
import io.takamaka.code.lang.RedPayable;
import io.takamaka.code.util.StorageLinkedList;
import io.takamaka.code.util.StorageList;

public class Distributor extends RedGreenContract {
  private final StorageList<RedGreenPayableContract> payees = new StorageLinkedList<>();
  private final RedGreenPayableContract owner;

  public @FromContract(RedGreenPayableContract.class) Distributor() {
    owner = (RedGreenPayableContract) caller();
  }

  public @FromContract(RedGreenPayableContract.class) void addAsPayee() {
    payees.add((RedGreenPayableContract) caller());
  }

  public @Payable @FromContract void distributeGreen(BigInteger amount) {
    int size = payees.size();
    if (size > 0) {
      BigInteger eachGets = amount.divide(BigInteger.valueOf(size));
      payees.forEach(payee -> payee.receive(eachGets));
      owner.receive(balance());
    }
  }

  public @RedPayable @FromContract void distributeRed(BigInteger amount) {
    int size = payees.size();
    if (size > 0) {
      BigInteger eachGets = amount.divide(BigInteger.valueOf(size));
      payees.forEach(payee -> payee.receiveRed(eachGets));
      owner.receiveRed(balanceRed());
    }
  }
}