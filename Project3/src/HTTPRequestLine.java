import java.util.HashMap;

/**
HTTPRequestLine is a data structure that stores a Java representation of the parsed Request-Line.
 **/
public class HTTPRequestLine {

	private HTTPConstants.HTTPMethod method;
	private String uripath;
	private HashMap<String, String> parameters;
	private String httpversion;
	
    /*
      You are expected to add appropriate constructors/getters/setters to access and update the data in this class.
     */
	public HTTPRequestLine()
	{
		parameters = new HashMap<String, String>();
	}

	public HTTPConstants.HTTPMethod getMethod() {
		return method;
	}

	public void setMethod(HTTPConstants.HTTPMethod method) {
		this.method = method;
	}

	public String getUripath() {
		return uripath;
	}

	public void setUripath(String uripath) {
		this.uripath = uripath;
	}

	public HashMap<String, String> getParameters() {
		return parameters;
	}

	public void setParameters(HashMap<String, String> parameters) {
		this.parameters = parameters;
	}

	public String getHttpversion() {
		return httpversion;
	}

	public void setHttpversion(String httpversion) {
		this.httpversion = httpversion;
	}
	
}
