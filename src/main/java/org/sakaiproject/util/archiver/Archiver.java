package org.sakaiproject.util.archiver;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlPasswordInput;
import com.gargoylesoftware.htmlunit.html.HtmlSubmitInput;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;

public class Archiver {

    private HtmlPage page;
    private WebClient webClient;
    private String site;
    private String user;
    private String password;
    private Properties options;

    public Archiver( String site, String user, String password) {
        setSite(site);
        setUser(user);
        setPassword(password);
    }

    public static void main(String[] args) {
        try {
            // TODO: use arguments for these.
            String site = "ADMNT2012_001_2012_2";
            String user = "nm2636";
            String password = "n60Ca4ls";
            Archiver archiver = new Archiver(site, user, password);
            archiver.execute();
        } catch ( Exception e ) {
            e.printStackTrace();
        }
    }
    /**
     * Main method which creates the archive.
     *
     * @throws Exception
     */
    public void execute() throws Exception {
    	loadOptions( null );
        initWebClient();
        if ( ! login(getSite(), getUser(), getPassword()) ) {
        	msg("Error:  Could not log in or did not get to required site.");
            getWebClient().closeAllWindows();
        	return;
        }
        savePage("home/home.html");
        getWebClient().closeAllWindows();
    }
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
    /**
     * Loads the options which define the login form, archive location, base sakai URL, and
     * the like.
     *
     * @param path Path to the properties file.  If null, the sakai-archiver.properties will be
     *             used from the classpath.
     * @throws IOException
     */
    public void loadOptions( String path ) throws IOException {
    	Properties options = new Properties();
    	if ( path == null ) {
    		options.load(Archiver.class.getClassLoader().getResourceAsStream("sakai-archiver.properties"));
    	}
    	else {
    		options.load(new FileInputStream(path));
    	}
    	setOptions( options );
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
     * @return
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
        setPage(page);
        return true;
    }
    public void msg(String msg) {
        System.out.println(msg);
    }
    public void savePage(String name) throws IOException {
    	String fullName = getOptions().getProperty("archive.dir.base") + name;
        File file = new File(fullName );
        file.getParentFile().mkdirs();
        getPage().save(file);
        msg("Saved name in " + fullName);
    }

    public HtmlPage getPage() {
        return page;
    }

    public void setPage(HtmlPage page) {
        this.page = page;
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
	public String getOption( String key ) {
		return getOptions().getProperty(key).trim();
	}

}
