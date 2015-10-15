package com.rfc.ypdriver;

import java.net.URL;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.firefox.internal.ProfilesIni;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.mongodb.DB;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.rfc.ypdriver.model.YPCategory;
import com.rfc.ypdriver.model.YPDish;
import com.rfc.ypdriver.model.YPRestaurantMenu;

/**
 * Yellow Pages WebDriver
 * 
 * http://www.singleplatform.com/
 * https://dev.locu.com/
 * 
 * @author rafael.ceron
 */
public class YPMainDriver {

	private static final Logger logger = LogManager.getLogger(YPMainDriver.class.getName());
	private static final Pattern p = Pattern.compile("^showing\\s*\\d+\\s*-\\s*(\\d+)\\s*of\\s*(\\d+)\\s*results\\s*$", Pattern.CASE_INSENSITIVE
			| Pattern.DOTALL);
	private static final int THREADS = 3;
	private static final int TIMEOUT = 60;

	private RemoteWebDriver mainDriver;
	private BlockingQueue<RemoteWebDriver> queue;
	private YPDriverDao dao;
	private final ExecutorService pool;

	private MongoClient mongoClient;

	public YPMainDriver() {
		logger.info("Starting driver!!");
		pool = Executors.newFixedThreadPool(THREADS);
		try {
			mongoClient = new MongoClient(new MongoClientURI("mongodb://localhost"));
			dao = new YPDriverDao(mongoClient.getDB("menudb"));
		} catch (UnknownHostException e) {
			logger.error("Error: ", e);
		}
	}

	private void startDrivers() throws Exception {
		logger.info("Starting new drivers.. Time to work!");

		FirefoxProfile profile = new ProfilesIni().getProfile("default");
		//FirefoxProfile profile = new FirefoxProfile();
		DesiredCapabilities dc = DesiredCapabilities.firefox();
		dc.setCapability(FirefoxDriver.PROFILE, profile);

		mainDriver = new RemoteWebDriver(new URL("http://127.0.0.1:4444/wd/hub"), dc);
		mainDriver.manage().timeouts().pageLoadTimeout(TIMEOUT, TimeUnit.SECONDS);

		queue = new ArrayBlockingQueue<RemoteWebDriver>(THREADS);
		for (int i = 0; i < THREADS; i++) {
			RemoteWebDriver auxDriver = new RemoteWebDriver(new URL("http://127.0.0.1:4444/wd/hub"), dc);
			auxDriver.manage().timeouts().pageLoadTimeout(TIMEOUT, TimeUnit.SECONDS);
			queue.put(auxDriver);
		}

		final MongoClient mongoClient = new MongoClient(new MongoClientURI("mongodb://localhost"));
		final DB menudb = mongoClient.getDB("menudb");
		dao = new YPDriverDao(menudb);
	}

	private void releaseDrivers() {
		logger.info("Releasing all drivers.... ");
		try {
			mainDriver.quit();
		} catch (Exception e) {
			logger.warn("Main driver failed to quit: " + mainDriver.getSessionId());
		}
		for (RemoteWebDriver auxDriver : queue) {
			try {
				auxDriver.quit();
			} catch (Exception e) {
				logger.warn("Aux Driver failed to quit: " + auxDriver.getSessionId());
			}
		}
	}

	private void drive() throws Exception {

		int coffeeBreak = 0;

		while (hasNext()) {

			List<WebElement> menuLinks = mainDriver.findElements(By.className("menu-link"));
			if (menuLinks != null && !menuLinks.isEmpty()) {
				CountDownLatch startSignal = new CountDownLatch(1);
				Semaphore semaphore = new Semaphore(0);
				int numberOfMenus = 0;
				for (WebElement mlink : menuLinks) {
					try {
						pool.execute(this.new MenuExtractor(mlink.getAttribute("href"), queue, semaphore, startSignal));
						numberOfMenus++;
					} catch (Exception e) {
						logger.error("Failed to Save menu: " + mlink, e);
					}
				}

				//Thread t = new Thread(new KeepMainAlive(mainDriver));
				//t.start();
				logger.info("Number of Threads Waiting to start: " + numberOfMenus);
				startSignal.countDown();
				semaphore.acquire(numberOfMenus);
				//t.interrupt();
			}

			logger.info("======================================================================");
			logger.info("PAGE DONE!!!");
			logger.info("----------------------------------------------------------------------");

			String nextPageLink = mainDriver.findElementByCssSelector(".ca .pagination .next a").getAttribute("href");
			coffeeBreak++;

			if (coffeeBreak == 30) {
				logger.info("Coffee Break! Sleeping for 1 minutes at: " + new Date());
				coffeeBreak = 0;
				releaseDrivers();
				Thread.sleep(60 * 1000); //1 minutes
				startDrivers();
			} else {
				logger.info("Not time for a break yet [" + coffeeBreak + "] sleeping for 1 seconds at: " + new Date());
				Thread.sleep(500);
			}
			logger.info("======================================================================");

			dao.saveNextPage(nextPageLink);
		}
	}

	public void shutdown() throws InterruptedException {
		pool.shutdown();
		while (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
			logger.info("Awaiting completion of threads.");
		}
	}

	private boolean hasNext() throws Exception {

		boolean proceed = false;
		WebElement results = null;
		String nextPageLink = dao.getNextPage();
		do {
			try {
				logger.info("Loading next page: " + nextPageLink);

				mainDriver.get(nextPageLink);
				results = (new WebDriverWait(mainDriver, TIMEOUT)).until(ExpectedConditions.presenceOfElementLocated(By.className("result-totals")));

				String textTotal = results.getText();
				Matcher m = p.matcher(textTotal);
				if (m.find()) {
					proceed = Integer.parseInt(m.group(2)) > Integer.parseInt(m.group(1));
					logger.info("Currently scrapping records [" + m.group(1) + "] from the total of [" + m.group(2) + "]");
				}
			} catch (Throwable e) {
				logger.error("Timeout loading next page or getting results.. Will retry!");
				results = null;
				releaseDrivers();
				startDrivers();
			}

			//Thread.sleep(1000);
		} while (results == null);

		return proceed;
	}

	private class KeepMainAlive implements Runnable {

		private RemoteWebDriver driver;

		public KeepMainAlive(RemoteWebDriver driver) {
			this.driver = driver;
		}

		public void run() {

			for (;;) {
				try {
					logger.info("pinging main driver...");
					driver.navigate().refresh();
					Thread.sleep(60000);
				} catch (Exception e) {
					logger.warn("Main driver keep alive: " + e.getMessage());
				}
			}
		}
	}

	private class MenuExtractor implements Runnable {

		private String menu;
		private BlockingQueue<RemoteWebDriver> queue;
		private Semaphore semaphore;
		private CountDownLatch startSignal;

		public MenuExtractor(final String menu, final BlockingQueue<RemoteWebDriver> q, Semaphore s, CountDownLatch c) {
			this.menu = menu;
			this.queue = q;
			this.semaphore = s;
			this.startSignal = c;
		}

		public void run() {

			RemoteWebDriver auxDriver = null;
			Long id = null;

			try {

				//logger.info("Waiting to START!! Thread: " + Thread.currentThread().getId() + " - " + Thread.currentThread().getName());
				startSignal.await();

				//logger.info("Starting Thread: " + Thread.currentThread().getId() + " - " + Thread.currentThread().getName());
				auxDriver = queue.take();

				String[] sp = menu.split("/");
				id = Long.parseLong(sp[sp.length - 2]);

				//check if menu already exists!!
				if (!dao.exists(id)) {

					logger.info("Getting menu from: " + menu);
					auxDriver.get(menu);

					WebElement scrollWrapper = null;
					do {
						try {
							scrollWrapper = (new WebDriverWait(auxDriver, 10))
									.until(ExpectedConditions.presenceOfElementLocated(By.className("scroll-wrapper")));
						} catch (TimeoutException e) {
							logger.error("Timeout getting menu scroll-wrapper!!! Retrying..");
							auxDriver.navigate().refresh();
						}
					} while (scrollWrapper == null);

					String name = null;
					String street = null;
					String city = null;
					String state = null;
					String zip = null;
					String phone = null;

					WebElement listName = auxDriver.findElement(By.className("listing-name"));
					if (listName != null) {
						name = listName.getText();
					} else {
						name = auxDriver.getTitle();
					}

					WebElement contactSection = auxDriver.findElement(By.className("contact"));
					List<WebElement> contactSpans = contactSection.findElements(By.xpath("./span"));
					if (contactSpans != null && !contactSpans.isEmpty()) {
						for (WebElement span : contactSpans) {
							String cssClass = StringUtils.trimToNull(span.getAttribute("class"));

							if (cssClass != null) {
								if ("street-address".equals(cssClass)) {
									street = span.getText();
								} else if ("postal-code".equals(cssClass)) {
									zip = span.getText();
								}
							} else {
								try {
									String[] cityState = span.getText().split(",");
									city = cityState[0].trim();
									state = cityState[1].trim();
								} catch (IndexOutOfBoundsException e) {
									logger.warn("City/State Index error.. for menu: " + menu);
								}
							}
						}

						try {
							WebElement phoneSpan = contactSection.findElement(By.className("phone"));
							if (phoneSpan != null) {
								phone = phoneSpan.getText();
							}
						} catch (NoSuchElementException e) {
							logger.warn("No phone # for: " + menu);
						}
					}

					logger.info("New Restaurant: [" + id + "] - [" + name + "] - [" + street + "] - [" + city + "] - [" + state + "] - [" + zip + "] - ["
							+ phone + "]");
					YPRestaurantMenu restaurantMenu = new YPRestaurantMenu(id, name, street, city, state, zip, phone);

					List<WebElement> children = scrollWrapper.findElements(By.xpath("./*"));

					YPCategory category = null;
					for (WebElement child : children) {

						if ("header".equals(child.getTagName()) && child.getAttribute("class").equals("category")) {
							category = new YPCategory(child.getText());
							restaurantMenu.addCategory(category);

						} else if ("ul".equals(child.getTagName())) {

							List<WebElement> dishes = child.findElements(By.xpath("./li"));

							for (WebElement dish : dishes) {
								if (dish.getAttribute("class").equals("description")
										|| (dish.findElement(By.cssSelector("#mip.menu #menu ul .title-price")) != null && StringUtils.isEmpty(dish
												.findElement(By.cssSelector("#mip.menu #menu ul .title-price")).getText()))) {
									continue;
								}

								WebElement titlePrice = dish.findElement(By.cssSelector("#mip.menu #menu ul .title-price"));
								WebElement description = dish.findElement(By.cssSelector("#mip.menu #menu ul .item-description"));

								String title = titlePrice.findElement(By.tagName("strong")).getText();
								String price = titlePrice.findElement(By.tagName("span")).getText();
								String descr = description.getText();

								category.addDish(new YPDish(title, descr, price));
							}
						}
					}

					dao.save(restaurantMenu);
					//					Thread.sleep(500);
				} else {
					logger.info("Menu [" + id + "] already saved..");
				}
			} catch (Exception e) {
				logger.error("Error saving menu: " + menu);
				dao.saveError(id, menu, e.getMessage());
			} finally {
				try {
					semaphore.release();
					queue.put(auxDriver);
				} catch (InterruptedException e) {
					logger.error(e);
				}
			}
		}
	}

	public static void main(String[] args) {
		YPMainDriver driver = new YPMainDriver();
		try {
			boolean finished = false;
			do {
				driver.startDrivers();
				try {
					driver.drive();
					finished = true;
				} catch (Exception ei) {
					logger.error("Error driving BUT we'll NOT give up!");
					logger.error(ei.getMessage());
					finished = false;
				} finally {
					driver.releaseDrivers();
				}
				logger.info("Let's take a break for 1 minutes before we retry tho.. : " + new Date());
				Thread.sleep(60 * 1000);
			} while (!finished);
			driver.shutdown();
		} catch (Exception e) {
			logger.error("Ouch.. Now something BAD really happened.", e);
		}
	}
}
