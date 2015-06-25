import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;


public class HTTPRequestLineParser {

	/**
	 * This method takes as input the Request-Line exactly as it is read from the socket.
	 * It returns a Java object of type HTTPRequestLine containing a Java representation of
	 * the line.
	 *
	 * The signature of this method may be modified to throw exceptions you feel are appropriate.
	 * The parameters and return type may not be modified.
	 *
	 * 
	 * @param line
	 * @return
	 */
	public static HTTPRequestLine parse(String line) {
	    //A Request-Line is a METHOD followed by SPACE followed by URI followed by SPACE followed by VERSION
	    //A VERSION is 'HTTP/' followed by 1.0 or 1.1
	    //A URI is a '/' followed by PATH followed by optional '?' PARAMS 
	    //PARAMS are of the form key'='value'&'

		HTTPRequestLine request = new HTTPRequestLine();
		
		String[] tokens = line.split(" ");
		if (tokens.length != 3)
		{	
			return request;
		}
				
		if (tokens[0].equals(HTTPConstants.HTTPMethod.POST.toString()))
		{
			request.setMethod(HTTPConstants.HTTPMethod.POST);
		}
		else if (tokens[0].equals(HTTPConstants.HTTPMethod.GET.toString()))
		{
			request.setMethod(HTTPConstants.HTTPMethod.GET);
		}
		else if (tokens[0].equals(HTTPConstants.HTTPMethod.DELETE.toString()))
		{
			request.setMethod(HTTPConstants.HTTPMethod.DELETE);
		}

		request.setUripath(tokens[1]);
		request.setHttpversion(tokens[2]);
		
		if (request.getUripath().contains("?"))
		{
			try {
				URL url = new URL("http:/" + tokens[1]);
				String query = url.getQuery();
			    String[] pairs = query.split("&");
			    for (String pair : pairs) {
			        int idx = pair.indexOf("=");
			        if (idx != -1)
			        {
			        	request.getParameters().put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"), URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
			        }
			    }
			} catch (UnsupportedEncodingException e1) {
				e1.printStackTrace();
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}
		}
		
		return request;
	}
		
}
