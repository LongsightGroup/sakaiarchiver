package org.sakaiproject.util.archiver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

public abstract class ToolParser {

	private String mainURL;
	private Archiver archiver;
	private String subdirectory;
	private String mainPage;
	private HtmlPage currentPage;
	private HtmlPage parentPage;

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
		msg("Parsing tool:  " + getToolName(), Archiver.NORMAL);
		parse();
	}
	public void loadMainPage() throws FailingHttpStatusCodeException, MalformedURLException, IOException {
		if ( getMainURL() == null ) {
			return;
		}
		HtmlPage page = loadPage(getMainURL());
		setCurrentPage(page);
		PageInfo info = new PageInfo(page);
		info.setTool(getToolName());
		info.setLocalURL("file://" + getPath() + "index.html");
		getArchiver().getSitePages().addLeaf(info);
	}
	public void parse() throws Exception {
		loadMainPage();
		savePage(getCurrentPage(), getSubdirectory() + "index.htm");
	}
	/**
	 * Save a page and associated information.
	 *
	 * @param page
	 * @param filepath The path to the file to save the html in relative to the archive base.
	 * @throws IOException
	 */
    public void savePage(HtmlPage page, String filepath) throws IOException {
		PageSaver pageSaver = new PageSaver(getArchiver());
		pageSaver.save(page, filepath);
        msg("Saved '" + page.getTitleText() + "' in " + filepath, Archiver.NORMAL);
    }

    /**
     * Get the full system path to the directory the tool info will be stored.
     *
     * @return The path based on archive.dir.base option, the site names and tool subdirectory.  Will
     *         end with /
     */
    public String getPath() {
    	return getArchiver().getOption("archive.dir.base") + getArchiver().getSite() + "/" + getSubdirectory();
    }
    /**
     * Get the short tool name for the current parser.  Tool names should only contain [a-z0-9_]
     *
     * @return The tool name.
     */
    abstract public String getToolName();

	public HtmlPage loadPage(String url) throws FailingHttpStatusCodeException, MalformedURLException, IOException {
		HtmlPage page = getArchiver().getWebClient().getPage(url);
		return page;
	}
	/**
	 * Save raw content to a file.
	 *
	 * @param content The content to write out.
	 * @param path The file path relative to the archive base path.
	 */
	public void saveContentString(String content, String path ) throws FileNotFoundException {
		File file = new File(getArchiver().getBasePath() + path );
		file.getParentFile().mkdirs();
		PrintWriter out = new PrintWriter(file);
		try {
			out.print(content);
		} finally {
			out.close();
		}
	}
	/**
	 * This method is called by PageSave after it has modified the HTML and
	 * before it is written to the file.
	 *
	 * @param page
	 * @param html
	 * @return
	 */
	public String modifySavedHtml( HtmlPage page, String html ) {
	    return html;
	}
	/**
	 * Output a message via Archiver's msg method.
	 *
	 * @param msg
	 */
	public void msg( String msg, int level ) {
		getArchiver().msg(msg, level);
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
	/**
	 * Get the tool subdirectory.
	 * @return The subdirectory with a trailing /
	 */
	public String getSubdirectory() {
		if ( ! subdirectory.equals("") && ! subdirectory.endsWith("/") ) {
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

	public HtmlPage getCurrentPage() {
		return currentPage;
	}

	public void setCurrentPage(HtmlPage currentPage) {
		this.currentPage = currentPage;
	}

	public HtmlPage getParentPage() {
		return parentPage;
	}

	public void setParentPage(HtmlPage parentPage) {
		this.parentPage = parentPage;
	}

}
