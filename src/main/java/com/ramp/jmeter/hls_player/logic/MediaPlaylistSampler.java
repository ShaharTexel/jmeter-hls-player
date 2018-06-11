package com.ramp.jmeter.hls_player.logic;

import org.apache.jmeter.protocol.http.control.CacheManager;
import org.apache.jmeter.protocol.http.control.CookieManager;
import org.apache.jmeter.protocol.http.control.HeaderManager;
import org.apache.jmeter.protocol.http.util.HTTPConstants;
import org.apache.jmeter.samplers.AbstractSampler;
import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.property.CollectionProperty;
import org.apache.jmeter.testelement.property.JMeterProperty;
import org.apache.jmeter.testelement.property.TestElementProperty;
import org.apache.jorphan.logging.LoggingManager;
import org.apache.log.Logger;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.lang.Math;
import java.lang.Float;
import java.lang.Thread;

public class MediaPlaylistSampler extends AbstractSampler {
    public static final String HEADER_MANAGER = "HLSRequest.header_manager"; // $NON-NLS-1$
    public static final String COOKIE_MANAGER = "HLSRequest.cookie_manager"; // $NON-NLS-1$
    public static final String CACHE_MANAGER = "HLSRequest.cache_manager"; // $NON-NLS-1$
	private static final Logger log = LoggingManager.getLoggerForClass();

	public static final String CUSTOM = "CUSTOM";
	public static final String MIN = "MIN";
	public static final String MAX = "MAX";

	public static final String MASTER_PLAYLIST_URL = "HLS.MASTER_PLAYLIST_URL";
	public static final String MEDIA_PLAYLIST_TYPE = "HLS.MEDIA_PLAYLIST_TYPE";
	public static final String IS_CUSTOM_DURATION = "HLS.IS_CUSTOM_DURATION";
	public static final String CUSTOM_DURATION = "HLS.CUSTOM_DURATION";
	//Video
	public static final String RESOLUTION_OPTION = "HLS.RESOLUTION_OPTION";
	public static final String CUSTOM_RESOLUTION = "HLS.CUSTOM_RESOLUTION";
	public static final String BANDWIDTH_OPTION = "HLS.BANDWIDTH_OPTION";
	public static final String CUSTOM_BANDWIDTH = "HLS.CUSTOM_BANDWIDTH";
	//Audio
	public static final String CUSTOM_AUDIO = "HLS.CUSTOM_AUDIO";
	//Closed Captions
	public static final String CUSTOM_CC = "HLS.CUSTOM_CC";
	public static final String TYPE_VIDEO = "Video";
	public static final String TYPE_AUDIO = "Audio";
	public static final String TYPE_CLOSED_CAPTIONS = "Closed Captions";


	private Parser parser;

    private int durationSeconds = 0; // configured video duration

    private DataRequest masterResponse = null;
    private SampleResult masterResult = null;

    private String playlistPath = null;
    private String playlist = null;
    
    private DataRequest playlistResponse = null;
    private SampleResult playlistResult = null;
	
    private boolean isLive = false; // does not have an end tag
    private int targetDuration = 0; // HLS target duration

    private long startTimeMillis = 0; // system time when the video started
    private long lastTimeMillis = 0; // system millis - time of last transaction
    private float playedSeconds = 0; // seconds played since start
    
    private float lastDurationSeconds = 0;
    	    
    private List<DataSegment> segments = new ArrayList<>();
    private ArrayList<String> segmentsFetched = new ArrayList<>();

    public MediaPlaylistSampler() {
		super();
		setName("HLS Media Playlist Sampler");
		parser = new Parser();
    }

    public HeaderManager getHeaderManager() {
	return (HeaderManager) getProperty(MediaPlaylistSampler.HEADER_MANAGER).getObjectValue();
    }

    private DataRequest getMasterList(SampleResult masterResult, Parser parser) throws IOException {

	masterResult.sampleStart();
	DataRequest response = parser.getBaseUrl(new URL(getURLData()), masterResult, true);
	masterResult.sampleEnd();
	
	masterResult.setRequestHeaders(response.getRequestHeaders() + "\n\n" + getCookieHeader(getURLData()) + "\n\n"
				       + getRequestHeader(this.getHeaderManager()));
	masterResult.setSuccessful(response.isSuccess());
	masterResult.setResponseMessage(response.getResponseMessage());
	masterResult.setSampleLabel("master");
	masterResult.setResponseHeaders(response.getHeadersAsString());
	masterResult.setResponseData(response.getResponse().getBytes());
	masterResult.setResponseCode(response.getResponseCode());
	masterResult.setContentType(response.getContentType());
	masterResult.setBytes(masterResult.getBytesAsLong() + (long) masterResult.getRequestHeaders().length());

	int headerBytes = masterResult.getResponseHeaders().length() // condensed
	    // length
	    // (without
	    // \r)
	    + response.getHeaders().size() // Add \r for each header
	    + 1 // Add \r for initial header
	    + 2; // final \r\n before data

	masterResult.setHeadersSize((int) headerBytes);
	masterResult.setSentBytes(response.getSentBytes());
	masterResult.setDataEncoding(response.getContentEncoding());

	return response;

    }

    private String getPlaylistPath(DataRequest respond, Parser parser) throws MalformedURLException {
	URL masterURL = new URL(getURLData());
	String auxPath = masterURL.getPath().substring(0, masterURL.getPath().lastIndexOf('/') + 1);
	
	String playlistUri = parser.selectMediaPlaylist(respond.getResponse(),
							this.getRESDATA(), this.getNetwordData(),
							this.getBandwidthType(), this.getResolutionType());

	if (playlistUri == null) playlistUri = getURLData(); // the supplied url is the media playlist possibly
	
	if (playlistUri.startsWith("http")) {
	    playlist = playlistUri;
	} else if (playlistUri.indexOf('/') == 0) {
	    playlist = masterURL.getProtocol() + "://" + masterURL.getHost() + (masterURL.getPort() > 0 ? ":" + masterURL.getPort() : "") + playlistUri;
	} else {
	    playlist = masterURL.getProtocol() + "://" + masterURL.getHost() + (masterURL.getPort() > 0 ? ":" + masterURL.getPort() : "") + auxPath + playlistUri;
	}

	auxPath = playlist.substring(0, playlist.lastIndexOf('/') + 1);

	return auxPath;
    }

    private DataRequest getPlaylist(SampleResult playListResult, Parser parser) throws IOException {

	playListResult.sampleStart();
	DataRequest response = parser.getBaseUrl(new URL(playlist), playListResult, true);
	playListResult.sampleEnd();

	playListResult.setRequestHeaders(response.getRequestHeaders() + "\n\n" + getCookieHeader(playlist) + "\n\n"
					 + getRequestHeader(this.getHeaderManager()));
	playListResult.setSuccessful(response.isSuccess());
	playListResult.setResponseMessage(response.getResponseMessage());
	playListResult.setSampleLabel("playlist");
	playListResult.setResponseHeaders(response.getHeadersAsString());
	playListResult.setResponseData(response.getResponse().getBytes());
	playListResult.setResponseCode(response.getResponseCode());
	playListResult.setContentType(response.getContentType());
	playListResult.setBytes(playListResult.getBytesAsLong() + (long) playListResult.getRequestHeaders().length());

	int headerBytes = playListResult.getResponseHeaders().length() // condensed
	    // length
	    // (without
	    // \r)
	    + response.getHeaders().size() // Add \r for each header
	    + 1 // Add \r for initial header
	    + 2; // final \r\n before data

	playListResult.setHeadersSize((int) headerBytes);
	playListResult.setSentBytes(response.getSentBytes());
	playListResult.setDataEncoding(response.getContentEncoding());

	return response;
    }

    @Override
    public SampleResult sample(Entry e) {
    	try {
	    if (masterResult == null) {
		masterResult = new SampleResult();
		masterResponse = getMasterList(masterResult, parser);
		playlistPath = getPlaylistPath(masterResponse, parser);
		startTimeMillis = System.currentTimeMillis();
		return masterResult;
	    }

	    log.info("sample: playlistPath: " + playlistPath + " playlist: " + playlist +
		     " thread: " + Thread.currentThread().getName());

	    if (getVideoDuration())
		durationSeconds = Integer.parseInt(getPlAYSecondsData());

	    while (true) {
		if (segments.isEmpty()) {
		    long now = System.currentTimeMillis();
		    if ((targetDuration > 0) && now < (lastTimeMillis + (targetDuration * 500))) {
			try {
			    Thread.sleep(now - (targetDuration * 500)); // only get playlist every TD/2 seconds
			} catch(InterruptedException e1) {
			    log.warn("sample: Thead.sleep() interupted");
			}		    					
		    }
		    playlistResult = new SampleResult();
		    playlistResponse = getPlaylist(playlistResult, parser);
		    segments.addAll(parser.extractSegmentUris(playlistResponse.getResponse()));
		    lastTimeMillis = now;
    				
		    int td = parser.getTargetDuration(playlistResponse.getResponse());
		    if (td < 0) {
			log.error("sample: playlist contains no target duration: \\n" + playlistResponse.getResponse());
		    } else if (targetDuration != 0 && td != targetDuration) {
			log.info("sample: playlist target duration changed: " + td + " (" + targetDuration + ")");
		    }
		    if (td > 0) {
			targetDuration = td;
		    }
    				
		    isLive = parser.isLive(playlistResponse.getResponse());
		    if (isLive) {
			log.info("sample: detected live playlist");
		    }
		    return playlistResult;
		} else {
		    DataSegment segment = segments.remove(0);
		    if ((segmentsFetched.size() == 0) || (!segmentsFetched.contains(segment.getUri().trim()))) {
			String durationStr = segment.getDuration();
			float duration = Float.parseFloat(durationStr);
			log.info("segment duration: " + durationStr + " (" + duration + ")");

			// playedSeconds is the total current playtime including the duration of the last segment
			// we wait when this time is in the future
			if (lastDurationSeconds > 0) playedSeconds += lastDurationSeconds;
			long now = System.currentTimeMillis();
			if ((now - startTimeMillis) < Math.round(playedSeconds * 1000)) {
			    try {
				long sleepMillis = (startTimeMillis + Math.round(playedSeconds*1000) - now);
				log.info("sample: sleeping: " + sleepMillis);
				Thread.sleep(sleepMillis); // only get segment when finished playing the one before
			    } catch(InterruptedException e1) {
				log.warn("sample: Thead.sleep() interupted");
			    }		    					
			}
			lastDurationSeconds = duration;
			SampleResult segmentResult = getSegment(parser, segment, playlistPath);
			segmentsFetched.add(segment.getUri().trim());
			return segmentResult;
		    }
		}

		if (getVideoDuration()) {
		    if (playedSeconds > durationSeconds) {
			break;
		    }
		}
	    }
    	} catch (IOException e1) {
	    e1.printStackTrace();
    	}
    	return null;
    }


    private void initHlsSamplerData() {
    	masterResponse = null;
    	if (segments != null) segments.clear();
    	if (segmentsFetched != null) segmentsFetched.clear();
    	playlistPath = null;
    	playlist = null;
    	playlistResponse = null;
    	playlistResult = null;
    	isLive = false; // does not have an end tag
    	targetDuration = 0;
    	playedSeconds = 0;
    	lastDurationSeconds = 0;
    	startTimeMillis = 0;
    	lastTimeMillis = 0;
    }

    public String getURLData() {
	return this.getPropertyAsString(MASTER_PLAYLIST_URL);
    }

    public String getRESDATA() {
	return this.getPropertyAsString(CUSTOM_RESOLUTION);
    }

    public String getNetwordData() {
	return this.getPropertyAsString(CUSTOM_BANDWIDTH);
    }

    public String getPlAYSecondsData() {
	return this.getPropertyAsString(CUSTOM_DURATION);
    }

    public boolean getVideoDuration() {
	return this.getPropertyAsBoolean(IS_CUSTOM_DURATION);
    }

    public String getResolutionType() {
	return this.getPropertyAsString(RESOLUTION_OPTION);
    }

    public String getBandwidthType() {
	return this.getPropertyAsString(BANDWIDTH_OPTION);
    }

    public void setURLData(String url) {
	this.setProperty(MASTER_PLAYLIST_URL, url);
    }

    public void setResData(String res) {
	this.setProperty(CUSTOM_RESOLUTION, res);
    }

    public void setNetworkData(String net) {
	this.setProperty(CUSTOM_BANDWIDTH, net);
    }

    public void setVideoDuration(boolean res) {
	this.setProperty(IS_CUSTOM_DURATION, res);
    }

    public void setPlaySecondsData(String seconds) {
	this.setProperty(CUSTOM_DURATION, seconds);
    }

    public void setResolutionType(String type) {
	this.setProperty(RESOLUTION_OPTION, type);
    }

    public void setBandwidthType(String type) {
	this.setProperty(BANDWIDTH_OPTION, type);
    }

    public SampleResult getSegment(Parser parser, DataSegment seg, String url) {
	SampleResult result = new SampleResult();

	String uriString = seg.getUri();
	log.info("url: " + (url!=null?url:"<null>") + " uriString: " + uriString);
	if ((url != null) && (!uriString.startsWith("http"))) {
	    uriString = url + uriString;
	    log.info("uriString: " + uriString);
	}

	result.sampleStart();

	try {
	    DataRequest respond = parser.getBaseUrl(new URL(uriString), result, false);

	    result.sampleEnd();

	    result.setRequestHeaders(respond.getRequestHeaders() + "\n\n" + getCookieHeader(uriString) + "\n\n"
				     + getRequestHeader(this.getHeaderManager()));
	    result.setSuccessful(respond.isSuccess());
	    result.setResponseMessage(respond.getResponseMessage());
	    result.setSampleLabel("video_segment");
	    result.setResponseHeaders("URL: " + uriString + "\n" + respond.getHeadersAsString());
	    result.setResponseCode(respond.getResponseCode());
	    result.setContentType(respond.getContentType());
	    result.setBytes(result.getBytesAsLong() + (long) result.getRequestHeaders().length());
	    int headerBytes = result.getResponseHeaders().length() // condensed length (without \r)
		+ respond.getHeaders().size() // Add \r for each header
		+ 1 // Add \r for initial header
		+ 2; // final \r\n before data

	    result.setHeadersSize((int) headerBytes);
	    result.setSentBytes(respond.getSentBytes());
	    result.setDataEncoding(respond.getContentEncoding());
	} catch (IOException e1) {
	    e1.printStackTrace();
	    result.sampleEnd();
	    result.setSuccessful(false);
	    result.setResponseMessage("Exception: " + e1);
	}

	return result;
    }

    // private method to allow AsyncSample to reset the value without performing
    // checks
    private void setCookieManagerProperty(CookieManager value) {
	setProperty(new TestElementProperty(COOKIE_MANAGER, value));
    }

    public void setCookieManager(CookieManager value) {
	CookieManager mgr = getCookieManager();
	if (mgr != null) {
	    log.warn("Existing CookieManager " + mgr.getName() + " superseded by " + value.getName());
	}
	setCookieManagerProperty(value);
    }

    public CookieManager getCookieManager() {
	return (CookieManager) getProperty(COOKIE_MANAGER).getObjectValue();
    }

    public void setCacheManager(CacheManager value) {
	CacheManager mgr = getCacheManager();
	if (mgr != null) {
	    log.warn("Existing CacheManager " + mgr.getName() + " superseded by " + value.getName());
	}
	setCacheManagerProperty(value);
    }

    // private method to allow AsyncSample to reset the value without performing
    // checks
    private void setCacheManagerProperty(CacheManager value) {
	setProperty(new TestElementProperty(CACHE_MANAGER, value));
    }

    public CacheManager getCacheManager() {
	return (CacheManager) getProperty(CACHE_MANAGER).getObjectValue();
    }

    public String getRequestHeader(org.apache.jmeter.protocol.http.control.HeaderManager headerManager) {
	String headerString = "";

	if (headerManager != null) {
	    CollectionProperty headers = headerManager.getHeaders();
	    if (headers != null) {
		for (JMeterProperty jMeterProperty : headers) {
		    org.apache.jmeter.protocol.http.control.Header header = (org.apache.jmeter.protocol.http.control.Header) jMeterProperty
			.getObjectValue();
		    String n = header.getName();
		    if (!HTTPConstants.HEADER_CONTENT_LENGTH.equalsIgnoreCase(n)) {
			String v = header.getValue();
			v = v.replaceFirst(":\\d+$", "");
			headerString = headerString + n + ": " + v + "\n";
		    }
		}
	    }
	}

	return headerString;
    }

    public void setHeaderManager(HeaderManager value) {
	HeaderManager mgr = getHeaderManager();
	if (mgr != null) {
	    value = mgr.merge(value, true);
	    if (log.isDebugEnabled()) {
		log.debug("Existing HeaderManager '" + mgr.getName() + "' merged with '" + value.getName() + "'");
		for (int i = 0; i < value.getHeaders().size(); i++) {
		    log.debug("    " + value.getHeader(i).getName() + "=" + value.getHeader(i).getValue());
		}
	    }
	}
	setProperty(new TestElementProperty(HEADER_MANAGER, (TestElement) value));
    }

    public String getCookieHeader(String urlData) throws MalformedURLException {
	String headerString = "";

	URL url = new URL(urlData);
	// Extracts all the required cookies for that particular URL request
	String cookieHeader = null;
	if (getCookieManager() != null) {
	    cookieHeader = getCookieManager().getCookieHeaderForURL(url);
	    if (cookieHeader != null) {
		headerString = headerString + HTTPConstants.HEADER_COOKIE + ": " + cookieHeader + "\n";
	    }
	}

	return headerString;
    }

    @Override
    public void addTestElement(TestElement el) {
	if (el instanceof HeaderManager) {
	    setHeaderManager((HeaderManager) el);
	} else if (el instanceof CookieManager) {
	    setCookieManager((CookieManager) el);
	} else if (el instanceof CacheManager) {
	    setCacheManager((CacheManager) el);
	} else {
	    super.addTestElement(el);
	}
    }
	
    public void setParser (Parser p){
	parser = p;
    }

}
