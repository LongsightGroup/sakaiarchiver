package org.sakaiproject.util.archiver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.html.HtmlInlineFrame;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

public abstract class ToolParser {

	private String mainURL;
	private String toolURL;
	private Archiver archiver;
	private String subdirectory;
	private String mainPage;
	private HtmlPage currentPage;
	private HtmlPage parentPage;
    private String toolPageName;

	public ToolParser() {
		initialize();
	}

	public ToolParser( String mainURL) {
		this();
		setMainURL(mainURL);
	}
	/**
	 * Setup the required default class properties when Parser object created.
	 */
	public void initialize() {
		// Subclasses should set up properties here.
	}

	public void parse( Archiver archiver ) throws Exception {
		setArchiver(archiver);
		msg("Parsing tool:  " + getToolName(), Archiver.NORMAL);
        init();
		parse();
		fini();
	}
    /**
     * Set up for tool parsing.
     *
     * @throws Exception
     */
    public void init() throws Exception {
        copyResources();
    }
    /**
     * Perform the parsing of the tool.
     *
     * @throws Exception
     */
    public void parse() throws Exception {
        loadMainPage();
        savePage(getCurrentPage(), getSubdirectory() + "index.htm");
    }
    /**
     * Clean up after parsing tool (if needed)
     *
     * @throws Exception
     */
    public void fini() throws Exception {

    }
    /**
     * Copy any required resources to the destination directory.
     *
     * @throws IOException
     */
    public void copyResources() throws IOException {
        List<String> files = getResources();
        File base = new File(getArchiver().getBasePath());

        for( String file: files ) {
            File resource = new File(base,file);

            OutputStream out = new FileOutputStream(resource);
            InputStream in = Archiver.class.getClassLoader().getResourceAsStream(file);
            try {
                IOUtils.copy(in, out);
            } finally {
                out.close();
            }
        }
    }
    /**
     * Return a list of resource files needed by parser (if any).
     *
     * @return
     */
    public List<String> getResources() {
        return new ArrayList<String>();
    }
    /**
     * Loads the tools' main page.
     *
     * @throws FailingHttpStatusCodeException
     * @throws MalformedURLException
     * @throws IOException
     */
	public void loadMainPage() throws FailingHttpStatusCodeException, MalformedURLException, IOException {
		if ( getMainURL() == null ) {
			return;
		}
		HtmlPage page = loadPage(getMainURL());
		setToolURL(getPortletMainIframeURL(page));

		setCurrentPage(page);
		PageInfo info = new PageInfo(page);
		info.setTool(getToolName());
		info.setLocalURL("file://" + getPath() + "index.html");
		getArchiver().getSitePages().addLeaf(info);
	}
	/**
	 * Save a page and associated information.
	 *
	 * @param page
	 * @param filepath The path to the file to save the html in relative to the archive base.
	 * @throws IOException
	 */
    public void savePage(HtmlPage page, String filepath) throws Exception {
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
	 * Get a tool view page.
	 *
	 * Note:  If previous page is a "protected" page, a tool reset should be
	 * called prior to using this.
	 *
	 * @param page
	 * @param view
	 * @return The tool view page.
	 * @throws FailingHttpStatusCodeException
	 * @throws MalformedURLException
	 * @throws IOException
	 */
	public HtmlPage getSubViewPage( HtmlPage page, String view )
	        throws FailingHttpStatusCodeException, MalformedURLException,
	               IOException {
	    URL url = page.getUrl();
	    String viewURL = url.toString().split("\\?")[0] +
	            "?sakai_action=doView&view=" + view;
	    HtmlPage viewPage = getArchiver().getWebClient().getPage(viewURL);
	    return viewPage;
	}
	public String getPortletMainIframeURL( HtmlPage page ) {
        List<?> elements = ParsingUtils.findElementWithCssClass(page, "iframe", "portletMainIframe");
        return ((HtmlInlineFrame) elements.get(0)).getSrcAttribute();
	}
	/**
	 * Sakai prevent a user from leaving some pages unless the server gets
	 * a valid command (edit loss protection).  This issues a reset to the
	 * server so new pages to be selected.
	 *
	 * @throws FailingHttpStatusCodeException
	 * @throws MalformedURLException
	 * @throws IOException
	 */
	public void resetTool()
	        throws FailingHttpStatusCodeException, MalformedURLException,
	               IOException {
	    String toolUrl = getToolURL();
	    if ( toolUrl == null || ! toolUrl.contains("/tool/")) {
	        msg("COULD NOT RESET TOOL: " + getToolName() +
	                ".  The main url was invalid or null.", Archiver.ERROR);
	        return;
	    }
	    String resetUrl = toolUrl.replaceAll("/tool/", "/tool-reset/");
//msg("resetURL=" + resetUrl, Archiver.DEBUG);
        getArchiver().getWebClient().getPage(resetUrl);
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
	public String addJavascript() throws Exception {
	    return "";
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

    public String getToolURL() {
        return toolURL;
    }

    public void setToolURL(String toolURL) {
        this.toolURL = toolURL;
    }

    public String getToolPageName() {
        return toolPageName;
    }

    public void setToolPageName(String toolPageName) {
        this.toolPageName = toolPageName;
    }
}
