package br.com.smart.jmeter.plugins;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.GZIPInputStream;

import org.apache.commons.io.input.CountingInputStream;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;


/**
 * This class can simulate the parallel downloading of resources from a host in a jmeter script.
 * If your resources are resource intensive, this might be handfull but if the resources are light 
 * to serve, you might be over detailing your jmeter script.
 * @author Domingos
 *
 */
public class AsyncSampler extends AbstractJavaSamplerClient {

	
	private static final String PROTOCOL = "protocol";
	private static final String DEFAULT_PROTOCOL = "http";

	private static final String LABEL = "label";
	private static final String DEFAULT_LABEL = "MEULABEL";

	private static final String HOST = "host";
	private static final String DEFAULT_HOST = "${host}";
	private static final String PORT = "porta";
	private static final String DEFAULT_PORT = "${port}";
	private static final String THREADS = "threads";
	private static final String DEFAULT_THREADS = "4";
	private static final String URL_PREFIX = "prefixo_path";
	private static final String DEFAULT_URL_PREFIX = "path_";
	private static final String DEFAULT_URL = "/caminho/servlet?param1=${param1}&param2=${param2}";
	
	
	private static final String COOKIE_PREFIX = "cookie_prefix";
	private static final String DEFAULT_COOKIE_PREFIX = "COOKIE_";
	
	private static final String COOKIE_JSESSIONID = "COOKIE_JSESSIONID";
	private static final String DEFAULT_COOKIE_JSESSIONID = "${COOKIE_JSESSIONID}";
	private static final String COOKIE_PHPSESSID = "COOKIE_PHPSESSID";
	private static final String DEFAULT_COOKIE_PHPSESSID = "${COOKIE_PHPSESSID}";

	/**
	 * Provide a list of parameters which this test supports. Any parameter
	 * names and associated values returned by this method will appear in the
	 * GUI by default so the user doesn't have to remember the exact names. The
	 * user can add other parameters which are not listed here. If this method
	 * returns null then no parameters will be listed. If the value for some
	 * parameter is null then that parameter will be listed in the GUI with an
	 * empty value.
	 * 
	 * @return a specification of the parameters used by this test which should
	 *         be listed in the GUI, or null if no parameters should be listed.
	 */
	@Override
	public Arguments getDefaultParameters() {
		Arguments params = new Arguments();
		params.addArgument(LABEL, DEFAULT_LABEL);
		params.addArgument(HOST, DEFAULT_HOST);
		params.addArgument(PORT, DEFAULT_PORT);
		params.addArgument(PROTOCOL, DEFAULT_PROTOCOL);
		params.addArgument(THREADS, DEFAULT_THREADS);
		params.addArgument(URL_PREFIX, DEFAULT_URL_PREFIX);
		params.addArgument(COOKIE_JSESSIONID, DEFAULT_COOKIE_JSESSIONID);
		params.addArgument(COOKIE_PHPSESSID, DEFAULT_COOKIE_PHPSESSID);
		
		for (int i = 1; i < 101; i++) {
			params.addArgument(DEFAULT_URL_PREFIX + i, DEFAULT_URL);	
		}
		
		return params;
		
	}

	@Override
	public SampleResult runTest(JavaSamplerContext context) {

		String label = context.getParameter(LABEL);
		String protocol = context.getParameter(PROTOCOL);
		String host = context.getParameter(HOST);
		int port = context.getIntParameter(PORT);
		int threads = context.getIntParameter(THREADS);
		String url_prefix = context.getParameter(URL_PREFIX, DEFAULT_URL_PREFIX);
		String cookie_prefix = context.getParameter(COOKIE_PREFIX, DEFAULT_COOKIE_PREFIX);
		
		StringBuffer sb = new StringBuffer();
		
		//searching all COOKIE_ parameter to be send
		boolean first = true;
		Iterator<String> iter = context.getParameterNamesIterator();
		while (iter.hasNext()) {
			String name = iter.next();
			if (name.startsWith(cookie_prefix)) {
				String value = context.getParameter(name, null);
				if(value!= null && !value.startsWith("${")){	
					if(first){
						first = false;
					} else {
						sb.append("; ");
					}
					sb.append(name.substring(cookie_prefix.length()));
					sb.append("=");
					sb.append(value);
				}
			}
		}
		
		final String cookies = sb.toString();
		//"JSESSIONID="+context.getParameter(COOKIE_JSESSIONID) + "; PHPSESSID=" + context.getParameter(COOKIE_PHPSESSID) ;

		ExecutorService newFixedThreadPool = Executors
				.newFixedThreadPool(threads);
		
		Collection<Callable<RequestResponse>> resourcesToDownload = new LinkedList<Callable<RequestResponse>>();

		iter = context.getParameterNamesIterator();
		SampleResult results = new SampleResult();

		results.setSampleLabel(label);

		while (iter.hasNext()) {
			String name = iter.next();
			if (name.startsWith(url_prefix)) {
				try {
					String path = context.getParameter(name);
					if(path == null || path.equals(DEFAULT_URL)){
						continue;
					}
					if (!path.startsWith("/")) {
						path = "/" + path;
					}
					final URL url = new URL(protocol, host, port, path);
					

					resourcesToDownload.add(new Callable<RequestResponse>() {
						@Override
						public RequestResponse call() throws Exception {
							RequestResponse toReturn = new RequestResponse();

							try {

								toReturn.setStart();
								URLConnection openConnection = url
										.openConnection();
								
								openConnection.setRequestProperty("Cookie", cookies);
								
								openConnection.connect();
								boolean gzipped = "gzip".equals(openConnection
										.getContentEncoding());
								InputStream instream = null;

								instream = new CountingInputStream(
										openConnection.getInputStream());
								BufferedInputStream in;
								if (gzipped) {
									in = new BufferedInputStream(
											new GZIPInputStream(instream));
								} else {
									in = new BufferedInputStream(instream);
								}

								byte[] buffer = new byte[1024];
								int readed;
								int counter = 0;
								while ((readed = in.read(buffer)) > 0) {
									counter += readed;
								}
								in.close();

								toReturn.setEnd();
								toReturn.setSize(counter);
							} catch (Exception e) {
								e.printStackTrace();
								toReturn.setError();
							}

							return toReturn;
						}
					});

				} catch (MalformedURLException e1) {

					results.setSuccessful(false);
					results.setSamplerData(e1.getMessage());

					return results;
				}
			}
		}

		// Record sample start time.
		results.sampleStart();
		results.setSuccessful(true);
		try {

			List<Future<RequestResponse>> list = newFixedThreadPool
					.invokeAll(resourcesToDownload);

			int transfered = 0;
			for (Future<RequestResponse> future : list) {
				RequestResponse response = future.get();
				transfered += response.bytes;
				if (!response.success) {
					results.setSuccessful(false);
				}
			}
			results.setBytes(transfered);

		} catch (InterruptedException e) {
			results.setSuccessful(true);
		} catch (Exception e) {
			results.setSuccessful(false);
		} finally {
			// Record end time and populate the results.
			results.sampleEnd();
			newFixedThreadPool.shutdownNow();
		}

		return results;
	}

	public static class RequestResponse {

		long start;
		long end;
		boolean success = true;
		int bytes;
	
		
		public void setError() {
			success = false;
		}

		public void setSize(int bytes) {
			this.bytes = bytes;

		}

		public void setStart() {
			start = System.currentTimeMillis();
		}

		public void setEnd() {
			end = System.currentTimeMillis();
		}
	}
}
