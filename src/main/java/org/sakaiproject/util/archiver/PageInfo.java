package org.sakaiproject.util.archiver;

import java.net.URL;

/**
 * Information about pages that have been visited.
 * 
 * @author monroe
 */
public class PageInfo {
	
	private String title;
	private URL url;
	private int order;
	private String localURL;
	
	
	public PageInfo( String title, URL url ) {
		setTitle(title);
		setUrl(url);
	}

	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public URL getUrl() {
		return url;
	}
	public void setUrl(URL url) {
		this.url = url;
	}
	public int getOrder() {
		return order;
	}
	public void setOrder(int order) {
		this.order = order;
	}
	public String getLocalURL() {
		return localURL;
	}
	public void setLocalURL(String localURL) {
		this.localURL = localURL;
	}
}
