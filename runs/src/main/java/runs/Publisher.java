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
import java.nio.file.Paths;
import java.security.KeyPair;
import java.util.Base64;

import io.hotmoka.constants.Constants;
import io.hotmoka.crypto.Entropies;
import io.hotmoka.helpers.InitializedNodes;
import io.hotmoka.node.SignatureAlgorithmForTransactionRequests;
import io.hotmoka.node.SimpleValidatorsConsensusConfigBuilders;
import io.hotmoka.node.tendermint.TendermintNodeConfigBuilders;
import io.hotmoka.node.tendermint.TendermintNodes;
import io.hotmoka.service.NodeService;
import io.hotmoka.service.NodeServiceConfig;

/**
 * Run in the IDE or go inside this project and run
 * 
 * mvn clean package
 * java --module-path ../../hotmoka/modules/explicit/:../../hotmoka/modules/automatic:target/runs-0.0.1.jar -classpath ../../hotmoka/modules/unnamed"/*" --module runs/runs.Publisher
 */
public class Publisher {
  public final static BigInteger SUPPLY = BigInteger.valueOf(100_000_000);

  public static void main(String[] args) throws Exception {
    var config = TendermintNodeConfigBuilders.defaults().build();
    var serviceConfig = new NodeServiceConfig.Builder().build();
    // the path of the runtime Takamaka jar, inside Maven's cache
    var takamakaCodePath = Paths.get
      (System.getProperty("user.home") +
      "/.m2/repository/io/hotmoka/io-takamaka-code/" + Constants.TAKAMAKA_VERSION +
      "/io-takamaka-code-" + Constants.TAKAMAKA_VERSION + ".jar");

    // create a key pair for the gamete and compute the Base64-encoding of its public key
    var signature = SignatureAlgorithmForTransactionRequests.ed25519();
    var entropy = Entropies.random();
	KeyPair keys = entropy.keys("password", signature);
	var publicKeyBase64 = Base64.getEncoder().encodeToString(signature.encodingOf(keys.getPublic()));
	var consensus = SimpleValidatorsConsensusConfigBuilders.defaults()
		.setPublicKeyOfGamete(publicKeyBase64)
		.setInitialSupply(SUPPLY)
		.build();

	try (var original = TendermintNodes.init(config, consensus);
         // remove the next line if you want to publish an uninitialized node
         var initialized = InitializedNodes.of(original, consensus, takamakaCodePath);
         var service = NodeService.of(serviceConfig, original)) {

        System.out.println("\nPress ENTER to turn off the server and exit this program");
        System.in.read();
    }
  }
}