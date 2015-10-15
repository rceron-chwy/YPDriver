package com.rfc.ypdriver.model;

import java.util.ArrayList;
import java.util.List;

public class YPRestaurantMenu {

	private Long id;
	private String name;
	private String street;
	private String city;
	private String state;
	private String zip;
	private String phone;

	private List<YPCategory> categories;

	public YPRestaurantMenu(Long id, String name, String street, String city, String state, String zip, String phone) {
		super();
		this.id = id;
		this.name = name;
		this.street = street;
		this.city = city;
		this.state = state;
		this.zip = zip;
		this.phone = phone;
		this.categories = new ArrayList<YPCategory>();
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}
	
	public String getStreet() {
		return street;
	}

	public String getCity() {
		return city;
	}

	public String getState() {
		return state;
	}

	public String getZip() {
		return zip;
	}

	public String getPhone() {
		return phone;
	}

	public List<YPCategory> getCategories() {
		return categories;
	}

	public void addCategory(YPCategory category) {
		this.categories.add(category);
	}
}
