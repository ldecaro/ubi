package com.example.ubi.api.runtime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Example resource returns example.html directly at the root context.
 */
@Path("/")
public class ExampleResource {

    private static String HTML =   null;
	private static DAO dao = null;

    public ExampleResource(){
		if( ExampleResource.HTML == null ){
        	ExampleResource.HTML = getFile("com/example/ubi/api/runtime/example.html");
			dao = new DAO();
		}
    }
    
    /**
     * Method handling HTTP GET requests. The returned object will be sent
     * to the client as "text/plain" media type.
     *
     * @return String that will be returned as a text/plain response.
     */
    @GET
	@Produces(MediaType.TEXT_HTML)
    public String example() {

		String region = System.getenv("AWS_REGION");
		Integer index =	0;
		try{
			if(region == null){
				System.out.println("Region is not part of the environment. Using region us-east-1");
				region = "us-east-1";
			}
			index = dao.keepalive(region);
			System.out.println("Using index "+index);
		}catch(Exception t){
			System.out.println("Exception: "+t.getMessage());
			t.printStackTrace();
		}
        return ExampleResource.HTML.replace("REGION", region).replace("INDEX", index+"");
    }	

    /**
     * Method handling HTTP GET requests. The returned object will be sent
     * to the client as "text/plain" media type.
     *
     * @return String that will be returned as a text/plain response.
     */
    @GET
	@Produces(MediaType.TEXT_HTML)
	@Path("/demo")
    public String demo() {

		String region = System.getenv("AWS_REGION");
		Integer index =	0;
		try{
			if(region == null){
				System.out.println("Region is not part of the environment. Using region us-east-1");
				region = "us-east-1";
			}
			index = dao.demo(region);
			System.out.println("Using index "+index);
		}catch(Exception t){
			System.out.println("Exception: "+t.getMessage());
			t.printStackTrace();
		}
        return ExampleResource.HTML.replace("REGION", region).replace("INDEX", index+"");
    }		

    private String getFile(String filename) {
		
		byte[] bytes = null;		
		try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(filename);
			BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
			// Use resource
			final String fileAsText = reader.lines().collect(Collectors.joining());
			return fileAsText;
		}catch( IOException ioe) {
			System.out.println("ExampleResource::Cannot HTML file "+filename+". IOException:"+ioe.getMessage());
			ioe.printStackTrace();
			return "";
		}
	}
}