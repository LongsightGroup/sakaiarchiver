package org.sakaiproject.util.archiver.parsers;

import java.io.IOException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;

import org.sakaiproject.util.archiver.ParsingUtils;
import org.sakaiproject.util.archiver.ToolParser;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.HtmlLink;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlScript;

public class SkinParser extends ToolParser {

	public static final String TOOL_NAME = "skin";

	public SkinParser() {
		super();
	}

	public SkinParser(String mainURL) {
		super(mainURL);
	}
	@Override
	public void initialize() {
		setSubdirectory("");
	}

	@Override
	public void parse() throws Exception {
 	    HtmlPage page = getArchiver().getHomePage();
		setCurrentPage(page);
		parseCss( page );
		parseJavascript( page );
	}

	@Override
	public String getToolName() {
		return TOOL_NAME;
	}
	public void parseCss( HtmlPage page ) throws IOException {
		List<?> links = ParsingUtils.findElementWithType(page, "link", "text/css");
        Iterator<?> it = links.iterator();
        while (it.hasNext()) {
            HtmlLink link = (HtmlLink) it.next();

            String path = link.getAttribute("href");
            if (path == null || path.equals("")) continue;

            if ( path.startsWith("/")) {
            	path = path.substring(1);
            }
            if ( ! getArchiver().getSavedPages().contains(path)) {
	            WebResponse resp = link.getWebResponse(true);
	            String css = resp.getContentAsString();
	            saveContentString(css, path);
	            getArchiver().getSavedPages().add(path);
	            parseCssImages(css);
            }
        }
	}
	public void parseCssImages(String css ) {
		// TODO: get embedded css images
		return;
	}

	public void parseJavascript( HtmlPage page ) throws IOException {
		List<?> scripts = ParsingUtils.findScriptElements(page);
        Iterator<?> it = scripts.iterator();
        while (it.hasNext()) {
            HtmlScript script = (HtmlScript) it.next();

            String path = script.getSrcAttribute();
            // The //: seems to come from an ie initializer optional code.
            if (path == null || path.equals("") || path.equals("//:")) continue;
        	URL url = page.getFullyQualifiedUrl(path);
            if ( path.startsWith("/")) {
            	path = path.substring(1);
            }
            if ( ! getArchiver().getSavedPages().contains(path)) {
            	Page jsPage = getArchiver().getWebClient().getPage(url);
	            saveContentString(jsPage.getWebResponse().getContentAsString(), path);
	            getArchiver().getSavedPages().add(path);
            }
        }
	}
}
