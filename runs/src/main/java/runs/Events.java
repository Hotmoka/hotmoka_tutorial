/*
    A blockchain startup and transactions example with Hotmoka.
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

package runs;

import static io.hotmoka.beans.Coin.panarea;
import static io.hotmoka.beans.types.BasicTypes.BOOLEAN;
import static io.hotmoka.beans.types.BasicTypes.BYTE;
import static io.hotmoka.beans.types.BasicTypes.INT;
import static io.hotmoka.beans.types.ClassType.BIG_INTEGER;
import static io.hotmoka.beans.types.ClassType.BYTES32_SNAPSHOT;
import static io.hotmoka.beans.types.ClassType.PAYABLE_CONTRACT;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import io.hotmoka.beans.SignatureAlgorithm;
import io.hotmoka.beans.references.TransactionReference;
import io.hotmoka.beans.requests.ConstructorCallTransactionRequest;
import io.hotmoka.beans.requests.InstanceMethodCallTransactionRequest;
import io.hotmoka.beans.requests.JarStoreTransactionRequest;
import io.hotmoka.beans.requests.SignedTransactionRequest;
import io.hotmoka.beans.requests.SignedTransactionRequest.Signer;
import io.hotmoka.beans.signatures.CodeSignature;
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
import io.hotmoka.beans.values.StringValue;
import io.hotmoka.crypto.Account;
import io.hotmoka.crypto.SignatureAlgorithmForTransactionRequests;
import io.hotmoka.nodes.Node;
import io.hotmoka.nodes.Node.Subscription;
import io.hotmoka.remote.RemoteNode;
import io.hotmoka.remote.RemoteNodeConfig;
import io.hotmoka.views.GasHelper;
import io.hotmoka.views.NonceHelper;
import io.hotmoka.views.SignatureHelper;

/**
 * Run in the IDE or go inside this project and run
 * 
 * mvn clean package
 * java --module-path ../../hotmoka/modules/explicit/:../../hotmoka/modules/automatic:target/runs-0.0.1.jar -classpath ../../hotmoka/modules/unnamed"/*" --module runs/runs.Events
 */
public class Events {
	// change this with your accounts' storage references
	private final static String[] ADDRESSES =
		{ "8a21b72f3f499a128acf99463d7b25450d34e8f9b4a81ee0af5c9ff2dd10a23f#0",
		  "6602aedcfbee393a2828c7cc06e7319cf92502ce1c026b9e5527c27d799eeff9#0",
		  "84ebc6ccdb2f5e76bb8b7d93c9b60805f518a76ae59f78e1b26bfc3734e5475d#0" };

	public final static int NUM_BIDS = 20; // number of bids placed
	public final static int BIDDING_TIME = 50_000; // in milliseconds
	public final static int REVEAL_TIME = 70_000; // in milliseconds

	private final static BigInteger _500_000 = BigInteger.valueOf(500_000);

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

	// the hashing algorithm used to hide the bids
	private final MessageDigest digest = MessageDigest.getInstance("SHA-256");

	private final Path auctionPath = Paths.get("../auction_events/target/auction_events-0.0.1.jar");
	private final TransactionReference takamakaCode;
	private final StorageReference[] accounts;
	private final Signer[] signers;
	private final String chainId;
	private final long start;  // the time when bids started being placed
	private final Node node;
	private final TransactionReference classpath;
	private final StorageReference auction;
	private final List<BidToReveal> bids = new ArrayList<>();
	private final GasHelper gasHelper;
	private final NonceHelper nonceHelper;

	public static void main(String[] args) throws Exception {
		RemoteNodeConfig config = new RemoteNodeConfig.Builder()
			.setURL("panarea.hotmoka.io")
			.build();

		try (Node node = RemoteNode.of(config)) {
			new Events(node);
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
				(signers[player], accounts[player],
				nonceHelper.getNonceOf(accounts[player]), chainId, _500_000,
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
				(signers[player], accounts[player],
				nonceHelper.getNonceOf(accounts[player]), chainId,
				_500_000, panarea(gasHelper.getSafeGasPrice()), classpath, CONSTRUCTOR_REVEALED_BID,
				new BigIntegerValue(value), new BooleanValue(fake), bytes32));
		}
	}

	private Events(Node node) throws Exception {
		this.node = node;
		takamakaCode = node.getTakamakaCode();
		accounts = Stream.of(ADDRESSES).map(StorageReference::new).toArray(StorageReference[]::new);
		SignatureAlgorithm<SignedTransactionRequest> signature
		  = SignatureAlgorithmForTransactionRequests.mk(node.getNameOfSignatureAlgorithmForRequests());
		signers = Stream.of(accounts).map(this::loadKeys).map(keys -> Signer.with(signature, keys)).toArray(Signer[]::new);
		gasHelper = new GasHelper(node);
		nonceHelper = new NonceHelper(node);
		chainId = getChainId();
		classpath = installJar();
		auction = createContract();
		start = System.currentTimeMillis();

		try (Subscription subscription = node.subscribeToEvents(auction,
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

	private StorageReference createContract() throws Exception {
		System.out.println("Creating contract");

		return node.addConstructorCallTransaction
			(new ConstructorCallTransactionRequest(signers[0], accounts[0],
			nonceHelper.getNonceOf(accounts[0]), chainId, _500_000, panarea(gasHelper.getSafeGasPrice()),
			classpath, CONSTRUCTOR_BLIND_AUCTION,
			new IntValue(BIDDING_TIME), new IntValue(REVEAL_TIME)));
	}

	private String getChainId() throws Exception {
		return ((StringValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
			(accounts[0], // payer
			BigInteger.valueOf(50_000), // gas limit
			takamakaCode, // class path for the execution of the transaction
			CodeSignature.GET_CHAIN_ID, // method
			node.getManifest()))) // receiver of the method call
			.value;
	}

	private TransactionReference installJar() throws Exception {
		System.out.println("Installing jar");

		return node.addJarStoreTransaction(new JarStoreTransactionRequest
			(signers[0], // an object that signs with the payer's private key
			accounts[0], // payer
			nonceHelper.getNonceOf(accounts[0]), // payer's nonce
			chainId, // chain identifier
			BigInteger.valueOf(1_000_000), // gas limit: enough for this very small jar
			gasHelper.getSafeGasPrice(), // gas price: at least the current gas price of the network
			takamakaCode, // class path for the execution of the transaction
			Files.readAllBytes(auctionPath), // bytes of the jar to install
			takamakaCode)); // dependency
	}

	private StorageReference placeBids() throws Exception {
		BigInteger maxBid = BigInteger.ZERO;
		StorageReference expectedWinner = null;
		Random random = new Random();

		int i = 1;
		while (i <= NUM_BIDS) { // generate NUM_BIDS random bids
			System.out.println("Placing bid " + i);
			int player = 1 + random.nextInt(accounts.length - 1);
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
					expectedWinner = accounts[player];
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
				(signers[player], accounts[player],
				nonceHelper.getNonceOf(accounts[player]), chainId,
				_500_000, panarea(gasHelper.getSafeGasPrice()), classpath, BID,
				auction, new BigIntegerValue(deposit), bytes32));

			i++;
		}

		return expectedWinner;
	}

	private void revealBids() throws Exception {
		// we create the revealed bids in blockchain; this is safe now, since the bidding time is over
		int counter = 1;
		for (BidToReveal bid: bids) {
			System.out.println("Revealing bid " + counter++ + " out of " + bids.size());
			int player = bid.player;
			StorageReference bidInBlockchain = bid.intoBlockchain();
			node.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
				(signers[player], accounts[player],
				nonceHelper.getNonceOf(accounts[player]), chainId, _500_000,
				panarea(gasHelper.getSafeGasPrice()),
				classpath, REVEAL, auction, bidInBlockchain));
		}
	}

	private StorageReference askForWinner() throws Exception {
		StorageValue winner = node.addInstanceMethodCallTransaction
			(new InstanceMethodCallTransactionRequest
			(signers[0], accounts[0], nonceHelper.getNonceOf(accounts[0]),
			chainId, _500_000, panarea(gasHelper.getSafeGasPrice()),
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
	 * Hashes a bid and put it in the store of the node, in hashed form.
	 */
	private StorageReference codeAsBytes32(int player, BigInteger value, boolean fake, byte[] salt) throws Exception {
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
			accounts[player],
			nonceHelper.getNonceOf(accounts[player]), chainId,
			_500_000, panarea(gasHelper.getSafeGasPrice()),
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

	private KeyPair loadKeys(StorageReference account) {
		try {
			String password;
			if (account.toString().equals(ADDRESSES[0]))
				password = "chocolate";
			else if (account.toString().equals(ADDRESSES[1]))
				password = "orange";
			else
				password = "apple";

			return new Account(account, "..").keys(password, new SignatureHelper(node).signatureAlgorithmFor(account));
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}