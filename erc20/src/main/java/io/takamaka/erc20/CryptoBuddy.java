/*
    An ERC20 token class example in Takamaka.
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

package io.takamaka.erc20;

import static io.takamaka.code.lang.Takamaka.require;

import io.takamaka.code.lang.Contract;
import io.takamaka.code.lang.FromContract;
import io.takamaka.code.math.UnsignedBigInteger;
import io.takamaka.code.tokens.ERC20;

/**
 * An ERC20 token example that allows its creator only to mint or burn tokens.
 */
public class CryptoBuddy extends ERC20 {
  private final Contract owner;

  public @FromContract CryptoBuddy() {
    super("CryptoBuddy", "CB");
    owner = caller();
    UnsignedBigInteger initialSupply = new UnsignedBigInteger("200000");
    UnsignedBigInteger multiplier = new UnsignedBigInteger("10").pow(18);
    _mint(caller(), initialSupply.multiply(multiplier)); // 200'000 * 10 ^ 18
  }

  public @FromContract void mint(Contract account, UnsignedBigInteger amount) {
    require(caller() == owner, "Lack of permission");
    _mint(account, amount);
  }

  public @FromContract void burn(Contract account, UnsignedBigInteger amount) {
    require(caller() == owner, "Lack of permission");
    _burn(account, amount);
  }
}