/*
    A service startup example with Hotmoka.
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
import io.hotmoka.nodes.ConsensusParams;
import io.hotmoka.nodes.Node;
import io.hotmoka.service.NodeService;
import io.hotmoka.service.NodeServiceConfig;
import io.hotmoka.tendermint.TendermintBlockchain;
import io.hotmoka.tendermint.TendermintBlockchainConfig;

/**
 * Run in the IDE or go inside this project and run
 * 
 * mvn clean package
 * java --module-path ../../hotmoka/modules/explicit/:../../hotmoka/modules/automatic:target/runs-0.0.1.jar -classpath ../../hotmoka/modules/unnamed"/*" --module runs/runs.Publisher
 */
public class Publisher {
  public final static BigInteger SUPPLY = BigInteger.valueOf(100_000_000);

  public static void main(String[] args) throws Exception {
    TendermintBlockchainConfig config = new TendermintBlockchainConfig.Builder().build();
    NodeServiceConfig serviceConfig = new NodeServiceConfig.Builder().build();
    // the path of the runtime Takamaka jar, inside Maven's cache
    Path takamakaCodePath = Paths.get
      (System.getProperty("user.home") +
      "/.m2/repository/io/hotmoka/io-takamaka-code/" + Constants.VERSION + "/io-takamaka-code-" + Constants.VERSION + ".jar");

    // create a key pair for the gamete and compute the Base64-encoding of its public key
    var signature = SignatureAlgorithmForTransactionRequests.ed25519();
	Entropy entropy = new Entropy();
	KeyPair keys = entropy.keys("password", signature);
	var publicKeyBase64 = Base64.getEncoder().encodeToString(signature.encodingOf(keys.getPublic()));
	ConsensusParams consensus = new ConsensusParams.Builder()
		.setPublicKeyOfGamete(publicKeyBase64)
		.setInitialSupply(SUPPLY)
		.build();

	try (Node original = TendermintBlockchain.init(config, consensus);
         // remove the next line if you want to publish an uninitialized node
         //InitializedNode initialized = InitializedNode.of(original, consensus, takamakaCodePath);
         NodeService service = NodeService.of(serviceConfig, original)) {

        System.out.println("\nPress ENTER to turn off the server and exit this program");
        System.in.read();
    }
  }
}