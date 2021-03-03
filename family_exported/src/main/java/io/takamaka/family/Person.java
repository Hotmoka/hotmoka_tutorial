/*
    A storage class example in Takamaka.
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

package io.takamaka.family;

import io.takamaka.code.lang.Exported;
import io.takamaka.code.lang.Storage;

@Exported
public class Person extends Storage {
	private final String name;
	private final int day;
	private final int month;
	private final int year;
	public final Person parent1;
	public final Person parent2;

	public Person(String name, int day, int month, int year,
			      Person parent1, Person parent2) {

		this.name = name;
		this.day = day;
		this.month = month;
		this.year = year;
		this.parent1 = parent1;
		this.parent2 = parent2;
	}

	public Person(String name, int day, int month, int year) {
		this(name, day, month, year, null, null);
	}

	@Override
	public String toString() {
		return name + " (" + day + "/" + month + "/" + year + ")";
	}
}