package io.takamaka.family;

import io.takamaka.code.lang.Storage;

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