package io.takamaka.auction;

import static io.hotmoka.beans.Coin.panarea;
import static io.hotmoka.beans.types.BasicTypes.BOOLEAN;
import static io.hotmoka.beans.types.BasicTypes.BYTE;
import static io.hotmoka.beans.types.BasicTypes.INT;
import static io.hotmoka.beans.types.ClassType.BIG_INTEGER;
import static io.hotmoka.beans.types.ClassType.BYTES32_SNAPSHOT;
import static io.hotmoka.beans.types.ClassType.PAYABLE_CONTRACT;
import static java.math.BigInteger.ONE;
import static java.math.BigInteger.ZERO;

import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import io.hotmoka.beans.references.TransactionReference;
import io.hotmoka.beans.requests.ConstructorCallTransactionRequest;
import io.hotmoka.beans.requests.InstanceMethodCallTransactionRequest;
import io.hotmoka.beans.requests.SignedTransactionRequest;
import io.hotmoka.beans.requests.SignedTransactionRequest.Signer;
import io.hotmoka.beans.signatures.ConstructorSignature;
import io.hotmoka.beans.signatures.MethodSignature;
import io.hotmoka.beans.signatures.NonVoidMethodSignature;
import io.hotmoka.beans.signatures.VoidMethodSignature;
import io.hotmoka.beans.types.ClassType;
import io.hotmoka.beans.values.BigIntegerValue;
import io.hotmoka.beans.values.BooleanValue;
import io.hotmoka.beans.values.ByteValue;
import io.hotmoka.beans.values.IntValue;
import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.beans.values.StorageValue;
import io.hotmoka.crypto.SignatureAlgorithm;
import io.hotmoka.memory.MemoryBlockchain;
import io.hotmoka.memory.MemoryBlockchainConfig;
import io.hotmoka.nodes.ConsensusParams;
import io.hotmoka.nodes.GasHelper;
import io.hotmoka.nodes.Node;
import io.hotmoka.nodes.Node.Subscription;
import io.hotmoka.nodes.views.InitializedNode;
import io.hotmoka.nodes.views.NodeWithAccounts;
import io.hotmoka.nodes.views.NodeWithJars;

/**
 * Go inside the hotmoka project, run
 * 
 * . set_variables.sh
 * 
 * then move inside this project and run
 * 
 * mvn clean package
 * java --module-path $explicit:$automatic:target/blockchain11-0.0.1-SNAPSHOT.jar -classpath $unnamed"/*" --module blockchain/io.takamaka.auction.Main
 */
public class Main {
  public final static int NUM_BIDS = 40; // number of bids placed
  public final static int BIDDING_TIME = 40_000; // in milliseconds
  public final static int REVEAL_TIME = 70_000; // in milliseconds

  private final static BigInteger _10_000 = BigInteger.valueOf(10_000);
  private final static BigInteger _1_000_000_000 = BigInteger.valueOf(1_000_000_000);
  private final static BigInteger _10_000_000_000_000 =
    _1_000_000_000.multiply(_10_000);
  public final static BigInteger GREEN_AMOUNT = _1_000_000_000.multiply(_1_000_000_000);
  public final static BigInteger RED_AMOUNT = ZERO;
  private final static BigInteger _100_000 = BigInteger.valueOf(100_000);

  // useful constants that refer to classes, constructors or methods
  private final static ClassType BLIND_AUCTION
    = new ClassType("io.takamaka.auction.BlindAuction");
  private final static ConstructorSignature CONSTRUCTOR_BLIND_AUCTION
    = new ConstructorSignature(BLIND_AUCTION, INT, INT);
  private final static ConstructorSignature CONSTRUCTOR_BYTES32_SNAPSHOT
    = new ConstructorSignature(BYTES32_SNAPSHOT,
     BYTE, BYTE, BYTE, BYTE, BYTE, BYTE, BYTE, BYTE,
     BYTE, BYTE, BYTE, BYTE, BYTE, BYTE, BYTE, BYTE,
     BYTE, BYTE, BYTE, BYTE, BYTE, BYTE, BYTE, BYTE,
     BYTE, BYTE, BYTE, BYTE, BYTE, BYTE, BYTE, BYTE);
  private final static ConstructorSignature CONSTRUCTOR_REVEALED_BID
    = new ConstructorSignature(
        new ClassType("io.takamaka.auction.BlindAuction$RevealedBid"),
        BIG_INTEGER, BOOLEAN, BYTES32_SNAPSHOT);
  private final static MethodSignature BID = new VoidMethodSignature
    (BLIND_AUCTION, "bid", BIG_INTEGER, BYTES32_SNAPSHOT);
  private final static MethodSignature REVEAL = new VoidMethodSignature
    (BLIND_AUCTION, "reveal", new ClassType("io.takamaka.auction.BlindAuction$RevealedBid"));
  private final static MethodSignature AUCTION_END = new NonVoidMethodSignature
    (BLIND_AUCTION, "auctionEnd", PAYABLE_CONTRACT);

  //the hashing algorithm used to hide the bids
  private final MessageDigest digest = MessageDigest.getInstance("SHA-256");

  private final long start;  // the time when bids started being placed
  private final NodeWithAccounts node;
  private final TransactionReference classpath;
  private final Signer[] signers = new Signer[4];
  private final BigInteger[] nonces = { ZERO, ZERO, ZERO, ZERO };
  private final StorageReference auction;
  private final List<BidToReveal> bids = new ArrayList<>();
  private final GasHelper gasHelper;

  public static void main(String[] args) throws Exception {
	MemoryBlockchainConfig config = new MemoryBlockchainConfig.Builder().build();
	ConsensusParams consensus = new ConsensusParams.Builder().build();

	try (Node emptyNode = MemoryBlockchain.init(config, consensus)) {
		new Main(emptyNode, consensus);
	}
  }

  /**
   * Class used to keep in memory the bids placed by each player,
   * that will be revealed at the end.
   */
  private class BidToReveal {
    private final int player;
    private final BigInteger value;
    private final boolean fake;
    private final byte[] salt;

    private BidToReveal(int player, BigInteger value, boolean fake, byte[] salt) {
      this.player = player;
      this.value = value;
      this.fake = fake;
      this.salt = salt;
    }

    /**
     * Creates in store a revealed bid corresponding to this object.
     * 
     * @return the storage reference to the freshly created revealed bid
     */
    private StorageReference intoBlockchain() throws Exception {
      StorageReference bytes32 = node.addConstructorCallTransaction(new ConstructorCallTransactionRequest
        (signers[player], node.account(player),
        getNonceAndIncrement(player), "",_100_000,
        panarea(gasHelper.getSafeGasPrice()), classpath, CONSTRUCTOR_BYTES32_SNAPSHOT,
    	new ByteValue(salt[0]), new ByteValue(salt[1]), new ByteValue(salt[2]), new ByteValue(salt[3]),
    	new ByteValue(salt[4]), new ByteValue(salt[5]), new ByteValue(salt[6]), new ByteValue(salt[7]),
    	new ByteValue(salt[8]), new ByteValue(salt[9]), new ByteValue(salt[10]), new ByteValue(salt[11]),
    	new ByteValue(salt[12]), new ByteValue(salt[13]), new ByteValue(salt[14]), new ByteValue(salt[15]),
    	new ByteValue(salt[16]), new ByteValue(salt[17]), new ByteValue(salt[18]), new ByteValue(salt[19]),
    	new ByteValue(salt[20]), new ByteValue(salt[21]), new ByteValue(salt[22]), new ByteValue(salt[23]),
    	new ByteValue(salt[24]), new ByteValue(salt[25]), new ByteValue(salt[26]), new ByteValue(salt[27]),
    	new ByteValue(salt[28]), new ByteValue(salt[29]), new ByteValue(salt[30]), new ByteValue(salt[31])));

      return node.addConstructorCallTransaction(new ConstructorCallTransactionRequest
        (signers[player], node.account(player),
         getNonceAndIncrement(player), "",
         _10_000, panarea(gasHelper.getSafeGasPrice()), classpath, CONSTRUCTOR_REVEALED_BID,
         new BigIntegerValue(value), new BooleanValue(fake), bytes32));
    }
  }

  private Main(Node emptyNode, ConsensusParams consensus) throws Exception {
    Path takamakaCodePath = Paths.get
      ("../../hotmoka/modules/explicit/io-takamaka-code-1.0.0.jar");
    Path auctionPath = Paths.get("../auction_events/target/auction_events-0.0.1-SNAPSHOT.jar");
    InitializedNode initialized = InitializedNode.of
      (emptyNode, consensus, takamakaCodePath, GREEN_AMOUNT, RED_AMOUNT);
    NodeWithJars nodeWithJars = NodeWithJars.of
      (emptyNode, initialized.gamete(), initialized.keysOfGamete().getPrivate(),
       auctionPath);
    this.node = NodeWithAccounts.of
      (emptyNode, initialized.gamete(), initialized.keysOfGamete().getPrivate(),
       _10_000_000_000_000, _10_000_000_000_000, _10_000_000_000_000, _10_000_000_000_000);

    SignatureAlgorithm<SignedTransactionRequest> signature
      = emptyNode.getSignatureAlgorithmForRequests();
    this.gasHelper = new GasHelper(node);

    for (int pos = 0; pos < 4; pos++)
      signers[pos] = Signer.with(signature, node.privateKey(pos));

    this.classpath = nodeWithJars.jar(0);

    // create the auction contract in the store of the node
    this.auction = node.addConstructorCallTransaction
      (new ConstructorCallTransactionRequest(signers[0], node.account(0),
       getNonceAndIncrement(0), "", _10_000, panarea(gasHelper.getSafeGasPrice()),
       classpath, CONSTRUCTOR_BLIND_AUCTION,
       new IntValue(BIDDING_TIME), new IntValue(REVEAL_TIME)));

    this.start = System.currentTimeMillis();

    try (Subscription subscription = emptyNode.subscribeToEvents(auction,
       (creator, event) -> System.out.println
         ("Seen event of class " + node.getClassTag(event).clazz.name
          + " created by contract " + creator))) {
      StorageReference expectedWinner = placeBids();
      waitUntilEndOfBiddingTime();
      revealBids();
      waitUntilEndOfRevealTime();
      StorageValue winner = askForWinner();

      // show that the contract computes the correct winner
      System.out.println("expected winner: " + expectedWinner);
      System.out.println("actual winner: " + winner);
    }
  }

private StorageReference placeBids() throws Exception {
    BigInteger maxBid = BigInteger.ZERO;
    StorageReference expectedWinner = null;
    Random random = new Random();

    int i = 1;
    while (i <= NUM_BIDS) { // generate NUM_BIDS random bids
      int player = 1 + random.nextInt(3);
      BigInteger deposit = BigInteger.valueOf(random.nextInt(1000));
      BigInteger value = BigInteger.valueOf(random.nextInt(1000));
      boolean fake = random.nextBoolean();
      byte[] salt = new byte[32];
      random.nextBytes(salt); // random 32 bytes of salt for each bid

      // create a Bytes32 hash of the bid in the store of the node
      StorageReference bytes32 = codeAsBytes32(player, value, fake, salt);

      // keep note of the best bid, to verify the result at the end
      if (!fake && deposit.compareTo(value) >= 0)
        if (expectedWinner == null || value.compareTo(maxBid) > 0) {
          maxBid = value;
          expectedWinner = node.account(player);
        }
        else if (value.equals(maxBid))
          // we do not allow ex aequos, since the winner
          // would depend on the fastest player to reveal
          continue;

      // keep the explicit bid in memory, not yet in the node,
      // since it would be visible there
      bids.add(new BidToReveal(player, value, fake, salt));

      // place a hashed bid in the node
      node.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
        (signers[player], node.account(player),
         getNonceAndIncrement(player), "",
         _100_000, panarea(gasHelper.getSafeGasPrice()), classpath, BID,
         auction, new BigIntegerValue(deposit), bytes32));

      i++;
    }

    return expectedWinner;
  }

  private void revealBids() throws Exception {
    // we create the revealed bids in blockchain; this is safe now, since the bidding time is over
    List<StorageReference> bidsInStore = new ArrayList<>();
    for (BidToReveal bid: bids)
      bidsInStore.add(bid.intoBlockchain());

    Iterator<BidToReveal> it = bids.iterator();
    for (StorageReference bidInStore: bidsInStore) {
      int player = it.next().player;
      node.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
        (signers[player], node.account(player),
        getNonceAndIncrement(player), "", _10_000,
        panarea(gasHelper.getSafeGasPrice()),
        classpath, REVEAL, auction, bidInStore));
    }
  }

  private StorageReference askForWinner() throws Exception {
    StorageValue winner = node.addInstanceMethodCallTransaction
      (new InstanceMethodCallTransactionRequest
      (signers[0], node.account(0), getNonceAndIncrement(0),
      "", _10_000, panarea(gasHelper.getSafeGasPrice()),
      classpath, AUCTION_END, auction));

    // the winner is normally a StorageReference,
    // but it could be a NullValue if all bids were fake
    return winner instanceof StorageReference ? (StorageReference) winner : null;
  }

  private void waitUntilEndOfBiddingTime() {
     waitUntil(BIDDING_TIME + 5000);
  }

  private void waitUntilEndOfRevealTime() {
    waitUntil(BIDDING_TIME + REVEAL_TIME + 5000);
  }

  /**
   * Waits until a specific time after start.
   */
  private void waitUntil(long duration) {
    try {
      Thread.sleep(start + duration - System.currentTimeMillis());
    }
    catch (InterruptedException e) {}
  }

  /**
   * Yields the nonce of the given player and increments it.
   */
  private BigInteger getNonceAndIncrement(int player) {
    BigInteger nonce = nonces[player];
    nonces[player] = nonce.add(ONE);
    return nonce;
  }

  /**
   * Hashes a bid and put it in the store of the node, in hashed form.
   */
  private StorageReference codeAsBytes32
      (int player, BigInteger value, boolean fake, byte[] salt)
      throws Exception {

    digest.reset();
    digest.update(value.toByteArray());
    digest.update(fake ? (byte) 0 : (byte) 1);
    digest.update(salt);
    byte[] hash = digest.digest();
    return createBytes32(player, hash);
  }

  /**
   * Creates a Bytes32Snapshot object in the store of the node.
   */
  private StorageReference createBytes32(int player, byte[] hash) throws Exception {
    return node.addConstructorCallTransaction
      (new ConstructorCallTransactionRequest(
        signers[player],
        node.account(player),
        getNonceAndIncrement(player), "",
        _100_000, panarea(gasHelper.getSafeGasPrice()),
        classpath, CONSTRUCTOR_BYTES32_SNAPSHOT,
        new ByteValue(hash[0]), new ByteValue(hash[1]),
        new ByteValue(hash[2]), new ByteValue(hash[3]),
        new ByteValue(hash[4]), new ByteValue(hash[5]),
        new ByteValue(hash[6]), new ByteValue(hash[7]),
        new ByteValue(hash[8]), new ByteValue(hash[9]),
        new ByteValue(hash[10]), new ByteValue(hash[11]),
        new ByteValue(hash[12]), new ByteValue(hash[13]),
        new ByteValue(hash[14]), new ByteValue(hash[15]),
        new ByteValue(hash[16]), new ByteValue(hash[17]),
        new ByteValue(hash[18]), new ByteValue(hash[19]),
        new ByteValue(hash[20]), new ByteValue(hash[21]),
        new ByteValue(hash[22]), new ByteValue(hash[23]),
        new ByteValue(hash[24]), new ByteValue(hash[25]),
        new ByteValue(hash[26]), new ByteValue(hash[27]),
        new ByteValue(hash[28]), new ByteValue(hash[29]),
        new ByteValue(hash[30]), new ByteValue(hash[31])));
  }
}