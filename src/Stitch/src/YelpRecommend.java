/**
 * Created by smoeller on 4/3/2016.
 *
 * Usage:
 * JSONObject results = queryAPI((String) "Jeans", 38.952508, -94.719309);
 * System.out.println("Best match business: " + results.toString());
 * Results:
 * Best match business: {"coordinate":{"latitude":"38.95641","longitude":"-94.71533"},"name":"Nordstrom"}
 */


import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.json.simple.parser.JSONParser;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.simple.parser.ParseException;
import org.scribe.builder.ServiceBuilder;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verb;
import org.scribe.oauth.OAuthService;


public class YelpRecommend {
    private static final String API_HOST = "api.yelp.com";
    private static final int SEARCH_LIMIT = 1;
    private static final String SEARCH_PATH = "/v2/search";
    private static final String BUSINESS_PATH = "/v2/business";
    private static final int RADIUS_FILTER_METERS = 500;
    private static final int SORT_METHOD = 2; // 0=Best matched, 1=Distance, 2=Highest Rated

    /*
     * Update OAuth credentials below from the Yelp Developers API site:
     * http://www.yelp.com/developers/getting_started/api_access
     */
    private static final String CONSUMER_KEY = "1O--WNDMuUMkL5j4PIQ_8A";
    private static final String CONSUMER_SECRET = "w5V7YGm4ilCfEOABFBWYmC_nSf4";
    private static final String TOKEN = "gC3r5o6zuP8xDbdvzhU42wCRLVXVKX__";
    private static final String TOKEN_SECRET = "KsVoR6JnKGmj68FFY-ta3DKXqA0";

    OAuthService service;
    Token accessToken;

    /**
     * Setup the Yelp API OAuth credentials.
     *
     * @param consumerKey Consumer key
     * @param consumerSecret Consumer secret
     * @param token Token
     * @param tokenSecret Token secret
     */
    public YelpRecommend(String consumerKey, String consumerSecret, String token, String tokenSecret) {
        this.service =
                new ServiceBuilder().provider(TwoStepOAuth.class).apiKey(consumerKey)
                        .apiSecret(consumerSecret).build();
        this.accessToken = new Token(token, tokenSecret);
    }

    /**
     * Creates and sends a request to the Search API by term and location.
     * <p>
     * See <a href="http://www.yelp.com/developers/documentation/v2/search_api">Yelp Search API V2</a>
     * for more info.
     *
     * @param term <tt>String</tt> of the search term to be queried
     * @param location <tt>String</tt> of the location
     * @return <tt>String</tt> JSON Response
     */
    public String searchForBusinessesByLocation(String term, String location) {
        OAuthRequest request = createOAuthRequest(SEARCH_PATH);
        request.addQuerystringParameter("term", term);
        String encodedLoc = location.replaceAll("\\s", "+");
        //request.addQuerystringParameter("location", location);
        request.addQuerystringParameter("ll", location);
        request.addQuerystringParameter("limit", String.valueOf(SEARCH_LIMIT));
        request.addQuerystringParameter("radius_filter", String.valueOf(RADIUS_FILTER_METERS));
        request.addQuerystringParameter("sort", String.valueOf(SORT_METHOD));
        //System.out.println("searchForBusinessesByLocation: request parameters: " + request.toString());
        return sendRequestAndGetResponse(request);
    }

    /**
     * Creates and sends a request to the Business API by business ID.
     * <p>
     * See <a href="http://www.yelp.com/developers/documentation/v2/business">Yelp Business API V2</a>
     * for more info.
     *
     * @param businessID <tt>String</tt> business ID of the requested business
     * @return <tt>String</tt> JSON Response
     */
    public String searchByBusinessId(String businessID) {
        OAuthRequest request = createOAuthRequest(BUSINESS_PATH + "/" + businessID);
        return sendRequestAndGetResponse(request);
    }

    /**
     * Creates and returns an {@link OAuthRequest} based on the API endpoint specified.
     *
     * @param path API endpoint to be queried
     * @return <tt>OAuthRequest</tt>
     */
    private OAuthRequest createOAuthRequest(String path) {
        OAuthRequest request = new OAuthRequest(Verb.GET, "https://" + API_HOST + path);
        return request;
    }

    /**
     * Sends an {@link OAuthRequest} and returns the {@link Response} body.
     *
     * @param request {@link OAuthRequest} corresponding to the API request
     * @return <tt>String</tt> body of API response
     */
    private String sendRequestAndGetResponse(OAuthRequest request) {
        //System.out.println("Querying " + request.getUrl() + " ...");
        this.service.signRequest(this.accessToken, request);
        //System.out.println("Request: " + request.toString());
        Response response = request.send();
        //System.out.println("Response body: " + response.getBody().toString());
        return response.getBody();
    }

    /**
     * Queries the Search API based on the command line arguments and takes the first result to query
     * the Business API.
     *
     * @param term <tt>term</tt> search term
     */
    private static JSONObject queryAPI(String term, double lat, double lon) {
        YelpRecommend yelpApi = new YelpRecommend(CONSUMER_KEY, CONSUMER_SECRET, TOKEN, TOKEN_SECRET);
        String location = String.valueOf(lat)+","+String.valueOf(lon);
        String searchResponseJSON =
                yelpApi.searchForBusinessesByLocation(term, location);

        JSONParser parser = new JSONParser();
        org.json.simple.JSONObject response = null;
        try {
            response = (org.json.simple.JSONObject) parser.parse(searchResponseJSON);
            //System.out.println("queryAPI: response dump: " + response.toString());
        } catch (ParseException pe) {
            System.out.println("queryAPI: Error: could not parse JSON response: " + searchResponseJSON.toString());
            System.exit(1);
        }

        org.json.simple.JSONObject region = (org.json.simple.JSONObject) response.get("region");
        //System.out.println("queryAPI: region dump: " + region.toString());

        org.json.simple.JSONArray businesses = new org.json.simple.JSONArray();
        try {
            businesses = (org.json.simple.JSONArray) response.get("businesses");
            //System.out.println("queryAPI: businesses dump: " + businesses.toString());
        } catch(NullPointerException npe) {
            System.out.println("queryAPI: response dump: " + response.toString());
            System.exit(1);
        }

        JSONObject bestMatch = new JSONObject();
        org.json.simple.JSONObject firstBusiness = null;

        try {
            firstBusiness = (org.json.simple.JSONObject) businesses.get(0);
            String firstBusinessID = firstBusiness.get("id").toString();

            // Select the first business and display business details
            String businessResponseJSON = yelpApi.searchByBusinessId(firstBusinessID.toString());
        } catch (NullPointerException npe) {
            System.out.println("queryAPI: Null pointer found1");
            System.exit(1);
        } catch (IndexOutOfBoundsException ibe) {
            //System.out.println("queryAPI: No records found");
            bestMatch.put("error", "No records found");
            return bestMatch;
        }

        try {
            org.json.simple.JSONObject firstLocation = (org.json.simple.JSONObject) firstBusiness.get("location");
            org.json.simple.JSONObject firstCoordinate = (org.json.simple.JSONObject) firstLocation.get("coordinate");
            bestMatch.put("name", firstBusiness.get("name").toString());
            JSONObject thisCoordinate = new JSONObject();
            thisCoordinate.put("latitude", firstCoordinate.get("latitude").toString());
            thisCoordinate.put("longitude", firstCoordinate.get("longitude").toString());
            bestMatch.put("coordinate", thisCoordinate);
        } catch (NullPointerException npe) {
            System.out.println("queryAPI: Null pointer found2");
            System.exit(1);
        }

        return bestMatch;
    }

    /**
     * Main entry for sample Yelp API requests.
     * <p>
     * After entering your OAuth credentials, execute <tt><b>run.sh</b></tt> to run this example.
     */
    public static void main(String[] args) {
        //YelpRecommend yelpApi = new YelpRecommend(CONSUMER_KEY, CONSUMER_SECRET, TOKEN, TOKEN_SECRET);
        JSONObject results = queryAPI((String) "Jeans", 38.952508, -94.719309);
        System.out.println("Best match business: " + results.toString());
    }
}
