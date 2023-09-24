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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;

import io.hotmoka.beans.references.TransactionReference;
import io.hotmoka.beans.requests.InstanceMethodCallTransactionRequest;
import io.hotmoka.beans.requests.JarStoreTransactionRequest;
import io.hotmoka.beans.signatures.CodeSignature;
import io.hotmoka.beans.values.BigIntegerValue;
import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.beans.values.StringValue;
import io.hotmoka.crypto.Signers;
import io.hotmoka.helpers.GasHelpers;
import io.hotmoka.helpers.SignatureHelpers;
import io.hotmoka.node.Accounts;
import io.hotmoka.node.SignatureAlgorithmForTransactionRequests;
import io.hotmoka.node.api.Node;
import io.hotmoka.remote.RemoteNode;
import io.hotmoka.remote.RemoteNodeConfig;

/**
 * Run in the IDE or go inside this project and run
 * 
 * mvn clean package
 * java --module-path ../../hotmoka/modules/explicit/:../../hotmoka/modules/automatic:target/runs-0.0.1.jar -classpath ../../hotmoka/modules/unnamed"/*" --module runs/runs.Family
 */
public class Family {

  // change this with your account's storage reference
  private final static String
    ADDRESS = "3290de7bdcb50522448a426160581bf3c58cb7480569ecc3358a9800c2477ee1#0";

  public static void main(String[] args) throws Exception {

	// the path of the user jar to install
    var familyPath = Paths.get("../family/target/family-0.0.1.jar");

    var config = new RemoteNodeConfig.Builder()
    	.setURL("panarea.hotmoka.io")
    	.build();

    try (var node = RemoteNode.of(config)) {
    	// we get a reference to where io-takamaka-code-1.0.1.jar has been stored
        TransactionReference takamakaCode = node.getTakamakaCode();

        // we get the signing algorithm to use for requests
        var signature = SignatureAlgorithmForTransactionRequests.of(node.getNameOfSignatureAlgorithmForRequests());

        var account = new StorageReference(ADDRESS);
        KeyPair keys = loadKeys(node, account);

        // we create a signer that signs with the private key of our account
        var signer = Signers.with(signature, keys.getPrivate());

        // we get the nonce of our account: we use the account itself as caller and
        // an arbitrary nonce (ZERO in the code) since we are running
        // a @View method of the account
        BigInteger nonce = ((BigIntegerValue) node
          .runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
            (account, // payer
            BigInteger.valueOf(50_000), // gas limit
            takamakaCode, // class path for the execution of the transaction
            CodeSignature.NONCE, // method
            account))) // receiver of the method call
          .value;

        // we get the chain identifier of the network
        String chainId = ((StringValue) node
          .runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
            (account, // payer
            BigInteger.valueOf(50_000), // gas limit
            takamakaCode, // class path for the execution of the transaction
            CodeSignature.GET_CHAIN_ID, // method
            node.getManifest()))) // receiver of the method call
          .value;

        var gasHelper = GasHelpers.of(node);

        // we install family-0.0.1-SNAPSHOT.jar in the node: our account will pay
        TransactionReference family = node
          .addJarStoreTransaction(new JarStoreTransactionRequest
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