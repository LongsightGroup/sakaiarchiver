package org.sakaiproject.util.archiver;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.sakaiproject.util.archiver.parsers.AssignmentsParser;
import org.sakaiproject.util.archiver.parsers.ForumsParser;
import org.sakaiproject.util.archiver.parsers.HomeParser;
import org.sakaiproject.util.archiver.parsers.ResourcesParser;
import org.sakaiproject.util.archiver.parsers.RosterParser;
import org.sakaiproject.util.archiver.parsers.SamigoParser;
import org.sakaiproject.util.archiver.parsers.SyllabusParser;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlPasswordInput;
import com.gargoylesoftware.htmlunit.html.HtmlSubmitInput;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;

public class Archiver {

    // Message and debug flags.
	public static final int ERROR = -2;
	public static final int WARNING = -1;
	public static final int NORMAL = 0;
	public static final int DEBUG = 1;
	public static final int VERBOSE = 2;
//	public static final String DEBUG_TOOL = "samigo";
    public static final String DEBUG_TOOL = null;
	public static final boolean DEBUG_SKIP_FILES = false;

	// Option keys
	public static final String ARCHIVE_DIR_BASE = "archive.dir.base";
	public static final String SAKAI_BASE_URL = "sakai.base.url";
	public static final String LOGIN_FORM_NAME = "login.form.name";
	public static final String LOGIN_FORM_USER = "login.form.user";
	public static final String LOGIN_FORM_SUBMIT = "login.form.submit";
	public static final String LOGIN_FORM_PASSWORD = "login.form.password";

    // Input arguments and options
	private String site;
    private String user;
    private String password;
    private String optionsFile;
    private Properties options;
    private String archiveBasePath;

    // Look up maps
    private Map<String,String> classToTool;
    private Map<String,ToolParser> toolToParser;

    // Working vars.
    private HtmlPage homePage;
    private WebClient webClient;
    private List<ToolParser> siteTools;
    /**
     * List of support pages (css, js, images, and the like) that have been
     * saved already.
     */
    private List<String> savedPages;
    private int outputVerbosity = DEBUG;
    /**
     * The host name of the site (used by JS for filtering.)
     */
    private String siteHost;
    /**
     * @Deprecated Was going to be used to produce navigation but not used now
     */
    private PageTree<PageInfo> sitePages;

	/**
	 * Constructor with all the command line options.
	 *
	 * @param site
	 * @param user
	 * @param password
	 * @param optionsFile
	 */
    public Archiver( String site, String user, String password, String optionsFile ) {
        setSite(site);
        setUser(user);
        setPassword(password);
        setOptionsFile(optionsFile);
    }
	/**
	 * Standard java command start point.
	 *
	 * @param args
	 */
	public static void main(String[] args) {
    	if ( args == null || args.length == 0 ) {
    		Archiver.usage("Missing arguments");
    		return;
    	}
        String site = args[0];
        if ( site == null ) {
        	Archiver.usage("Missing site argument");
        	return;
        }
        String user = args[1];
        if ( user == null ) {
        	Archiver.usage("Missing user argument");
        	return;
        }
        String password = args[2];
        if ( password == null ) {
        	Archiver.usage("Missing password argument");
        	return;
        }
        Archiver archiver = new Archiver(site, user, password, args[3]);
        try {
            archiver.initialize();
            archiver.execute();
            archiver.finalize();
        } catch ( Exception e ) {
            e.printStackTrace();
        } finally {
        	archiver.finalize();
        }
    }
	/**
	 * Output an error message with usage information.
	 *
	 * @param msg
	 */
    static public void usage( String msg ) {
    	System.err.println(msg);
    	System.err.println("Usage:  SakaiArchiver site user password (optional properties-file)");
    }
    /**
     * Call the various sub initialize methods.  Needs to be called prior to the execute method.
     *
     * @throws IOException
     */
    public void initialize() throws IOException {
    	initializeToolParser();
    	loadOptions( getOptionsFile() );
    	if ( ! initArchiveBasePath() ) {
    		msg("Could not initialize the archive base location: " + getArchiveBasePath(), ERROR);
    		return;
    	}
        initWebClient();
        copyResources();
        URL siteURL = new URL(getOption(SAKAI_BASE_URL));
        setSiteHost(siteURL.getHost());
    }
    /**
     * Main method which creates the archive.
     *
     * @throws Exception
     */
    public void execute() throws Exception {
        if ( ! login(getSite(), getUser(), getPassword()) ) {
        	msg("Error:  Could not log in or did not get to required site.", ERROR);
        	return;
        }
        msg("Successfully logged in to site.", NORMAL);
        PageInfo pInfo = new PageInfo( getHomePage());
    	setSitePages( new PageTree<PageInfo>( pInfo ) );
        locateTools();
        for( ToolParser tool: getSiteTools()) {
        	tool.parse(this);
        }
//        System.out.println("Tree = " + getSitePages().toString());
    }
    /**
     * Clean up before exiting.
     */
    public void finalize() {
    	getWebClient().closeAllWindows();
    }
    /**
     * Loads the options which define the login form, archive location, base sakai URL, and
     * the like.
     *
     * @param path Path to the properties file.  If null, the sakai-archiver.properties will be
     *             used from the classpath.
     * @throws IOException
     */
    public void loadOptions( String path ) throws IOException {

    	Properties defaults = new Properties();
    	defaults.load(Archiver.class.getClassLoader().getResourceAsStream("sakai-archiver.properties"));
    	Properties options = new Properties(defaults);
    	if ( path != null ) {
    		options.load(new FileInputStream(path));
    	}
    	setOptions( options );
    }
    /**
     * Set up the directory location used to save the site.  NOTE: if the site directory exists
     * it will be deleted.
     *
     * TODO: add option to enable/disable autodelete
     *
     * @throws IOException
     */
    public boolean initArchiveBasePath() throws IOException {
    	setArchiveBasePath( getOption(ARCHIVE_DIR_BASE) + getSite() );
    	File base = new File(getArchiveBasePath());
    	if ( base.exists() ) {
    		FileUtils.deleteDirectory(base);
    	}
    	base.mkdirs();
    	return true;
    }
    /**
     * Initialize the WebClient
     */
    public void initWebClient() {
        WebClient webClient = new WebClient(BrowserVersion.FIREFOX_17);
        webClient.getCookieManager().setCookiesEnabled(true);
        webClient.getOptions().setRedirectEnabled(true);
        webClient.getOptions().setThrowExceptionOnScriptError(false);
        java.util.logging.Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(Level.WARNING);
        setWebClient(webClient);
    }
    /**
     * Log in to the site.
     *
     * TODO: Generalize this
     *
     * @param site Site URL
     * @param user
     * @param pwd
     * @return True if succeeded / False if not.
     * @throws IOException
     * @throws MalformedURLException
     * @throws FailingHttpStatusCodeException
     */
    public boolean login(String site, String user, String pwd )
            throws Exception {

    	String fullSite = getOption(SAKAI_BASE_URL) + site;
    	String loginURL = fullSite.replaceAll("portal/site", "portal/login/site");

        HtmlPage page = getWebClient().getPage(loginURL);
        HtmlForm form = page.getFormByName(getOption(LOGIN_FORM_NAME));
        HtmlSubmitInput button = form.getInputByName(getOption(LOGIN_FORM_SUBMIT));
        HtmlTextInput userField = form.getInputByName(getOption(LOGIN_FORM_USER));
        HtmlPasswordInput pwdField = form.getInputByName(getOption(LOGIN_FORM_PASSWORD));

        userField.setValueAttribute(user);
        pwdField.setValueAttribute(pwd);

        page = button.click();

        URI thisLocation = page.getUrl().toURI();
        URI wantedLocation = new URI(fullSite);
        if (! thisLocation.equals(wantedLocation)) {
        	return false;
        }
        setHomePage(page);
        return true;
    }
    /**
     * Search the current homePage to find the URL to the applicable tools.
     */
    public void locateTools(){
    	HtmlPage page = getHomePage();
    	Map<String,String> classMap = getClassToTool();
//    	getSiteTools().add(getToolToParser().get("skin"));
    	ToolParser parser = getToolToParser().get("home");
    	parser.setMainURL(getHomePage().getUrl().toString());
    	getSiteTools().add(parser);

    	for ( String key: classMap.keySet() ) {
    		List<?> anchors = page.getByXPath("//a[contains(@class,'" + key + "')]");
    		if ( ! anchors.isEmpty() ) {
    			HtmlAnchor tool = (HtmlAnchor) anchors.get(0);
    			String toolName = classMap.get(key);
    			if ( DEBUG_TOOL != null && ! toolName.equals(DEBUG_TOOL)) {
    				continue;
    			}
    			parser = getToolToParser().get(toolName);
    			parser.setMainURL(tool.getHrefAttribute());
    			getSiteTools().add(parser);
    		}
    	}
    }
    /**
     * Initializes various map structures used to parse the tools.
     */
    public void initializeToolParser() {
    	Map<String,String> classMap = getClassToTool();
    	classMap.put("icon-sakai-syllabus", "syllabus");
    	classMap.put("icon-sakai-resources", "resources");
    	classMap.put("icon-sakai-forums", "forums");
    	classMap.put("icon-sakai-resources", "resources");
    	classMap.put("icon-sakai-samigo", "samigo");
    	classMap.put("icon-sakai-assignment-grades", "assignments");
    	classMap.put("icon-sakai-site-roster", "roster");

    	//TODO: Replace with tool specific classes!
    	Map<String,ToolParser> toolMap = getToolToParser();
    	toolMap.put("home", new HomeParser());
    	toolMap.put("syllabus", new SyllabusParser());
    	toolMap.put("resources", new ResourcesParser());
    	toolMap.put("forums", new ForumsParser());
    	toolMap.put("samigo", new SamigoParser());
    	toolMap.put("assignments", new AssignmentsParser());
    	toolMap.put("roster", new RosterParser());
    }
    /**
     * Output a message to stdout
     *
     * @param msg
     * @param level The msg level (see Archiver flags)
     */
    public void msg(String msg, int level ) {
    	if ( level <= getOutputVerbosity() ) {
    		System.out.println(msg);
    	}
    }
    /**
     * Copy resource files needed by the offline version.
     *
     * @throws IOException
     */
    public void copyResources() throws IOException {
    	File base = new File(getBasePath());
    	File css = new File(base,"sakai-offline.css");
    	File js = new File(base, "sakai-offline.js");

    	OutputStream out = new FileOutputStream(css);
    	InputStream in = Archiver.class.getClassLoader().getResourceAsStream("sakai-offline.css");
    	try {
    		IOUtils.copy(in, out);
    	} finally {
    		out.close();
    	}
    	out = new FileOutputStream(js);
    	in = Archiver.class.getClassLoader().getResourceAsStream("sakai-offline.js");
    	try {
    		IOUtils.copy(in, out);
    	} finally {
    		out.close();
    	}
    }
    /**
     * Save a file and associated files.
     *
     * @param name
     * @throws IOException
     */
/*
    public void dumpAnchors() {
        List<HtmlAnchor> anchors = getPage().getAnchors();
        Iterator<HtmlAnchor> iAnchors = anchors.iterator();
        while(iAnchors.hasNext()) {
            HtmlAnchor anchor = iAnchors.next();
            System.out.print(anchor.getTextContent());
            System.out.print(" => ");
            System.out.print(anchor.getHrefAttribute());
            System.out.println();
        }
    }
*/
    /**
     * Gets the absolute base path to the site archive.
     *
     * @return The base path ending with a /
     */
    public String getBasePath() {
    	String path = getOption(ARCHIVE_DIR_BASE);
    	if ( ! path.endsWith("/") ) {
    		path += "/";
    	}
    	path += getSite() + "/";
    	return path;
    }
    public HtmlPage getHomePage() {
        return homePage;
    }

    public void setHomePage(HtmlPage page) {
        this.homePage = page;
    }

    public WebClient getWebClient() {
        return webClient;
    }

    public void setWebClient(WebClient webClient) {
        this.webClient = webClient;
    }
    public String getSite() {
        return site;
    }
    public void setSite(String site) {
        this.site = site;
    }
    public String getUser() {
        return user;
    }
    public void setUser(String user) {
        this.user = user;
    }
    public String getPassword() {
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
    }

	public Properties getOptions() {
		return options;
	}

	public void setOptions(Properties options) {
		this.options = options;
	}
	/**
	 * Get the value of the option specified by key.
	 *
	 * @param key
	 * @return The trimmed option value.
	 */
	public String getOption( String key ) {
		return getOptions().getProperty(key).trim();
	}

	public String getOptionsFile() {
		return optionsFile;
	}

	public void setOptionsFile(String optionsFile) {
		this.optionsFile = optionsFile;
	}

	public String getArchiveBasePath() {
		return archiveBasePath;
	}

	public void setArchiveBasePath(String archiveBasePath) {
		this.archiveBasePath = archiveBasePath;
	}
	/**
	 * Get the class to tool property
	 *
	 * @return Always returns a Map object (may be empty)
	 */
	public Map<String, String> getClassToTool() {
		if ( this.classToTool == null ) {
			this.classToTool = new HashMap<String, String>();
		}
		return classToTool;
	}

	public void setClassToTool(Map<String, String> classToTool) {
		this.classToTool = classToTool;
	}
	public PageTree<PageInfo> getSitePages() {
		return sitePages;
	}
	public void setSitePages(PageTree<PageInfo> sitePages) {
		this.sitePages = sitePages;
	}
	/**
	 * Get the tool parser lookup property
	 *
	 * @return Always returns a Map object (may be empty)
	 */
	public Map<String, ToolParser> getToolToParser() {
		if ( toolToParser == null ) {
			toolToParser = new HashMap<String, ToolParser>();
		}
		return toolToParser;
	}
	public void setToolToParser(Map<String, ToolParser> toolToParser) {
		this.toolToParser = toolToParser;
	}
	/**
	 * Get the site tools property
	 *
	 * @return Always returns a Map object (may be empty)
	 */
	public List<ToolParser> getSiteTools() {
		if ( siteTools == null ) {
			siteTools = new ArrayList<ToolParser>();
		}
		return siteTools;
	}
	public void setSiteTools(List<ToolParser> siteTools) {
		this.siteTools = siteTools;
	}
	public List<String> getSavedPages() {
		if ( savedPages == null ) {
			savedPages = new ArrayList<String>();
		}
		return savedPages;
	}
	public void setSavedPages(List<String> savedPages) {
		this.savedPages = savedPages;
	}
	public int getOutputVerbosity() {
		return outputVerbosity;
	}
	public void setOutputVerbosity(int outputVerbosity) {
		this.outputVerbosity = outputVerbosity;
	}
    public String getSiteHost() {
        return siteHost;
    }
    public void setSiteHost(String siteHost) {
        this.siteHost = siteHost;
    }
}
