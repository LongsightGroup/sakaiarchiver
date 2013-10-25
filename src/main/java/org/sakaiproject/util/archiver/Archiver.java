package org.sakaiproject.util.archiver;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.sakaiproject.util.archiver.parsers.AssignmentsParser;
import org.sakaiproject.util.archiver.parsers.ForumsParser;
import org.sakaiproject.util.archiver.parsers.HomeParser;
import org.sakaiproject.util.archiver.parsers.ResourcesParser;
import org.sakaiproject.util.archiver.parsers.RosterParser;
import org.sakaiproject.util.archiver.parsers.SamigoParser;
import org.sakaiproject.util.archiver.parsers.SkinParser;
import org.sakaiproject.util.archiver.parsers.SylabusParser;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlPasswordInput;
import com.gargoylesoftware.htmlunit.html.HtmlSubmitInput;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;

public class Archiver {

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
    private PageTree<PageInfo> sitePages;
    private List<ToolParser> siteTools;

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
    		msg("Could not initialize the archive base location: " + getArchiveBasePath());
    		return;
    	}
        initWebClient();
    }
    /**
     * Main method which creates the archive.
     *
     * @throws Exception
     */
    public void execute() throws Exception {
        if ( ! login(getSite(), getUser(), getPassword()) ) {
        	msg("Error:  Could not log in or did not get to required site.");
        	return;
        }
        PageInfo pInfo = new PageInfo( getHomePage());
    	setSitePages( new PageTree<PageInfo>( pInfo ) );
        locateTools();
        for( ToolParser tool: getSiteTools()) {
        	tool.parse(this);
        }
        System.out.println("Tree = " + getSitePages().toString());

//        savePage("home/home.html");
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
    	setArchiveBasePath( getOption("archive.dir.base") + getSite() );
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
        WebClient webClient = new WebClient();
        webClient.getCookieManager().setCookiesEnabled(true);
        webClient.getOptions().setRedirectEnabled(true);
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

    	String fullSite = getOption("sakai.base.url") + site;

        HtmlPage page = getWebClient().getPage(fullSite);
        HtmlForm form = page.getFormByName(getOption("login.form.name"));
        HtmlSubmitInput button = form.getInputByName(getOption("login.form.submit"));
        HtmlTextInput userField = form.getInputByName(getOption("login.form.user"));
        HtmlPasswordInput pwdField = form.getInputByName(getOption("login.form.password"));

        userField.setValueAttribute(user);
        pwdField.setValueAttribute(pwd);

        page = button.click();

        msg(page.getTitleText());

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
    	getSiteTools().add(getToolToParser().get("skin"));
    	ToolParser parser = getToolToParser().get("home");
    	parser.setMainURL(getHomePage().getUrl().toString());
    	getSiteTools().add(parser);

    	for ( String key: classMap.keySet() ) {
    		List<?> anchors = page.getByXPath("//a[contains(@class,'" + key + "')]");
    		if ( ! anchors.isEmpty() ) {
    			HtmlAnchor tool = (HtmlAnchor) anchors.get(0);
    			String toolName = classMap.get(key);
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
    	classMap.put("icon-sakai-syllabus", "sylabus");
    	classMap.put("icon-sakai-resources", "resources");
    	classMap.put("icon-sakai-forums", "forums");
    	classMap.put("icon-sakai-resources", "resources");
    	classMap.put("icon-sakai-samigo", "samigo");
    	classMap.put("icon-sakai-assignment-grades", "assignments");
    	classMap.put("icon-sakai-site-roster", "roster");

    	//TODO: Replace with tool specific classes!
    	Map<String,ToolParser> toolMap = getToolToParser();
    	toolMap.put("skin", new SkinParser());
    	toolMap.put("home", new HomeParser());
    	toolMap.put("sylabus", new SylabusParser());
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
     */
    public void msg(String msg) {
        System.out.println(msg);
    }
    /**
     * Save a file and associated files.
     *
     * @param name
     * @throws IOException
     */
    public void savePage(String name) throws IOException {

    	String fullName = getOption("archive.dir.base") + getSite() + "/" + name;
        File file = new File(fullName );
        file.getParentFile().mkdirs();
        new JavaScriptXMLSerializer().save(getHomePage(), file);
        msg("Saved name in " + fullName);
    }
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
	public Map<String, ToolParser> getToolToParser() {
		if ( toolToParser == null ) {
			toolToParser = new HashMap<String, ToolParser>();
		}
		return toolToParser;
	}
	public void setToolToParser(Map<String, ToolParser> toolToParser) {
		this.toolToParser = toolToParser;
	}
	public List<ToolParser> getSiteTools() {
		if ( siteTools == null ) {
			siteTools = new ArrayList<ToolParser>();
		}
		return siteTools;
	}
	public void setSiteTools(List<ToolParser> siteTools) {
		this.siteTools = siteTools;
	}

}
