package com.rfc.ypdriver.model;

public class YPDish {

	private String name;
	private String description;
	private String price;

	public YPDish(String name, String description, String price) {
		super();
		this.name = name;
		this.description = description;
		this.price = price;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public String getPrice() {
		return price;
	}
}