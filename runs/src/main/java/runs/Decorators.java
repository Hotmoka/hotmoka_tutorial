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

import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.util.Base64;

import io.hotmoka.constants.Constants;
import io.hotmoka.crypto.Entropy;
import io.hotmoka.crypto.SignatureAlgorithmForTransactionRequests;
import io.hotmoka.helpers.InitializedNode;
import io.hotmoka.helpers.NodeWithAccounts;
import io.hotmoka.helpers.NodeWithJars;
import io.hotmoka.memory.MemoryBlockchain;
import io.hotmoka.memory.MemoryBlockchainConfig;
import io.hotmoka.nodes.ConsensusParams;
import io.hotmoka.nodes.Node;

/**
 * Run in the IDE or go inside this project and run
 * 
 * mvn clean package
 * java --module-path ../../hotmoka/modules/explicit/:../../hotmoka/modules/automatic:target/runs-0.0.1.jar -classpath ../../hotmoka/modules/unnamed"/*" --module runs/runs.Decorators
 */
public class Decorators {
  public final static BigInteger SUPPLY = BigInteger.valueOf(1_000_000_000);

  public static void main(String[] args) throws Exception {
    MemoryBlockchainConfig config = new MemoryBlockchainConfig.Builder().build();

    // the path of the runtime Takamaka jar, inside Maven's cache
    Path takamakaCodePath = Paths.get
      (System.getProperty("user.home") +
      "/.m2/repository/io/hotmoka/io-takamaka-code/" + Constants.VERSION + "/io-takamaka-code-" + Constants.VERSION + ".jar");

    // the path of the user jar to install
    Path familyPath = Paths.get("../family/target/family-0.0.1.jar");

    // create a key pair for the gamete and compute the Base64-encoding of its public key
    var signature = SignatureAlgorithmForTransactionRequests.ed25519();
	Entropy entropy = new Entropy();
	KeyPair keys = entropy.keys("password", signature);
	var publicKeyBase64 = Base64.getEncoder().encodeToString(signature.encodingOf(keys.getPublic()));
	ConsensusParams consensus = new ConsensusParams.Builder()
   		.setInitialSupply(SUPPLY)
   		.setPublicKeyOfGamete(publicKeyBase64).build();

	try (Node node = MemoryBlockchain.init(config, consensus)) {
      // first view: store the io-takamaka-code jar and create manifest and gamete
      InitializedNode initialized = InitializedNode.of(node, consensus, takamakaCodePath);

      // second view: store family-0.0.1.jar: the gamete will pay for that
      NodeWithJars nodeWithJars = NodeWithJars.of
        (node, initialized.gamete(), keys.getPrivate(), familyPath);

	  // third view: create two accounts, the first with 10,000,000 units of coin
      // and the second with 20,000,000 units of coin; the gamete will pay
      NodeWithAccounts nodeWithAccounts = NodeWithAccounts.of
        (node, initialized.gamete(), keys.getPrivate(),
        BigInteger.valueOf(10_000_000), BigInteger.valueOf(20_000_000));

      System.out.println("manifest: " + node.getManifest());
      System.out.println("family-0.0.1.jar: " + nodeWithJars.jar(0));
      System.out.println("account #0: " + nodeWithAccounts.account(0) +
                         "\n  with private key " + nodeWithAccounts.privateKey(0));
      System.out.println("account #1: " + nodeWithAccounts.account(1) +
                         "\n  with private key " + nodeWithAccounts.privateKey(1));
    }
  }
}