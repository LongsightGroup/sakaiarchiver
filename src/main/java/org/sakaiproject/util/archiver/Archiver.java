package org.sakaiproject.util.archiver;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.sakaiproject.util.archiver.parsers.AssignmentsParser;
import org.sakaiproject.util.archiver.parsers.ForumsParser;
import org.sakaiproject.util.archiver.parsers.GradeBookParser;
import org.sakaiproject.util.archiver.parsers.HomeParser;
import org.sakaiproject.util.archiver.parsers.ResourcesParser;
import org.sakaiproject.util.archiver.parsers.RosterParser;
import org.sakaiproject.util.archiver.parsers.SamigoParser;
import org.sakaiproject.util.archiver.parsers.SyllabusParser;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.CookieManager;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.util.Cookie;

/**
 * The Sakai Archiver's main driver class.  Handles argument validation,
 * configuration properties, state properties, identifying
 * the tools used by the site, and calling the various parses.
 *
 * @author monroe
 */
public class Archiver {

    // Message and debug flags.
	public static final int ERROR = 0;
	public static final int WARNING = 1;
	public static final int NORMAL = 2;
	public static final int DEBUG = 3;
	public static final int VERBOSE = 4;
	/** Flag to set home page + single tool parsing for quicker debugging */
//	public static final String DEBUG_TOOL = "samigo";
    public static final String DEBUG_TOOL = null;
    /** Speed up debugging by skipping all binary file link downloads */
	public static final boolean DEBUG_SKIP_FILES = false;

	// Option keys
	public static final String ARCHIVE_DIR_BASE = "archive.dir.base";
	public static final String SAKAI_BASE_URL = "sakai.base.url";
	public static final String BINARY_FILE_EXTENSIONS = "binary.file.extensions";
	public static final String DOWNLOAD_STUDENT_PICTURES = "download.student.pictures";
	public static final String PARSE_QUESTION_POOL = "parse.question.pool";
	public static final String OUTPUT_VERBOSITY = "output.verbosity";

    // Input arguments and options
	private String site;
    private String cookie;
    private String optionsFile;
    private Properties options;
    private String archiveBasePath;
    private List<String> fileExtensions;

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
    private int outputVerbosity = -1001;
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
    public Archiver( String site, String cookie, String optionsFile ) {
        setSite(site);
        setCookie(cookie);
        setOptionsFile(optionsFile);
    }

	/**
	 * Standard java command start point.
	 *
	 * @param args
	 */
	public static void main(String[] args) {
    	if ( args == null || args.length < 2 ) {
    		Archiver.usage("Missing arguments");
    		return;
    	}
        String site = args[0];
        if ( site == null ) {
        	Archiver.usage("Missing site argument");
        	return;
        }
        String cookie = args[1];
        if ( cookie == null ) {
        	Archiver.usage("Missing cookie argument");
        	return;
        }
        String optionsFile = null;
        if ( args.length == 3  ) {
            optionsFile = args[2];
        }
        Archiver archiver = new Archiver(site, cookie, optionsFile);
        int rc = 0;
        try {
            archiver.initialize();
            archiver.execute();
            archiver.finalize();
        } catch ( Exception e ) {
            e.printStackTrace();
            rc = 1;
            System.out.println("Sakai Archiver did not succeed!.");
        } finally {
        	archiver.finalize();
        }
        if ( rc == 0 ) {
            System.out.println("Sakai Archiver finished successfully.");
        }
        System.exit(rc);
    }
	/**
	 * Output an error message with usage information.
	 *
	 * @param msg
	 */
    static public void usage( String msg ) {
    	System.err.println(msg);
    	System.err.println("Usage: SakaiArchiver site cookie (optional properties-file)");
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

        // Set the base url for sites
        String baseURL = getOption(SAKAI_BASE_URL);
        URL siteURL = new URL(baseURL);
        setSiteHost(siteURL.getHost());

        initWebClient();
        copyResources();

        // Set the home page to start from
        String homeURL = baseURL + getSite();
        HtmlPage page = getWebClient().getPage(homeURL);
        setHomePage(page);
    }

    /**
     * Main method which creates the archive.
     *
     * @throws Exception
     */
    public void execute() throws Exception {
        PageInfo pInfo = new PageInfo( getHomePage() );
    	setSitePages( new PageTree<PageInfo>( pInfo ) );
        locateTools();
        msg("******** NOTE:", NORMAL);
        msg("******** Please ignore any 'javascript.StrictErrorReporter' runtime errors.", NORMAL);
        msg("******** These are just Javascript code designed to work with older browser that can't be parsed by newer Javascript engines.", NORMAL);
        for( ToolParser tool: getSiteTools()) {
        	tool.parse(this);
        }
    }
    /**
     * Clean up before exiting.
     */
    public void finalize() {
    	getWebClient().closeAllWindows();
    }
    /**
     * Loads the options which define the archive location, base sakai URL, and
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
    	normalizePathOptions(SAKAI_BASE_URL);
    	normalizePathOptions(ARCHIVE_DIR_BASE);
    }

    public void normalizePathOptions( String option ) {
        String path = getOption(option);
        if ( ! path.endsWith("/") ) {
            path += "/";
            getOptions().setProperty(option, path);
        }
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
    	File base = new File(getBasePath());
    	if ( base.exists() ) {
    		FileUtils.deleteDirectory(base);
    	}
    	base.mkdirs();
    	return true;
    }
    /**
     * Initialize the WebClient
     * @throws MalformedURLException 
     */
    public void initWebClient() throws MalformedURLException {
        WebClient webClient = new WebClient(BrowserVersion.FIREFOX_38);
        
        CookieManager cookieManager = webClient.getCookieManager();
        cookieManager.setCookiesEnabled(true);
        Cookie sakaiCookie = new Cookie(getSiteHost(), "JSESSIONID", getCookie(), "/", 999999, true);
        cookieManager.addCookie(sakaiCookie);

        webClient.getOptions().setRedirectEnabled(true);
        webClient.getOptions().setThrowExceptionOnScriptError(false);
        java.util.logging.Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(Level.SEVERE);
        setWebClient(webClient);
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

    	List<?> anchors;
    	for ( String key: classMap.keySet() ) {
    		anchors = page.getByXPath("//a[contains(@class,'" + key + "')]");
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
    		// Tool icon classes may be on spans contained inside an anchor
    		else {
                anchors = page.getByXPath("//span[contains(@class,'" + key + "')]/parent::a");
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
    	classMap.put("icon-sakai-gradebook-tool", "gradebook");

    	//TODO: Replace with tool specific classes!
    	Map<String,ToolParser> toolMap = getToolToParser();
    	toolMap.put("home", new HomeParser());
    	toolMap.put("syllabus", new SyllabusParser());
    	toolMap.put("resources", new ResourcesParser());
    	toolMap.put("forums", new ForumsParser());
    	toolMap.put("samigo", new SamigoParser());
    	toolMap.put("assignments", new AssignmentsParser());
    	toolMap.put("roster", new RosterParser());
        toolMap.put("gradebook", new GradeBookParser());
    }
    /**
     * Output a message to stdout
     *
     * @param msg
     * @param level The msg level (see Archiver flags / verbosityLevel)
     */
    public void msg(String msg, int level ) {
    	if ( level <= getOutputVerbosity() ) {
    	    String prefix = "";
    	    switch( level ) {
    	        case ERROR:
    	            prefix = "ERROR:  ";
    	            break;
                case WARNING:
                    prefix = "WARNING:  ";
                    break;
                case DEBUG:
                    prefix = "DEBUG:  ";
                    break;
    	    }
    		System.out.println(prefix + msg);
    	}
    }
    /**
     * Copy resource files needed by the offline version.
     *
     * @throws IOException
     */
    public void copyResources() throws IOException {
        String[] resources = {
                "sakai-offline.css", "sakai-offline.js",
                "not-available-photo.png", "index.htm",
                "fileNotFound.htm"
        };
        File base = new File(getBasePath());
        for ( int i = 0; i < resources.length; i++ ) {
            File resource = new File(base,resources[i]);

            OutputStream out = new FileOutputStream(resource);
            InputStream in = Archiver.class.getClassLoader().
                                getResourceAsStream(resources[i]);
        	try {
        		IOUtils.copy(in, out);
        	} finally {
        		out.close();
        	}
        }
    }
    /**
     * Gets the absolute base path to the site archive.
     *
     * @return The base path ending with a /
     */
    public String getBasePath() {
    	return getArchiveBasePath() + getSite() + "/";
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
    /**
     * Get the site argument value.
     *
     * @return
     */
    public String getSite() {
        return site;
    }
    public void setSite(String site) {
        this.site = site;
    }
    /**
     * Get the user argument value.
     *
     * @return
     */
    public String getCookie() {
        return cookie;
    }
    public void setCookie(String cookie) {
        this.cookie = cookie;
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

    /**
     * Get the option file argument value.
     *
     * @return
     */
	public String getOptionsFile() {
		return optionsFile;
	}

	public void setOptionsFile(String optionsFile) {
		this.optionsFile = optionsFile;
	}
	/**
	 * Get the normalized version of the archive.dir.base property.
	 *
	 * @return Returns the path with a / added if needed.
	 */
	public String getArchiveBasePath() {
	    if ( archiveBasePath == null ) {
	        archiveBasePath = getOption(ARCHIVE_DIR_BASE);
	        if ( ! archiveBasePath.endsWith("/") ) {
	            archiveBasePath += "/";
	        }
	    }
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
	/**
	 * Gets the output verbosity.  Will be set to the output.verbosity
	 * property value if current value is less than -1000.
	 *
	 * @return
	 */
	public int getOutputVerbosity() {
	    if ( outputVerbosity < -1000 ) {
	        String verbosity = getOption(OUTPUT_VERBOSITY);
	        outputVerbosity = Integer.parseInt(verbosity);
	    }
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
    /**
     * Gets the file extensions that are considered to be files to download.
     * Will populate itself if not set from the options file.
     * @return
     */
    public List<String> getFileExtensions() {
        if ( fileExtensions == null ) {
            String[] exts = getOption(BINARY_FILE_EXTENSIONS).split("\\s*[,]\\s*");
            fileExtensions = new ArrayList<String>(Arrays.asList(exts));
        }
        return fileExtensions;
    }
    public void setFileExtensions(List<String> fileExtensions) {
        this.fileExtensions = fileExtensions;
    }
}
