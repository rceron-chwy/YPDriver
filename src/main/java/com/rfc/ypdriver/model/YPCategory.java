package com.rfc.ypdriver.model;

import java.util.ArrayList;
import java.util.List;

public class YPCategory {

	private String name;

	private List<YPDish> dishes;

	public YPCategory(String name) {
		super();
		this.name = name;
		this.dishes = new ArrayList<YPDish>();
	}

	public void addDish(YPDish dish) {
		this.dishes.add(dish);
	}

	public String getName() {
		return this.name;
	}

	public List<YPDish> getDishes() {
		return dishes;
	}
}