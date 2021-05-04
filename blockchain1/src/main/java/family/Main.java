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

package family;

import static java.math.BigInteger.ONE;

import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;

import io.hotmoka.beans.SignatureAlgorithm;
import io.hotmoka.beans.references.TransactionReference;
import io.hotmoka.beans.requests.InstanceMethodCallTransactionRequest;
import io.hotmoka.beans.requests.JarStoreTransactionRequest;
import io.hotmoka.beans.requests.SignedTransactionRequest;
import io.hotmoka.beans.requests.SignedTransactionRequest.Signer;
import io.hotmoka.beans.signatures.CodeSignature;
import io.hotmoka.beans.values.BigIntegerValue;
import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.beans.values.StringValue;
import io.hotmoka.crypto.SignatureAlgorithmForTransactionRequests;
import io.hotmoka.nodes.GasHelper;
import io.hotmoka.nodes.Node;
import io.hotmoka.remote.RemoteNode;
import io.hotmoka.remote.RemoteNodeConfig;

/**
 * Move inside this project and run
 * 
 * mvn clean package
 * java --module-path ../../hotmoka/modules/explicit/:../../hotmoka/modules/automatic:target/blockchain1-0.0.1-SNAPSHOT.jar -classpath ../../hotmoka/modules/unnamed"/*" --module blockchain/family.Main
 */
public class Main {

  // change this with your account's storage reference
  private final static String ADDRESS =
    "22e5e16eeed3b4a78176ddfe1f60d5a82b07b0fc0c95a2000b86a806853add39#0";

  public static void main(String[] args) throws Exception {

	// the path of the user jar to install
    Path familyPath = Paths.get("../family/target/family-0.0.1-SNAPSHOT.jar");

    RemoteNodeConfig config = new RemoteNodeConfig.Builder()
    	.setURL("ec2-54-194-239-91.eu-west-1.compute.amazonaws.com:8080")
    	.build();

    try (Node node = RemoteNode.of(config)) {
    	// we get a reference to where io-takamaka-code-1.0.0.jar has been stored
        TransactionReference takamakaCode = node.getTakamakaCode();

        // we get the signing algorithm to use for requests
        SignatureAlgorithm<SignedTransactionRequest> signature
          = SignatureAlgorithmForTransactionRequests.mk(node.getNameOfSignatureAlgorithmForRequests());

        StorageReference account = new StorageReference(ADDRESS);
        KeyPair keys = loadKeys(ADDRESS);

        // we create a signer that signs with the private key of our account
        Signer signer = Signer.with(signature, keys.getPrivate());

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

        GasHelper gasHelper = new GasHelper(node);

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

        System.out.println("family-0.0.1-SNAPSHOT.jar installed at: " + family);
    }
  }

  private static KeyPair loadKeys(String account) throws Exception {
	  try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream("../" + account + ".keys"))) {
		  return (KeyPair) ois.readObject();
	  }
  }
}