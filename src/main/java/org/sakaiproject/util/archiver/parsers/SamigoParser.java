package org.sakaiproject.util.archiver.parsers;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;
import org.sakaiproject.util.archiver.Archiver;
import org.sakaiproject.util.archiver.PageSaver;
import org.sakaiproject.util.archiver.ParsingUtils;
import org.sakaiproject.util.archiver.ToolParser;

import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlDivision;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlInlineFrame;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlOption;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlParagraph;
import com.gargoylesoftware.htmlunit.html.HtmlSelect;
import com.gargoylesoftware.htmlunit.html.HtmlTable;
/**
 * Parse the Samigo tools and create all the pages and navigation needed.
 *
 * TODO: Parse the student submissions under questions
 * TODO: Question Pools
 * TODO: Assessment Types (in scope?)
 * TODO: Preview (in scope?)
 *
 * @author monroe
 *
 */
public class SamigoParser extends ToolParser {

	public static final String TOOL_NAME = "samigo";

	public static final int MAIN = 1;
	public static final int EDIT = 2;
	public static final int SETTINGS = 3;
	public static final int SCORES_MAIN = 4;
    public static final int SCORES_SUBPAGE = 5;

	private Map<String,Map<String,String>> selectChanges;
	private int pageSaveType;
	private Map<String,Map<String,String>> pageUrlUpdates;

	public SamigoParser() {
		super();
	}

	public SamigoParser(String mainURL) {
		super(mainURL);
	}
	@Override
    public void parse() throws Exception {
        super.parse();
        HtmlPage page = getCurrentPage();
        setParentPage(page);
        setToolPageName(FilenameUtils.getName(new URI(getToolURL()).getPath()));

        // Get the main iframe
        List<?> elements = ParsingUtils.findElementWithCssClass(page, "iframe", "portletMainIframe");
        String path = ((HtmlInlineFrame) elements.get(0)).getSrcAttribute();
        HtmlPage mainPage = getArchiver().getWebClient().getPage(path);

        parseMainPage(mainPage);

        // Overwrite the default frame with the "opened" version.
        String name = getSubdirectory() + getToolPageName();
        msg("Updating main assessments iframe: " + name + "(" +
                mainPage.getTitleText()+")", Archiver.NORMAL);
        savePage(MAIN, mainPage, name );
    }

	public void parseMainPage( HtmlPage page) throws Exception {

	    parsePending(page);
	    parseActive(page);
	    parseInactive(page);
	}
	/**
	 * Parse the pending assignment area.
	 *
	 * @param page
	 * @throws Exception
	 */
	public void parsePending( HtmlPage page )
	        throws Exception {

	    //TODO:  Is this consistent enough?
	    List<?> divs = page.getByXPath("//form/div/div[2]");
	    if ( divs == null || divs.size() == 0 ) {
	        msg("Could not find Pending Assessments area", Archiver.WARNING);
	        return;
	    }

	    // Get a list of option select ids.

	    List<?> selects = ((HtmlDivision)divs.get(0)).getHtmlElementsByTagName("select");
	    List<String> selectIds = new ArrayList<String>();
	    for( Object obj: selects ) {
	        HtmlSelect select = (HtmlSelect) obj;
	        selectIds.add(select.getId());
	    }

	    // Get edit/settings pages using a fresh main page for each.
	    URL mainUrl = page.getUrl();
	    for( String selectId: selectIds ) {
	        HtmlPage workingPage = getArchiver().getWebClient().getPage(mainUrl);

	        Map<String,String> links = new HashMap<String,String>();
	        HtmlSelect select = (HtmlSelect) workingPage.getElementById(selectId);

	        // Parse edit page
	        HtmlOption option = select.getOptionByValue("edit_pending");
	        HtmlPage editPage = select.setSelectedAttribute(option, true);
	        HtmlInput input = editPage.getHtmlElementById("assesssmentForm:assessmentId");
	        String id = input.getValueAttribute().trim();
	        String filename = "assessment-" + id + "-edit";
	        String filepath = getSubdirectory() + filename;
	        links.put("Edit", filename);
            savePage(EDIT, editPage, filepath);

            // Parse settings page - JavaScript on link fails so fill out form
            // manually, add submit button, and submit form to get settings.
            HtmlForm form = (HtmlForm) editPage.getElementById("assesssmentForm");
            HtmlInput idcl = form.getInputByName("assesssmentForm:_idcl");
            idcl.setValueAttribute("assesssmentForm:editAssessmentSettings_editAssessment");
            HtmlInput aId = form.getInputByName("assessmentId");
            aId.setValueAttribute(id);

            HtmlButton submitButton = (HtmlButton)editPage.createElement("button");
            submitButton.setAttribute("type", "submit");
            form.appendChild(submitButton);
            HtmlPage settingsPage = submitButton.click();

            filename = "assessment-" + id + "-settings";
            filepath = getSubdirectory() + filename;
            links.put("Settings", filename);
            savePage(SETTINGS, settingsPage, filepath);

            getSelectChanges().put(selectId, links);
	    }
	}
	/**
	 * Parse the active assessments area
	 *
	 * @param page
	 * @throws Exception
	 */
    public void parseActive( HtmlPage page )
            throws Exception {

        //TODO:  Is this consistent enough?
        List<?> tables = page.getByXPath("//form/div/div[3]/table");
        if ( tables == null || tables.size() == 0 ) {
            msg("Could not find Active Assessments area", Archiver.WARNING);
            return;
        }

        // Get a list of option select ids.
        List<?> selects = ((HtmlTable)tables.get(0)).getHtmlElementsByTagName("select");
        List<String> selectIds = new ArrayList<String>();
        for( Object obj: selects ) {
            HtmlSelect select = (HtmlSelect) obj;
            selectIds.add(select.getId());
        }

        // Get edit/settings pages using a fresh main page for each.
        URL mainUrl = page.getUrl();
        for( String selectId: selectIds ) {
            HtmlPage workingPage = getArchiver().getWebClient().getPage(mainUrl);

            Map<String,String> links = new HashMap<String,String>();
            HtmlSelect select = (HtmlSelect) workingPage.getElementById(selectId);

            // Skip edit because can't edit when active.
            // Parse settings page
            select = (HtmlSelect) workingPage.getElementById(selectId);
            HtmlOption option = select.getOptionByValue("settings_published");
            HtmlPage editPage = select.setSelectedAttribute(option, true);
            HtmlInput input = editPage.getHtmlElementById("assessmentSettingsAction:assessmentId");
            String id = input.getValueAttribute().trim();
            String filename = "assessment-" + id + "-edit";
            String filepath = getSubdirectory() + filename;
            links.put("Settings", filename);
            savePage(SETTINGS, editPage, filepath);

            getSelectChanges().put(selectId, links);
        }
    }
    /**
     * Parse the inactive assessments area
     *
     * @param page
     * @throws Exception
     */
    public void parseInactive( HtmlPage page )
            throws Exception {

        //TODO:  Is this consistent enough?
        List<?> tables = page.getByXPath("//form/div/div[3]/table[2]");
        if ( tables == null || tables.size() == 0 ) {
            msg("Could not find Active Assessments area", Archiver.WARNING);
            return;
        }

        // Get a list of option select ids.
        List<?> selects = ((HtmlTable)tables.get(0)).getHtmlElementsByTagName("select");
        List<String> selectIds = new ArrayList<String>();
        for( Object obj: selects ) {
            HtmlSelect select = (HtmlSelect) obj;
            selectIds.add(select.getId());
        }

        // Get scores/settings pages using a fresh main page for each.
        URL mainUrl = page.getUrl();
        for( String selectId: selectIds ) {
            HtmlPage workingPage = getArchiver().getWebClient().getPage(mainUrl);

            Map<String,String> links = new HashMap<String,String>();
            HtmlSelect select = (HtmlSelect) workingPage.getElementById(selectId);

            // Parse scores page
            HtmlOption option = select.getOptionByValue("scores");
            HtmlPage scoresPage = select.setSelectedAttribute(option, true);
            HtmlInput input = scoresPage.getHtmlElementById("editTotalResults:publishedId");
            String id = input.getValueAttribute().trim();

            parseScoresSubpages(scoresPage, id);

            String filename = "assessment-" + id + "-scores";
            String filepath = getSubdirectory() + filename;
            links.put("Scores", filename);
            savePage(SCORES_MAIN, scoresPage, filepath);

            // Parse settings page
            workingPage = getArchiver().getWebClient().getPage(mainUrl);
            select = (HtmlSelect) workingPage.getElementById(selectId);
            option = select.getOptionByValue("settings_published");
            HtmlPage settingsPage = select.setSelectedAttribute(option, true);
            input = settingsPage.getHtmlElementById("assessmentSettingsAction:assessmentId");
            id = input.getValueAttribute().trim();
            filename = "assessment-" + id + "-settings";
            filepath = getSubdirectory() + filename;
            links.put("Settings", filename);
            savePage(SETTINGS, settingsPage, filepath);

            getSelectChanges().put(selectId, links);
        }
    }

    public void parseScoresSubpages( HtmlPage page, String id )
            throws Exception {
        Map<String,String> urlMap = new HashMap<String,String>();

        // Hack to get scores nav to work
        urlMap.put("Total Scores", "assessment-" + id + "-scores");
        urlMap.put("Submission Status", "assessment-" + id + "-substatus");
        urlMap.put("Questions", "assessment-" + id + "-questions");
        urlMap.put("Statistics", "assessment-" + id + "-stats" );
        urlMap.put("Item Analysis", "assessment-" + id + "-item-analysis" );
        getPageUrlUpdates().put("SCORES_NAV", urlMap);

        Map<String,String> links = new HashMap<String,String>();
        links.put("Submission", "-substatus");
        links.put("Questions", "-questions");
        links.put("Statistics", "-stats");
        links.put("Item", "-item-analysis");

        // Search the
        for ( String search: links.keySet()) {
            String suffix = links.get(search);
            List<?> navBar = ParsingUtils.findElementWithCssClass(page, "p", "navViewAction");
            HtmlParagraph p = (HtmlParagraph) navBar.get(0);
            List<?> anchors = p.getHtmlElementsByTagName("a");
            for ( Object obj: anchors ) {
                HtmlAnchor anchor = (HtmlAnchor) obj;
                String label = anchor.getTextContent();
                if (label.contains(search)) {
                    page = (HtmlPage) anchor.click();
                    String filename = "assessment-" + id + suffix;
                    String filepath = getSubdirectory() + filename;
                    savePage(SCORES_SUBPAGE, page, filepath);
                }
            }
        }
    }

	public void savePage(int pageType, HtmlPage page, String filePath )
	        throws Exception {
	    setPageSaveType(pageType);
	    savePage(page, filePath);
	}

    @Override
    public void savePage(HtmlPage page, String filepath) throws Exception {
        PageSaver pageSaver = new PageSaver(getArchiver());
        pageSaver.setParser(this);
        pageSaver.save(page, filepath);
        msg("Saved '" + page.getTitleText() + "' in " + filepath, Archiver.NORMAL);
    }

    @Override
    public String addJavascript() throws Exception {
        String js = "";
        String script = "";
        switch ( getPageSaveType() ) {
            case MAIN:
                Map<String,Map<String,String>> changes = getSelectChanges();
                for( String selectId: changes.keySet()) {
                    // Slashes need to be quad delimited to pass thru all regex stuff.
                    String jSelectId = selectId.replaceAll("[:]","\\\\\\\\\\\\\\\\:");
                    String links = "";
                    for( String linkText: changes.get(selectId).keySet()) {
                        String href = changes.get(selectId).get(linkText);
                        if ( ! links.equals("") ) {
                            links += "&nbsp&nbsp;|&nbsp&nbsp;";
                        }
                        links += "<a class='offline-link' href='" + href +
                                "'>" + linkText + "</a>";
                    }
                    script += "\\$(\"select#" + jSelectId + "\").hide().after(" +
                         "\"<span>" + links + "</span>\");\r\n";
                }
                js = ParsingUtils.addInlineJavaScript(script);
                break;
            case EDIT:
                break;
            case SETTINGS:
                script = "\\$(\"h4\").css(\"color\",\"red\");";
                js = ParsingUtils.addInlineJavaScript(script);
                break;
            case SCORES_MAIN:
            case SCORES_SUBPAGE:
                Map<String,String> links =
                        getPageUrlUpdates().get("SCORES_NAV");
                String navBar = "";
                for ( String label: links.keySet() ) {
                    String href = links.get(label);
                    if ( ! navBar.equals("")) {
                        navBar += "&nbsp;|&nbsp";
                    }
                    navBar += "<a class='offline-link' href='" + href +
                            "'>" + label + "</a>";
                }
                script = "\\$(\"p.navViewAction\").html(\""+navBar+"\");";
                js = ParsingUtils.addInlineJavaScript(script);
                break;
        }
        return js;
    }

    @Override
	public void initialize() {
		setSubdirectory(getToolName());
	}

	@Override
	public String getToolName() {
		return TOOL_NAME;
	}
	/**
	 * Gets the selectChanges map of maps.  Will always return an object.
	 *
	 * @return A map with the key of Select ids and value of a map with
	 *         link labels as keys, and subpage as value.
	 */
    public Map<String, Map<String, String>> getSelectChanges() {
        if ( selectChanges == null ) {
            selectChanges = new HashMap<String,Map<String,String>>();
        }
        return selectChanges;
    }

    public void setSelectChanges(Map<String, Map<String, String>> selectChanges) {
        this.selectChanges = selectChanges;
    }

    public int getPageSaveType() {
        return pageSaveType;
    }

    public void setPageSaveType(int pageSaveType) {
        this.pageSaveType = pageSaveType;
    }
    /**
     * Get the page url map.
     *
     * @return Always returns an object.
     */
    public Map<String, Map<String,String>> getPageUrlUpdates() {
        if ( pageUrlUpdates == null ) {
            pageUrlUpdates = new HashMap<String,Map<String,String>>();
        }
        return pageUrlUpdates;
    }
}
