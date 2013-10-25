package org.sakaiproject.util.archiver;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

public abstract class ToolParser {

	private String mainURL;
	private Archiver archiver;
	private String subdirectory;
	private String mainPage;

	public ToolParser() {
		initialize();
	}

	public ToolParser( String mainURL) {
		this();
		setMainURL(mainURL);
	}
	public void initialize() {
		// Subclasses should set up properties here.
	}

	public void parse( Archiver archiver ) throws Exception {
		setArchiver(archiver);
		parse();
	}
	public void loadMainPage() throws FailingHttpStatusCodeException, MalformedURLException, IOException {
		if ( getMainURL() == null ) {
			return;
		}
		HtmlPage page = loadPage(getMainURL());
		PageInfo info = new PageInfo(page);
		info.setTool(getToolName());
		info.setLocalURL("file://" + getPath() + "index.html");
		getArchiver().getSitePages().addLeaf(info);
		savePage(page, "index.htm");
	}
	public void parse() throws Exception {
		loadMainPage();
	}
    public void savePage(HtmlPage page, String name) throws IOException {

    	String fullName = getPath() + name;
        File file = new File(fullName );
        file.getParentFile().mkdirs();
        new JavaScriptXMLSerializer().save(page, file);
        getArchiver().msg("Saved '" + page.getTitleText() + "' in " + fullName);
    }

    public String getPath() {
    	return getArchiver().getOption("archive.dir.base") + getArchiver().getSite() + "/" + getSubdirectory();
    }

    abstract public String getToolName();

	public HtmlPage loadPage(String url) throws FailingHttpStatusCodeException, MalformedURLException, IOException {
		HtmlPage page = getArchiver().getWebClient().getPage(url);
		return page;
	}

	public String getMainURL() {
		return mainURL;
	}

	public void setMainURL(String mainURL) {
		this.mainURL = mainURL;
	}

	public Archiver getArchiver() {
		return archiver;
	}

	public void setArchiver(Archiver archiver) {
		this.archiver = archiver;
	}

	public String getSubdirectory() {
		if ( ! subdirectory.endsWith("/") ) {
			subdirectory += "/";
		}
		return subdirectory;
	}

	public void setSubdirectory(String subdirecory) {
		this.subdirectory = subdirecory;
	}

	public String getMainPage() {
		return mainPage;
	}

	public void setMainPage(String mainPage) {
		this.mainPage = mainPage;
	}

}
