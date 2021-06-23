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

import static io.hotmoka.beans.Coin.panarea;
import static io.hotmoka.beans.types.BasicTypes.INT;
import static java.math.BigInteger.ONE;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;

import io.hotmoka.beans.SignatureAlgorithm;
import io.hotmoka.beans.references.TransactionReference;
import io.hotmoka.beans.requests.ConstructorCallTransactionRequest;
import io.hotmoka.beans.requests.InstanceMethodCallTransactionRequest;
import io.hotmoka.beans.requests.JarStoreTransactionRequest;
import io.hotmoka.beans.requests.SignedTransactionRequest;
import io.hotmoka.beans.requests.SignedTransactionRequest.Signer;
import io.hotmoka.beans.signatures.CodeSignature;
import io.hotmoka.beans.signatures.ConstructorSignature;
import io.hotmoka.beans.signatures.NonVoidMethodSignature;
import io.hotmoka.beans.types.ClassType;
import io.hotmoka.beans.values.BigIntegerValue;
import io.hotmoka.beans.values.IntValue;
import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.beans.values.StorageValue;
import io.hotmoka.beans.values.StringValue;
import io.hotmoka.crypto.SignatureAlgorithmForTransactionRequests;
import io.hotmoka.nodes.Node;
import io.hotmoka.remote.RemoteNode;
import io.hotmoka.remote.RemoteNodeConfig;
import io.hotmoka.views.GasHelper;
import io.hotmoka.views.SignatureHelper;

/**
 * Run in the IDE or go inside this project and run
 * 
 * mvn clean package
 * java --module-path ../../hotmoka/modules/explicit/:../../hotmoka/modules/automatic:target/runs-0.0.1.jar -classpath ../../hotmoka/modules/unnamed"/*" --module runs/runs.Family3
 */
public class Family3 {

  // change this with your account's storage reference
  private final static String ADDRESS =
    "08ba1159da663e9c76ad614a1e9235fcfa2c3558a3437b1146ef438948d17d2d#0";

  private final static ClassType PERSON = new ClassType("io.takamaka.family.Person");

  public static void main(String[] args) throws Exception {

	// the path of the user jar to install
    Path familyPath = Paths.get("../family_exported/target/family_exported-0.0.1-SNAPSHOT.jar");

    RemoteNodeConfig config = new RemoteNodeConfig.Builder()
    	.setURL("panarea.hotmoka.io")
    	.build();

    try (Node node = RemoteNode.of(config)) {
    	// we get a reference to where io-takamaka-code-1.0.1.jar has been stored
        TransactionReference takamakaCode = node.getTakamakaCode();

        // we get the signing algorithm to use for requests
        SignatureAlgorithm<SignedTransactionRequest> signature
          = SignatureAlgorithmForTransactionRequests.mk(node.getNameOfSignatureAlgorithmForRequests());

        StorageReference account = new StorageReference(ADDRESS);
        KeyPair keys = loadKeys(node, account);

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

        // call the constructor of Person and store in albert the new object in blockchain
        StorageReference albert = node.addConstructorCallTransaction
          (new ConstructorCallTransactionRequest
            (signer, // an object that signs with the payer's private key
            account, // payer
            nonce, // payer's nonce: relevant since this is not a call to a @View method!
            chainId, // chain identifier: relevant since this is not a call to a @View method!
            BigInteger.valueOf(50_000), // gas limit: enough for a small object
            panarea(gasHelper.getSafeGasPrice()), // gas price, in panareas
            family, // class path for the execution of the transaction

            // constructor Person(String,int,int,int)
            new ConstructorSignature(PERSON, ClassType.STRING, INT, INT, INT),

            // actual arguments
            new StringValue("Albert Einstein"), new IntValue(14),
            new IntValue(4), new IntValue(1879)
        ));

        // we increase our copy of the nonce, ready for further
        // transactions having the account as payer
        nonce = nonce.add(ONE);

        StorageValue s = node.addInstanceMethodCallTransaction
          (new InstanceMethodCallTransactionRequest
            (signer, // an object that signs with the payer's private key
             account, // payer
             nonce, // payer's nonce: relevant since this is not a call to a @View method!
             chainId, // chain identifier: relevant since this is not a call to a @View method!
             BigInteger.valueOf(50_000), // gas limit: enough for a small object
             panarea(gasHelper.getSafeGasPrice()), // gas price, in panareas
             family, // class path for the execution of the transaction

      	     // method to call: String Person.toString()
        	 new NonVoidMethodSignature(PERSON, "toString", ClassType.STRING),

        	 // receiver of the method to call
             albert
           ));

        // we increase our copy of the nonce, ready for further
        // transactions having the account as payer
        nonce = nonce.add(ONE);

        // print the result of the call
        System.out.println(s);
    }
  }

  private static KeyPair loadKeys(Node node, StorageReference account) throws Exception {
	  return new SignatureHelper(node).signatureAlgorithmFor(account).readKeys("../" + account.toString());
  }
}