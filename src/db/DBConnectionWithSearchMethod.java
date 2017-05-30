package db;

import java.util.HashSet;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

public abstract class DBConnectionWithSearchMethod implements DBConnection{
	
	protected static final int MAX_RECOMMENDED_RESTAURANTS = 10;

	public JSONArray recommendRestaurantsCommon(String userId) {
		try {

			Set<String> visitedRestaurants = getVisitedRestaurants(userId);//step 1
			Set<String> allCategories = new HashSet<>();// why hashSet? //step 2
			for (String restaurant : visitedRestaurants) {
				allCategories.addAll(getCategories(restaurant));
			}
			Set<String> allRestaurants = new HashSet<>();//step 3
			for (String category : allCategories) {
				Set<String> set = getBusinessId(category);
				allRestaurants.addAll(set);
			}
			Set<JSONObject> diff = new HashSet<>();//step 4
			int count = 0;
			for (String businessId : allRestaurants) {
				// Perform filtering
				if (!visitedRestaurants.contains(businessId)) {
					diff.add(getRestaurantsById(businessId, false));
					count++;
					if (count >= MAX_RECOMMENDED_RESTAURANTS) {
						break;
					}
				}
			}
			return new JSONArray(diff);
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
		return null;
	}
}
