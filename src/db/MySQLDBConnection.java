package db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import model.Restaurant;

import org.json.JSONArray;
import org.json.JSONObject;

import yelp.YelpAPI;

public class MySQLDBConnection extends DBConnectionWithSearchMethod{
	private Connection conn = null;
	public MySQLDBConnection() {
		this(DBUtil.URL);
	}

	public MySQLDBConnection(String url) {
		try {
			// Forcing the class representing the MySQL driver to load and
			// initialize.
			// The newInstance() call is a work around for some broken Java
			// implementations
			Class.forName("com.mysql.jdbc.Driver").newInstance();
			conn = DriverManager.getConnection(url);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public JSONArray searchRestaurants(String userId, double lat, double lon, String term) {
		try {
			YelpAPI api = new YelpAPI();
			JSONObject response = new JSONObject(
					api.searchForBusinessesByLocation(lat, lon));
			JSONArray array = (JSONArray) response.get("businesses");

			List<JSONObject> list = new ArrayList<JSONObject>();
			Set<String> visited = getVisitedRestaurants(userId);

			for (int i = 0; i < array.length(); i++) {
				JSONObject object = array.getJSONObject(i);
				Restaurant restaurant = new Restaurant(object);
				String businessId = restaurant.getBusinessId();
				String name = restaurant.getName();
				String categories = restaurant.getCategories();
				String city = restaurant.getCity();
				String state = restaurant.getState();
				String fullAddress = restaurant.getFullAddress();
				double stars = restaurant.getStars();
				double latitude = restaurant.getLatitude();
				double longitude = restaurant.getLongitude();
				String imageUrl = restaurant.getImageUrl();
				String url = restaurant.getUrl();
				JSONObject obj = restaurant.toJSONObject();
				if (visited.contains(businessId)) {
					obj.put("is_visited", true);
				} else {
					obj.put("is_visited", false);
				}
				String sql = "INSERT IGNORE INTO restaurants VALUES (?,?,?,?,?,?,?,?,?,?,?)";
				PreparedStatement statement = conn.prepareStatement(sql);
				statement.setString(1, businessId);
				statement.setString(2, name);
				statement.setString(3, categories);
				statement.setString(4, city);
				statement.setString(5, state);
				statement.setDouble(6, stars);
				statement.setString(7, fullAddress);
				statement.setDouble(8, latitude);
				statement.setDouble(9, longitude);
				statement.setString(10, imageUrl);
				statement.setString(11, url);
				statement.execute();
				// Perform filtering if term is specified.
				if (term == null || term.isEmpty()) {
					list.add(obj);
				} else {
					if (categories.contains(term) || fullAddress.contains(term) || name.contains(term)) {
						list.add(obj);
					}
				}
			}
			return new JSONArray(list);
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
		return null;
	}

	@Override
	public void close() {
		if (conn != null) {
			try {
				conn.close();
			} catch (Exception e) { /* ignored */
			}
		}
	}
	@Override
	public void setVisitedRestaurants(String userId, List<String> businessIds) {
		String query = "INSERT INTO history (user_id, business_id) VALUES (?, ?)";
		try {
			PreparedStatement statement = conn.prepareStatement(query);
			for (String businessId : businessIds) {
				statement.setString(1,  userId);
				statement.setString(2, businessId);
				statement.execute();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	@Override
	public void unsetVisitedRestaurants(String userId, List<String> businessIds) {
		String query = "DELETE FROM history WHERE user_id = ? and business_id = ?";
		try {
			PreparedStatement statement = conn.prepareStatement(query);
			for (String businessId : businessIds) {
				statement.setString(1,  userId);
				statement.setString(2, businessId);
				statement.execute();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	@Override
	public Set<String> getVisitedRestaurants(String userId) {
		Set<String> visitedRestaurants = new HashSet<String>();
		try {
			String sql = "SELECT business_id from history WHERE user_id = ?";
			PreparedStatement statement = conn.prepareStatement(sql);
			statement.setString(1, userId);
			ResultSet rs = statement.executeQuery();
			while (rs.next()) {
				String visitedRestaurant = rs.getString("business_id");
				visitedRestaurants.add(visitedRestaurant);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return visitedRestaurants;
	}

	@Override
	public JSONObject getRestaurantsById(String businessId, boolean isVisited) {
		try {
			String sql = "SELECT * from restaurants where business_id = ?";
			PreparedStatement statement = conn.prepareStatement(sql);
			statement.setString(1, businessId);
			ResultSet rs = statement.executeQuery();
			if (rs.next()) {
				Restaurant restaurant = new Restaurant(
						rs.getString("business_id"), rs.getString("name"),
						rs.getString("categories"), rs.getString("city"),
						rs.getString("state"), rs.getFloat("stars"),
						rs.getString("full_address"), rs.getFloat("latitude"),
						rs.getFloat("longitude"), rs.getString("image_url"),
						rs.getString("url"));
				JSONObject obj = restaurant.toJSONObject();
				obj.put("is_visited", isVisited);
				return obj;
			}
		} catch (Exception e) { /* report an error */
			System.out.println(e.getMessage());
		}
		return null;

	}
	@Override
	public JSONArray recommendRestaurants(String userId) {
			if (conn == null) {
				return null;
			}
			return super.recommendRestaurantsCommon(userId);
	}
	
	@Override
	public Set<String> getCategories(String businessId) {
		Set<String> cat = new HashSet<String>();
		try{
			if (conn == null) {
				return cat;
			}
			String sql = "SELECT categories from restaurants WHERE business_id = ?";
			PreparedStatement statement = conn.prepareStatement(sql);
			statement.setString(1, businessId);
			ResultSet rs = statement.executeQuery();
			if (rs.next()) {
				String[] categories = rs.getString("categories").split(",");
				for (String category : categories) {
					cat.add(category.trim());
				}
			}
			return cat;
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
		return cat;
	}
	@Override
	public Set<String> getBusinessId(String category) {
		Set<String> cat = new HashSet<String>();
		try{
			if (conn == null) {
				return cat;
			}
			String sql = "SELECT business_id from restaurants WHERE categories LIKE ?";
			PreparedStatement statement = conn.prepareStatement(sql);
			statement.setString(1, "%" + category + "%");
			ResultSet rs = statement.executeQuery();
			while (rs.next()) {
				String id = rs.getString("business_id");
					cat.add(id.trim());
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
		return cat;
	}
	@Override
	public Boolean verifyLogin(String userId, String password) {
		try {
			if (conn == null) {
				return false;
			}

			String sql = "SELECT user_id from users WHERE user_id = ? and password = ?";
			PreparedStatement statement = conn.prepareStatement(sql);
			statement.setString(1, userId);
			statement.setString(2, password);
			ResultSet rs = statement.executeQuery();
			if (rs.next()) {
				return true;
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
		return false;
	}
	@Override
	public String getFirstLastName(String userId) {
		String name = "";
		try {
			if (conn != null) {
				String sql = "SELECT first_name, last_name from users WHERE user_id = ?";
				PreparedStatement statement = conn.prepareStatement(sql);
				statement.setString(1, userId);
				ResultSet rs = statement.executeQuery();
				if (rs.next()) {
					name += rs.getString("first_name") + " "
							+ rs.getString("last_name");
				}
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
		return name;
	}
}