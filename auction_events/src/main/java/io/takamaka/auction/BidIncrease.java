/*
    A blind auction event example in Takamaka.
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

package io.takamaka.auction;

import java.math.BigInteger;

import io.takamaka.code.lang.FromContract;
import io.takamaka.code.lang.Event;
import io.takamaka.code.lang.PayableContract;
import io.takamaka.code.lang.View;

public class BidIncrease extends Event {
  public final PayableContract bidder;
  public final BigInteger amount;

  @FromContract BidIncrease(PayableContract bidder, BigInteger amount) {
    this.bidder = bidder;
    this.amount = amount;
  }

  public @View PayableContract getBidder() {
    return bidder;
  }

  public @View BigInteger getAmount() {
    return amount;
  }
}