/*
    A tic-tac-toe game smart contract in Takamaka.
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

package io.takamaka.tictactoe;

import static io.takamaka.code.lang.Takamaka.require;
import static java.util.stream.Collectors.joining;
import static java.util.stream.IntStream.rangeClosed;

import java.math.BigInteger;

import io.takamaka.code.lang.Contract;
import io.takamaka.code.lang.FromContract;
import io.takamaka.code.lang.Payable;
import io.takamaka.code.lang.PayableContract;
import io.takamaka.code.lang.View;
import io.takamaka.code.util.StorageArray;
import io.takamaka.code.util.StorageTreeArray;

public class TicTacToe extends Contract {

  public enum Tile {
    EMPTY, CROSS, CIRCLE;

    @Override
    public String toString() {
      switch (this) {
      case EMPTY: return " ";
      case CROSS: return "X";
      default: return "O";
      }
    }

    private Tile nextTurn() {
      return this == CROSS ? CIRCLE : CROSS;
    }
  }

  private final StorageArray<Tile> board = new StorageTreeArray<>(9, Tile.EMPTY);
  private PayableContract crossPlayer, circlePlayer;
  private Tile turn = Tile.CROSS; // cross plays first
  private boolean gameOver;

  public @View Tile at(int x, int y) {
    require(1 <= x && x <= 3 && 1 <= y && y <= 3,
      "coordinates must be between 1 and 3");
    return board.get((y - 1) * 3 + x - 1);
  }

  private void set(int x, int y, Tile tile) {
    board.set((y - 1) * 3 + x - 1, tile);
  }

  public @Payable @FromContract(PayableContract.class)
      void play(long amount, int x, int y) {

    require(!gameOver, "the game is over");
    require(1 <= x && x <= 3 && 1 <= y && y <= 3,
      "coordinates must be between 1 and 3");
    require(at(x, y) == Tile.EMPTY, "the selected tile is not empty");

    var player = (PayableContract) caller();

    if (turn == Tile.CROSS)
      if (crossPlayer == null)
        crossPlayer = player;
      else
        require(player == crossPlayer, "it's not your turn");
    else
      if (circlePlayer == null) {
        require(crossPlayer != player, "you cannot play against yourself");
        long previousBet = balance().subtract(BigInteger.valueOf(amount)).longValue();
        require(amount >= previousBet,
          () -> "you must bet at least " + previousBet + " coins");
        circlePlayer = player;
      }
      else
        require(player == circlePlayer, "it's not your turn");

    set(x, y, turn);
    if (isGameOver(x, y))
      player.receive(balance());
    else
      turn = turn.nextTurn();
  }

  private boolean isGameOver(int x, int y) {
    return gameOver =
      rangeClosed(1, 3).allMatch(_y -> at(x, _y) == turn) || // column x
      rangeClosed(1, 3).allMatch(_x -> at(_x, y) == turn) || // row y
      (x == y && rangeClosed(1, 3).allMatch(_x -> at(_x, _x) == turn)) || // 1st diagonal
      (x + y == 4 && rangeClosed(1, 3).allMatch(_x -> at(_x, 4 - _x) == turn)); // 2nd
  }

  @Override
  public @View String toString() {
    return rangeClosed(1, 3)
      .mapToObj(y -> rangeClosed(1, 3)
                     .mapToObj(x -> at(x, y).toString())
                     .collect(joining("|")))
      .collect(joining("\n-----\n"));
  }
}
