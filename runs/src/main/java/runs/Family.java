/*
    A blockchain transactions example with Hotmoka.
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

import static java.math.BigInteger.ONE;

import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;

import io.hotmoka.crypto.api.Signer;
import io.hotmoka.helpers.GasHelpers;
import io.hotmoka.helpers.SignatureHelpers;
import io.hotmoka.node.Accounts;
import io.hotmoka.node.MethodSignatures;
import io.hotmoka.node.StorageValues;
import io.hotmoka.node.TransactionRequests;
import io.hotmoka.node.api.Node;
import io.hotmoka.node.api.requests.SignedTransactionRequest;
import io.hotmoka.node.api.transactions.TransactionReference;
import io.hotmoka.node.api.values.BigIntegerValue;
import io.hotmoka.node.api.values.StorageReference;
import io.hotmoka.node.api.values.StringValue;
import io.hotmoka.node.remote.RemoteNodes;

/**
 * Run in the IDE or go inside this project and run
 * 
 * mvn clean package
 * java --module-path ../../hotmoka/io-hotmoka-moka/modules/explicit/:../../hotmoka/io-hotmoka-moka/modules/automatic:target/runs-0.0.1.jar -classpath ../../hotmoka/io-hotmoka-moka/modules/unnamed"/*" --add-modules org.glassfish.tyrus.container.grizzly.server,org.glassfish.tyrus.container.grizzly.client --module runs/runs.Family
 */
public class Family {

  // change this with your account's storage reference
  private final static String
    ADDRESS = "883efcf0348d1c37e38d7cdc6aece63d56df410bc5db606f3329903159b6d9d3#0";

  public static void main(String[] args) throws Exception {

	// the path of the user jar to install
    var familyPath = Paths.get("../family/target/family-0.0.1.jar");

    try (var node = RemoteNodes.of(URI.create("ws://panarea.hotmoka.io"), 20000)) {
    	// we get a reference to where io-takamaka-code-X.Y.Z.jar has been stored
        TransactionReference takamakaCode = node.getTakamakaCode();
        StorageReference manifest = node.getManifest();

        // we get the signing algorithm to use for requests
        var signature = node.getConfig().getSignatureForRequests();

        var account = StorageValues.reference(ADDRESS);
        KeyPair keys = loadKeys(node, account);

        // we create a signer that signs with the private key of our account
        Signer<SignedTransactionRequest<?>> signer = signature.getSigner
          (keys.getPrivate(), SignedTransactionRequest::toByteArrayWithoutSignature);

        // we get the nonce of our account: we use the account itself as caller and
        // an arbitrary nonce (ZERO in the code) since we are running
        // a @View method of the account
        BigInteger nonce = ((BigIntegerValue) node
          .runInstanceMethodCallTransaction(TransactionRequests.instanceViewMethodCall
            (account, // payer
            BigInteger.valueOf(50_000), // gas limit
            takamakaCode, // class path for the execution of the transaction
            MethodSignatures.NONCE, // method
            account)).get()) // receiver of the method call
          .getValue();

        // we get the chain identifier of the network
        String chainId = ((StringValue) node
          .runInstanceMethodCallTransaction(TransactionRequests.instanceViewMethodCall
            (account, // payer
            BigInteger.valueOf(50_000), // gas limit
            takamakaCode, // class path for the execution of the transaction
            MethodSignatures.GET_CHAIN_ID, // method
            manifest)).get()) // receiver of the method call
          .getValue();

        var gasHelper = GasHelpers.of(node);

        // we install family-0.0.1-SNAPSHOT.jar in the node: our account will pay
        TransactionReference family = node
          .addJarStoreTransaction(TransactionRequests.jarStore
            (signer, // an object that signs with the payer's private key
            account, // payer
            nonce, // payer's nonce: relevant since this is not a call to a @View method!
            chainId, // chain identifier: relevant since this is not a call to a @View method!
            BigInteger.valueOf(300_000), // gas limit: enough for this very small jar
            gasHelper.getSafeGasPrice(), // gas price: at least the current gas price of the network
            takamakaCode, // class path for the execution of the transaction
            Files.readAllBytes(familyPath), // bytes of the jar to install
            takamakaCode)); // dependencies of the jar that is being installed

        // we increase our copy of the nonce, ready for further
        // transactions having the account as payer
        nonce = nonce.add(ONE);

        System.out.println("family-0.0.1.jar installed at: " + family);
    }
  }

  private static KeyPair loadKeys(Node node, StorageReference account) throws Exception {
    return Accounts.of(account, "..").keys("chocolate", SignatureHelpers.of(node).signatureAlgorithmFor(account));
  }
}