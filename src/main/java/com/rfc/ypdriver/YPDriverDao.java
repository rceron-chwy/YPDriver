package com.rfc.ypdriver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.rfc.ypdriver.model.YPCategory;
import com.rfc.ypdriver.model.YPDish;
import com.rfc.ypdriver.model.YPRestaurantMenu;

public class YPDriverDao {

	public static Logger logger = LogManager.getLogger(YPDriverDao.class.getName());

	DBCollection collection;
	DBCollection errors;
	DBCollection nextPage;

	public YPDriverDao(final DB menudb) {
		collection = menudb.getCollection("menus");
		collection.ensureIndex(new BasicDBObject("mid", 1));

		errors = menudb.getCollection("menu.errors");

		nextPage = menudb.getCollection("next.page");
	}

	public boolean exists(Long id) {
		DBObject restaurant = collection.findOne(new BasicDBObject("mid", id));
		return restaurant != null;
	}

	public void saveError(Long id, String link, String errorMessage) {
		errors.insert(new BasicDBObject("mid", id).append("link", link).append("errorMessage", errorMessage));
	}

	public void saveNextPage(String nextPageLink) {
		nextPage.update(new BasicDBObject("_id", 1), new BasicDBObject("_id", 1).append("link", nextPageLink), true, false);
	}

	public String getNextPage() {
		DBObject np = nextPage.findOne();
		return (String) np.get("link");
	}

	public void save(YPRestaurantMenu restaurantMenu) {

		logger.info("Saving menu for: " + restaurantMenu.getId() + " - " + restaurantMenu.getName());

		BasicDBObject mmenu = new BasicDBObject();
		BasicDBList mdishes = new BasicDBList();

		for (YPCategory category : restaurantMenu.getCategories()) {
			for (YPDish dish : category.getDishes()) {
				BasicDBList categoriesTag = new BasicDBList();
				categoriesTag.add(category.getName());
				mdishes.add(new BasicDBObject("name", dish.getName()).append("description", dish.getDescription()).append("price", dish.getPrice())
						.append("categories", categoriesTag));
			}
		}

		mmenu.append("mid", restaurantMenu.getId());
		mmenu.append("name", restaurantMenu.getName());
		mmenu.append("address",
				new BasicDBObject("street", restaurantMenu.getStreet()).append("city", restaurantMenu.getCity()).append("state", restaurantMenu.getState())
						.append("zip", restaurantMenu.getZip()));
		mmenu.append("phone", restaurantMenu.getPhone());
		mmenu.append("dishes", mdishes);

		collection.insert(mmenu);
	}
}
