/*
    A blind auction smart contract example in Takamaka.
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

import static io.takamaka.code.lang.Takamaka.now;
import static io.takamaka.code.lang.Takamaka.require;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.function.Supplier;

import io.takamaka.code.lang.Contract;
import io.takamaka.code.lang.Exported;
import io.takamaka.code.lang.FromContract;
import io.takamaka.code.lang.Payable;
import io.takamaka.code.lang.PayableContract;
import io.takamaka.code.lang.Storage;
import io.takamaka.code.util.Bytes32Snapshot;
import io.takamaka.code.util.StorageLinkedList;
import io.takamaka.code.util.StorageList;
import io.takamaka.code.util.StorageMap;
import io.takamaka.code.util.StorageTreeMap;

/**
 * A contract for a simple auction. This class is derived from the Solidity code at
 * https://solidity.readthedocs.io/en/v0.5.9/solidity-by-example.html#id2
 * In this contract, bidders place bids together with a hash. At the end of
 * the bidding period, bidders are expected to reveal if and which of their bids
 * were real and their actual value. Fake bids are refunded. Real bids are compared
 * and the bidder with the highest bid wins.
 */
public class BlindAuction extends Contract {

  /**
   * A bid placed by a bidder. The deposit has been paid in full.
   * If, later, the bid will be revealed as fake, then the deposit will
   * be fully refunded. If, instead, the bid will be revealed as real, but for
   * a lower amount, then only the difference will be refunded.
   */
  private static class Bid extends Storage {

    /**
     * The hash that will be regenerated and compared at reveal time.
     */
    private final Bytes32Snapshot hash;

    /**
      * The value of the bid. Its real value might be lower and known
      * at real time only.
      */
    private final BigInteger deposit;

    private Bid(Bytes32Snapshot hash, BigInteger deposit) {
      this.hash = hash;
      this.deposit = deposit;
    }

    /**
     * Recomputes the hash of a bid at reveal time and compares it
     * against the hash provided at bidding time. If they match,
     * we can reasonably trust the bid.
     * 
     * @param revealed the revealed bid
     * @param digest the hasher
     * @return true if and only if the hashes match
     */
    private boolean matches(RevealedBid revealed, MessageDigest digest) {
      digest.update(revealed.value.toByteArray());
      digest.update(revealed.fake ? (byte) 0 : (byte) 1);
      digest.update(revealed.salt.toArray());
      return Arrays.equals(hash.toArray(), digest.digest());
    }
  }

  /**
   * A bid revealed by a bidder at reveal time. The bidder shows
   * if the corresponding bid was fake or real, and how much was the
   * actual value of the bid. This might be lower than previously communicated.
   */
  @Exported
  public static class RevealedBid extends Storage {
    private final BigInteger value;
    private final boolean fake;

    /**
     * The salt used to strengthen the hashing.
     */
    private final Bytes32Snapshot salt;

    public RevealedBid(BigInteger value, boolean fake, Bytes32Snapshot salt) {
      this.value = value;
      this.fake = fake;
      this.salt = salt;
    }
  }

  /**
   * The beneficiary that, at the end of the reveal time, will receive the highest bid.
   */
  private final PayableContract beneficiary;

  /**
   * The bids for each bidder. A bidder might place more bids.
   */
  private final StorageMap<PayableContract, StorageList<Bid>> bids = new StorageTreeMap<>();

  /**
   * The time when the bidding time ends.
   */
  private final long biddingEnd;

  /**
   * The time when the reveal time ends.
   */
  private final long revealEnd;

  /**
   * The bidder with the highest bid, at reveal time.
   */
  private PayableContract highestBidder;

  /**
   * The highest bid, at reveal time.
   */
  private BigInteger highestBid;

  /**
   * Creates a blind auction contract.
   * 
   * @param biddingTime the length of the bidding time
   * @param revealTime the length of the reveal time
   */
  public @FromContract(PayableContract.class) BlindAuction(int biddingTime, int revealTime) {
    require(biddingTime > 0, "Bidding time must be positive");
    require(revealTime > 0, "Reveal time must be positive");

    this.beneficiary = (PayableContract) caller();
    this.biddingEnd = now() + biddingTime;
    this.revealEnd = biddingEnd + revealTime;
  }

  /**
   * Places a blinded bid with the given hash.
   * The money sent is only refunded if the bid is correctly
   * revealed in the revealing phase. The bid is valid if the
   * money sent together with the bid is at least "value" and
   * "fake" is not true. Setting "fake" to true and sending
   * not the exact amount are ways to hide the real bid but
   * still make the required deposit. The same bidder can place multiple bids.
   */
  public @Payable @FromContract(PayableContract.class) void bid
      (BigInteger amount, Bytes32Snapshot hash) {

    onlyBefore(biddingEnd);
    bids.computeIfAbsent((PayableContract) caller(), (Supplier<StorageList<Bid>>) StorageLinkedList::new)
      .add(new Bid(hash, amount));
  }

  /**
   * Reveals a bid of the caller. The caller will get a refund for all correctly
   * blinded invalid bids and for all bids except for the totally highest.
   * 
   * @param revealed the revealed bid
   * @throws NoSuchAlgorithmException if the hashing algorithm is not available
   */
  public @FromContract(PayableContract.class) void reveal
      (RevealedBid revealed) throws NoSuchAlgorithmException {

    onlyAfter(biddingEnd);
    onlyBefore(revealEnd);
    var bidder = (PayableContract) caller();
    StorageList<Bid> bids = this.bids.get(bidder);
    require(bids != null && bids.size() > 0, "No bids to reveal");
    require(revealed != null, () -> "The revealed bid cannot be null");

    // any other hashing algorithm will do, as long as
    // both bidder and auction contract use the same
    var digest = MessageDigest.getInstance("SHA-256");
    // by removing the head of the list, it makes it impossible
    // for the caller to re-claim the same deposits
    bidder.receive(refundFor(bidder, bids.removeFirst(), revealed, digest));
  }

  /**
   * Ends the auction and sends the highest bid to the beneficiary.
   * 
   * @return the highest bidder
   */
  public PayableContract auctionEnd() {
    onlyAfter(revealEnd);
    PayableContract winner = highestBidder;
        
    if (winner != null) {
      beneficiary.receive(highestBid);
      //event(new AuctionEnd(winner, highestBid)); // doesn't compile yet
      highestBidder = null;
    }

    return winner;
  }

  /**
   * Checks how much of the deposit should be refunded for a given bid.
   * 
   * @param bidder the bidder that placed the bid
   * @param bid the bid, as was placed at bidding time
   * @param revealed the bid, as was revealed later
   * @param digest the hashing algorithm
   * @return the amount to refund
   */
  private BigInteger refundFor(PayableContract bidder, Bid bid,
      RevealedBid revealed, MessageDigest digest) {

    if (!bid.matches(revealed, digest))
      // the bid was not actually revealed: no refund
      return BigInteger.ZERO;
    else if (!revealed.fake && bid.deposit.compareTo(revealed.value) >= 0
        && placeBid(bidder, revealed.value))
      // the bid was correctly revealed and is the best up to now:
      // only the difference between promised and provided is refunded;
      // the rest might be refunded later if a better bid will be revealed
      return bid.deposit.subtract(revealed.value);
    else
      // the bid was correctly revealed and is not the best one:
      // it is fully refunded
      return bid.deposit;
  }

  /**
   * Takes note that a bidder has correctly revealed a bid for the given value.
   * 
   * @param bidder the bidder
   * @param value the value, as revealed
   * @return true if and only if this is the best bid, up to now
   */
  private boolean placeBid(PayableContract bidder, BigInteger value) {
    if (highestBid != null && value.compareTo(highestBid) <= 0)
      // this is not the best bid seen so far
      return false;

    // if there was a best bidder already, its bid is refunded
    if (highestBidder != null)
      // Refund the previously highest bidder
      highestBidder.receive(highestBid);

    // take note that this is the best bid up to now
    highestBid = value;
    highestBidder = bidder;
    //event(new BidIncrease(bidder, value));  // doesn't compile yet

    return true;
  }

  private static void onlyBefore(long when) {
    require(now() < when, "Too late");
  }

  private static void onlyAfter(long when) {
    require(now() > when, "Too early");
  }
}