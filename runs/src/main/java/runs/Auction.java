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
import static io.hotmoka.beans.StorageTypes.BIG_INTEGER;
import static io.hotmoka.beans.StorageTypes.BOOLEAN;
import static io.hotmoka.beans.StorageTypes.BYTE;
import static io.hotmoka.beans.StorageTypes.BYTES32_SNAPSHOT;
import static io.hotmoka.beans.StorageTypes.INT;
import static io.hotmoka.beans.StorageTypes.PAYABLE_CONTRACT;
import static io.hotmoka.beans.StorageValues.byteOf;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.hotmoka.beans.ConstructorSignatures;
import io.hotmoka.beans.MethodSignatures;
import io.hotmoka.beans.StorageTypes;
import io.hotmoka.beans.StorageValues;
import io.hotmoka.beans.api.signatures.ConstructorSignature;
import io.hotmoka.beans.api.transactions.TransactionReference;
import io.hotmoka.beans.api.types.ClassType;
import io.hotmoka.beans.api.values.StorageReference;
import io.hotmoka.beans.api.values.StorageValue;
import io.hotmoka.beans.api.values.StringValue;
import io.hotmoka.beans.requests.ConstructorCallTransactionRequest;
import io.hotmoka.beans.requests.InstanceMethodCallTransactionRequest;
import io.hotmoka.beans.requests.JarStoreTransactionRequest;
import io.hotmoka.beans.requests.SignedTransactionRequest;
import io.hotmoka.beans.signatures.MethodSignature;
import io.hotmoka.beans.signatures.NonVoidMethodSignature;
import io.hotmoka.beans.signatures.VoidMethodSignature;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.crypto.api.Signer;
import io.hotmoka.helpers.GasHelpers;
import io.hotmoka.helpers.NonceHelpers;
import io.hotmoka.helpers.SignatureHelpers;
import io.hotmoka.helpers.api.GasHelper;
import io.hotmoka.helpers.api.NonceHelper;
import io.hotmoka.node.Accounts;
import io.hotmoka.node.api.Node;
import io.hotmoka.node.remote.RemoteNodeConfigBuilders;
import io.hotmoka.node.remote.RemoteNodes;
import io.hotmoka.node.remote.api.RemoteNodeConfig;

/**
 * Run in the IDE or go inside this project and run
 * 
 * mvn clean package
 * java --module-path ../../hotmoka/modules/explicit/:../../hotmoka/modules/automatic:target/runs-0.0.1.jar -classpath ../../hotmoka/modules/unnamed"/*" --module runs/runs.Auction
 */
public class Auction {
	// change this with your accounts' storage references
	private final static String[] ADDRESSES = new String[3];
	
	static {
		ADDRESSES[0] = "3290de7bdcb50522448a426160581bf3c58cb7480569ecc3358a9800c2477ee1#0";
		ADDRESSES[1] = "ab03328645589defeb289fd49a5fa7b6d41d1475d307565717b568e4cd4e7bec#0";
		ADDRESSES[2] = "b9372abbd001a7588a0a2d0b9bcca6aee5c9fc943b0227e664c5f1f53bc2d443#0";
	}

	public final static int NUM_BIDS = 10; // number of bids placed
	public final static int BIDDING_TIME = 130_000; // in milliseconds
	public final static int REVEAL_TIME = 170_000; // in milliseconds

	private final static BigInteger _500_000 = BigInteger.valueOf(500_000);

	private final static ClassType BLIND_AUCTION
	  = StorageTypes.classNamed("io.takamaka.auction.BlindAuction");
	private final static ConstructorSignature CONSTRUCTOR_BLIND_AUCTION
	  = ConstructorSignatures.of(BLIND_AUCTION, INT, INT);
	private final static ConstructorSignature CONSTRUCTOR_BYTES32_SNAPSHOT
	  = ConstructorSignatures.of(BYTES32_SNAPSHOT,
			BYTE, BYTE, BYTE, BYTE, BYTE, BYTE, BYTE, BYTE,
			BYTE, BYTE, BYTE, BYTE, BYTE, BYTE, BYTE, BYTE,
			BYTE, BYTE, BYTE, BYTE, BYTE, BYTE, BYTE, BYTE,
			BYTE, BYTE, BYTE, BYTE, BYTE, BYTE, BYTE, BYTE);
	private final static ConstructorSignature CONSTRUCTOR_REVEALED_BID
	  = ConstructorSignatures.of(
			StorageTypes.classNamed("io.takamaka.auction.BlindAuction$RevealedBid"),
			BIG_INTEGER, BOOLEAN, BYTES32_SNAPSHOT);
	private final static MethodSignature BID = new VoidMethodSignature
			(BLIND_AUCTION, "bid", BIG_INTEGER, BYTES32_SNAPSHOT);
	private final static MethodSignature REVEAL = new VoidMethodSignature
			(BLIND_AUCTION, "reveal",
			StorageTypes.classNamed("io.takamaka.auction.BlindAuction$RevealedBid"));
	private final static MethodSignature AUCTION_END = new NonVoidMethodSignature
			(BLIND_AUCTION, "auctionEnd", PAYABLE_CONTRACT);

	//the hashing algorithm used to hide the bids
	private final MessageDigest digest = MessageDigest.getInstance("SHA-256");

	private final Path auctionPath = Paths.get("../auction/target/auction-0.0.1.jar");
	private final TransactionReference takamakaCode;
	private final StorageReference[] accounts;
	private final List<Signer<SignedTransactionRequest>> signers;
	private final String chainId;
	private final long start;  // the time when bids started being placed
	private final Node node;
	private final TransactionReference classpath;
	private final StorageReference auction;
	private final List<BidToReveal> bids = new ArrayList<>();
	private final GasHelper gasHelper;
	private final NonceHelper nonceHelper;

	public static void main(String[] args) throws Exception {
		RemoteNodeConfig config = RemoteNodeConfigBuilders.defaults()
			.setURL("panarea.hotmoka.io")
			.build();

		try (Node node = RemoteNodes.of(config)) {
			new Auction(node);
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
				(signers.get(player), accounts[player],
				nonceHelper.getNonceOf(accounts[player]), chainId, _500_000,
				panarea(gasHelper.getSafeGasPrice()), classpath, CONSTRUCTOR_BYTES32_SNAPSHOT,
				byteOf(salt[0]), byteOf(salt[1]), byteOf(salt[2]), byteOf(salt[3]),
				byteOf(salt[4]), byteOf(salt[5]), byteOf(salt[6]), byteOf(salt[7]),
				byteOf(salt[8]), byteOf(salt[9]), byteOf(salt[10]), byteOf(salt[11]),
				byteOf(salt[12]), byteOf(salt[13]), byteOf(salt[14]), byteOf(salt[15]),
				byteOf(salt[16]), byteOf(salt[17]), byteOf(salt[18]), byteOf(salt[19]),
				byteOf(salt[20]), byteOf(salt[21]), byteOf(salt[22]), byteOf(salt[23]),
				byteOf(salt[24]), byteOf(salt[25]), byteOf(salt[26]), byteOf(salt[27]),
				byteOf(salt[28]), byteOf(salt[29]), byteOf(salt[30]), byteOf(salt[31])));

			return node.addConstructorCallTransaction(new ConstructorCallTransactionRequest
				(signers.get(player), accounts[player],
				nonceHelper.getNonceOf(accounts[player]), chainId,
				_500_000, panarea(gasHelper.getSafeGasPrice()), classpath, CONSTRUCTOR_REVEALED_BID,
				StorageValues.bigIntegerOf(value), StorageValues.booleanOf(fake), bytes32));
		}
	}

	private Auction(Node node) throws Exception {
		this.node = node;
		takamakaCode = node.getTakamakaCode();
		accounts = Stream.of(ADDRESSES).map(StorageValues::reference).toArray(StorageReference[]::new);
		var signature = SignatureAlgorithms.of(node.getNameOfSignatureAlgorithmForRequests());
		signers = Stream.of(accounts).map(this::loadKeys).map(KeyPair::getPrivate).map(key -> signature.getSigner(key, SignedTransactionRequest::toByteArrayWithoutSignature))
			.collect(Collectors.toCollection(ArrayList::new));
		gasHelper = GasHelpers.of(node);
		nonceHelper = NonceHelpers.of(node);
		chainId = getChainId();
		classpath = installJar();
		auction = createContract();
		start = System.currentTimeMillis();

		StorageReference expectedWinner = placeBids();
		waitUntilEndOfBiddingTime();
		revealBids();
		waitUntilEndOfRevealTime();
		StorageValue winner = askForWinner();

		// show that the contract computes the correct winner
		System.out.println("expected winner: " + expectedWinner);
		System.out.println("actual winner: " + winner);
	}

	private StorageReference createContract() throws Exception {
		System.out.println("Creating contract");

		return node.addConstructorCallTransaction
			(new ConstructorCallTransactionRequest(signers.get(0), accounts[0],
			nonceHelper.getNonceOf(accounts[0]), chainId, _500_000, panarea(gasHelper.getSafeGasPrice()),
			classpath, CONSTRUCTOR_BLIND_AUCTION,
			StorageValues.intOf(BIDDING_TIME), StorageValues.intOf(REVEAL_TIME)));
	}

	private String getChainId() throws Exception {
		return ((StringValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
			(accounts[0], // payer
			BigInteger.valueOf(50_000), // gas limit
			takamakaCode, // class path for the execution of the transaction
			MethodSignatures.GET_CHAIN_ID, // method
			node.getManifest()))) // receiver of the method call
			.getValue();
	}

	private TransactionReference installJar() throws Exception {
		System.out.println("Installing jar");

		return node.addJarStoreTransaction(new JarStoreTransactionRequest
			(signers.get(0), // an object that signs with the payer's private key
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
			var deposit = BigInteger.valueOf(random.nextInt(1000));
			var value = BigInteger.valueOf(random.nextInt(1000));
			boolean fake = random.nextBoolean();
			var salt = new byte[32];
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
				(signers.get(player), accounts[player],
				nonceHelper.getNonceOf(accounts[player]), chainId,
				_500_000, panarea(gasHelper.getSafeGasPrice()), classpath, BID,
				auction, StorageValues.bigIntegerOf(deposit), bytes32));

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
				(signers.get(player), accounts[player],
				nonceHelper.getNonceOf(accounts[player]), chainId, _500_000,
				panarea(gasHelper.getSafeGasPrice()),
				classpath, REVEAL, auction, bidInBlockchain));
		}
	}

	private StorageReference askForWinner() throws Exception {
		StorageValue winner = node.addInstanceMethodCallTransaction
			(new InstanceMethodCallTransactionRequest
			(signers.get(0), accounts[0], nonceHelper.getNonceOf(accounts[0]),
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
			signers.get(player),
			accounts[player],
			nonceHelper.getNonceOf(accounts[player]), chainId,
			_500_000, panarea(gasHelper.getSafeGasPrice()),
			classpath, CONSTRUCTOR_BYTES32_SNAPSHOT,
			byteOf(hash[0]), byteOf(hash[1]),
			byteOf(hash[2]), byteOf(hash[3]),
			byteOf(hash[4]), byteOf(hash[5]),
			byteOf(hash[6]), byteOf(hash[7]),
			byteOf(hash[8]), byteOf(hash[9]),
			byteOf(hash[10]), byteOf(hash[11]),
			byteOf(hash[12]), byteOf(hash[13]),
			byteOf(hash[14]), byteOf(hash[15]),
			byteOf(hash[16]), byteOf(hash[17]),
			byteOf(hash[18]), byteOf(hash[19]),
			byteOf(hash[20]), byteOf(hash[21]),
			byteOf(hash[22]), byteOf(hash[23]),
			byteOf(hash[24]), byteOf(hash[25]),
			byteOf(hash[26]), byteOf(hash[27]),
			byteOf(hash[28]), byteOf(hash[29]),
			byteOf(hash[30]), byteOf(hash[31])));
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

			return Accounts.of(account, "..").keys(password, SignatureHelpers.of(node).signatureAlgorithmFor(account));
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}