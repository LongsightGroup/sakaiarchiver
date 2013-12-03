package org.sakaiproject.util.archiver;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.gargoylesoftware.htmlunit.html.FrameWindow;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

/**
 * Information about pages that have been processed.
 *
 * @deprecated Originally going to be used to create external navigation page.
 * @author monroe
 */
public class PageInfo {

	private String title;
	private URL url;
	private int order;
	private String localURL;
	private String tool;

	private List<PageInfo> iframes = new ArrayList<PageInfo>();

	public PageInfo() {
	}

	public PageInfo( HtmlPage page ) {
		initialize(page);
	}

	public void initialize(HtmlPage page ) {
		setTitle(page.getTitleText());
		setUrl(page.getUrl());
		parseIFrames(page);
	}

	public void parseIFrames( HtmlPage page) {
		List<FrameWindow> windows = page.getFrames();
		List<PageInfo> iframes = getIframes();
		for( FrameWindow iframe: windows) {
			HtmlPage framePage = (HtmlPage) iframe.getEnclosedPage();
			PageInfo frameInfo = new PageInfo(framePage);
			iframes.add(frameInfo);
		}
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
	public List<PageInfo> getIframes() {
		return iframes;
	}

	public void setIframes(List<PageInfo> iframes) {
		this.iframes = iframes;
	}
	public void addIframe(PageInfo iframe) {
		getIframes().add(iframe);
	}

	@Override
	public String toString() {
		String eol = System.getProperty("line.separator") + "  ";
		String s = "PAGE INFO" + eol + "Title: " + getTitle() + eol +
		  "URL: " + getUrl().toString() + eol;

		if ( ! getIframes().isEmpty()) {
		    s += "iFrames[" + eol;
		    for( PageInfo iframe: getIframes() ) {
			    s += "  " + iframe.toString() + eol;
		    }
		    s += "]" + eol;
		}
		return s;
	}

	public String getTool() {
		return tool;
	}

	public void setTool(String tool) {
		this.tool = tool;
	}


}
