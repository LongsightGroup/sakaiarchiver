package org.sakaiproject.util.archiver.parsers;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;
import org.sakaiproject.util.archiver.Archiver;
import org.sakaiproject.util.archiver.ParsingUtils;
import org.sakaiproject.util.archiver.ToolParser;

import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlDivision;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlHeading3;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlOption;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlParagraph;
import com.gargoylesoftware.htmlunit.html.HtmlSelect;
import com.gargoylesoftware.htmlunit.html.HtmlTable;
import com.gargoylesoftware.htmlunit.html.HtmlTableBody;
/**
 * Parse the Samigo tools and create all the pages and navigation needed.
 *
 * TODO: Assessment Types (in scope?)
 * TODO: Parse Score Statistics subpages (in scope?)
 * TODO: Parse Score Question Statistics subpages (in scope?)
 * TODO: Preview (in scope?)
 * TODO: Use "Cancel" button to get back to student submission list faster?
 *
 * @author monroe
 *
 */
public class SamigoParser extends ToolParser {

	public static final String TOOL_NAME = "samigo";

	// Page types
	public static final int MAIN = 1;
	public static final int EDIT = 2;
	public static final int SETTINGS = 3;
	public static final int SCORES_MAIN = 4;
    public static final int SCORES_SUBPAGE = 5;
    public static final int STUDENT_SUBMISSION = 6;
    public static final int QUESTION_POOL_MAIN = 7;
    public static final int QUESTION_POOL_SUBPAGE = 8;
    public static final int QUESTION_POOL_QUESTION = 9;

	private Map<String,Map<String,String>> selectChanges;
	private Map<String,String> studentScores;

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
        HtmlPage mainPage = loadToolMainPage();

        parseMainPage(mainPage);

        // Reload the main page.
        mainPage = loadToolMainPage();
        parseQuestionPools(mainPage);

        // Overwrite the default frame with the "opened" version.
        String name = getSubdirectory() + getToolPageName();
        msg("Updating main assessments iframe: " + name + "(" +
                mainPage.getTitleText()+")", Archiver.NORMAL);
        savePage(MAIN, mainPage, name );
    }
	/**
	 * Parse the three sections on the main assessments page.
	 *
	 * @param page
	 * @throws Exception
	 */
	public void parseMainPage( HtmlPage page) throws Exception {

	    parsePending(page);
	    parseActive(page);
	    parseInactive(page);
	}
	/**
	 * Parse the Question Pools area of the Samigo tool.
	 *
	 * @param mainPage
	 * @throws Exception
	 */
	public void parseQuestionPools(HtmlPage mainPage) throws Exception {

	    HtmlAnchor link = mainPage.getHtmlElementById("authorIndexForm:questionPoolsLink");
	    HtmlPage page = link.click();

	    // Get link id's and pool id numbers for question pools
	    HtmlTable table = page.getHtmlElementById("questionpool:TreeTable");
	    HtmlTableBody body = (HtmlTableBody)
	            table.getHtmlElementsByTagName("tbody").get(0);
	    Map<String,String> poolIds = new HashMap<String,String>();
	    List<?> poolAnchors = body.getHtmlElementsByTagName("a");
	    for( Object obj: poolAnchors ) {
	        HtmlAnchor anchor = (HtmlAnchor) obj;
	        String id = anchor.getId();
	        if ( id.matches("questionpool[:]TreeTable[:]\\d+[:]editlink") ) {
	            String onClick = anchor.getOnClickAttribute();
	            Pattern p = Pattern.compile(
	                    "^.*\\[[']questionpool[']\\]\\[[']qpid[']\\]\\.value=[']([0-9]+)['].*");
	            Matcher m = p.matcher(onClick);
	            m.find();
	            String poolId = m.group(1);
	            poolIds.put(id, poolId);
	        }
	    }

	    // Parse the individual question pools.
	    Map<String,String> urlMods = new HashMap<String,String>();
	    for( String poolLinkId: poolIds.keySet() ) {
	        String filename =
	                parseQuestionPool(page, poolIds.get(poolLinkId), poolLinkId);
	        urlMods.put(poolLinkId, filename);

	        // Reload the question pool page
	        page = loadToolMainPage();
	        link = page.getHtmlElementById("authorIndexForm:questionPoolsLink");
	        page = link.click();
	    }
	    getPageUrlUpdates().put("QUESTION_POOL_MAIN", urlMods);

        String name = getSubdirectory() + getToolPageName() + "-question-pool";
        savePage( QUESTION_POOL_MAIN, page, name );
	}
	/**
	 * Parses an a question pool page.
	 *
	 * @param page
	 * @param poolId
	 * @param poolLinkId
	 * @return
	 * @throws Exception
	 */
	public String parseQuestionPool( HtmlPage page, String poolId,
	                                 String poolLinkId )
	                                         throws Exception {

       HtmlAnchor anchor = page.getHtmlElementById(poolLinkId);
       page = anchor.click();

       // Get link id and question id for questions in pool
       List<?> sections = page.getByXPath("//form/div");
       HtmlDivision qDiv = (HtmlDivision) sections.get(2);
       List<?> anchors = qDiv.getHtmlElementsByTagName("a");
       Map<String,String> qIds = new HashMap<String,String>();
       for( Object obj: anchors ) {
           HtmlAnchor link = (HtmlAnchor) obj;
           String id = link.getId().trim();
           if ( id.matches("editform[:][^:]+[:][^:]+[:]modify")) {
               String onClick = link.getOnClickAttribute();
               Pattern p = Pattern.compile(
                       "^.*\\[[']editform[']\\]\\[[']itemid[']\\]\\.value=[']([0-9]+)['].*");
               Matcher m = p.matcher(onClick);
               m.find();
               String qId = m.group(1);
               qIds.put(id, qId);
           }
       }

       // Parse the individual questions.
       Map<String,String> urlMods = new HashMap<String,String>();
       for( String qLinkId: qIds.keySet() ) {
           String filename =
                   parseQuestion(page, poolId, qIds.get(qLinkId), qLinkId);
           urlMods.put(qLinkId, filename);

           // Reload the question pool page
           page = loadToolMainPage();
           HtmlAnchor link = page.getHtmlElementById("authorIndexForm:questionPoolsLink");
           page = link.click();
           anchor = page.getHtmlElementById(poolLinkId);
           page = anchor.click();
       }
       getPageUrlUpdates().put("QUESTION_POOL_SUBPAGE", urlMods);

       String filename = "question-pool-" + poolId;
       String filepath = getSubdirectory() + filename;
       savePage(QUESTION_POOL_SUBPAGE, page, filepath);

       return filename;
	}
	/**
	 * Parse the individual questions.
	 *
	 * @param page
	 * @param poolId
	 * @param qId
	 * @param qLinkId
	 * @return
	 * @throws Exception
	 */
	private String parseQuestion(HtmlPage page, String poolId, String qId,
                                 String qLinkId)
	                                throws Exception {

	    HtmlAnchor link = page.getHtmlElementById(qLinkId);
	    page = link.click();

        String filename = "question-pool-" + poolId + "-question-" + qId;
	    String filepath = getSubdirectory() + filename;
	    savePage(QUESTION_POOL_QUESTION, page, filepath);

        return filename;
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
	    for( String selectId: selectIds ) {
	        HtmlPage workingPage = loadToolMainPage();

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
        for( String selectId: selectIds ) {
            HtmlPage workingPage = loadToolMainPage();

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
        for( String selectId: selectIds ) {
            HtmlPage workingPage = loadToolMainPage();

            Map<String,String> links = new HashMap<String,String>();
            HtmlSelect select = (HtmlSelect) workingPage.getElementById(selectId);

            // Parse scores page
            HtmlOption option = select.getOptionByValue("scores");
            HtmlPage scoresPage = select.setSelectedAttribute(option, true);
            HtmlInput input = scoresPage.getHtmlElementById("editTotalResults:publishedId");
            String id = input.getValueAttribute().trim();

            parseScoresSubpages(scoresPage, id, selectId);
            parseTotalScoresStudents(scoresPage, id, selectId );

            String filename = "assessment-" + id + "-scores";
            String filepath = getSubdirectory() + filename;
            links.put("Scores", filename);
            savePage(SCORES_MAIN, scoresPage, filepath);

            // Parse settings page
            workingPage = loadToolMainPage();
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
    /**
     * Parse thru the student submissions to an assessment shown on the Total
     * Scores page.
     *
     * @param page The assessment's Total Scores page.
     * @param id The assessment's id number
     * @param selectId The id of the select statement being processed.
     * @throws Exception
     */
    private void parseTotalScoresStudents( HtmlPage page, String id,
                                           String selectId)
                                                  throws Exception {
        HtmlHeading3 h3 = page.getFirstByXPath("//form/h3");
        msg("Processing submissions for: " + h3.asText(), Archiver.NORMAL);
        HtmlTable scoreTable =
                page.getHtmlElementById("editTotalResults:totalScoreTable");
        List<?> scoreBodies = scoreTable.getHtmlElementsByTagName("tbody");
        HtmlTableBody scoreBody = (HtmlTableBody) scoreBodies.get(0);
        List<?> stAnchors = scoreBody.getHtmlElementsByTagName("a");

        // Get index id's of links to Student submissions.
        List<Integer> linkIndices = new ArrayList<Integer>();
        List<String> students = new ArrayList<String>();
        int index = 0;
        for( Object obj: stAnchors ) {
            HtmlAnchor link = (HtmlAnchor) obj;
            // Skip internal page anchors.
            if ( link.getOnClickAttribute().equals("") ) {
                index++;
                continue;
            }
            linkIndices.add(new Integer(index++));
            students.add(link.getTextContent());
        }

        Map<String,String> urlUpdates = new HashMap<String,String>();
        Iterator<String> itr = students.iterator();
        for ( Integer linkIndex: linkIndices ) {
            String student = itr.next();
            msg("Getting submission for " + student, Archiver.NORMAL );
            // Reload master page by getting tool master and then subpage.
            HtmlPage mainPage = loadToolMainPage();
            HtmlSelect select = (HtmlSelect) mainPage.getElementById(selectId);
            HtmlOption option = select.getOptionByValue("scores");
            page = select.setSelectedAttribute(option, true);

            scoreTable =
                    page.getHtmlElementById("editTotalResults:totalScoreTable");
            scoreBodies = scoreTable.getHtmlElementsByTagName("tbody");
            scoreBody = (HtmlTableBody) scoreBodies.get(0);
            stAnchors = scoreBody.getHtmlElementsByTagName("a");
            HtmlAnchor link = (HtmlAnchor) stAnchors.get(linkIndex.intValue());

            page = (HtmlPage) link.click();
            HtmlInput input = page.getHtmlElementById("editStudentResults:gradingData");
            String subId = input.getValueAttribute().trim();
            String filename = "assessment-" + id + "-student-response-" + subId;
            String filepath = getSubdirectory() + filename;

            urlUpdates.put(subId, filename);
            savePage(STUDENT_SUBMISSION, page, filepath);
        }
        getPageUrlUpdates().put("SCORES_MAIN", urlUpdates);

        // Reload master page.
        HtmlPage mainPage = loadToolMainPage();
        HtmlSelect select = (HtmlSelect) mainPage.getElementById(selectId);
        HtmlOption option = select.getOptionByValue("scores");
        page = select.setSelectedAttribute(option, true);
    }
    /**
     * Parse the main subpages of the Scores section.
     *
     * @param page
     * @param id
     * @throws Exception
     */
    public void parseScoresSubpages( HtmlPage page, String id, String selectId )
            throws Exception {
        Map<String,String> urlMap = new HashMap<String,String>();

        //TODO:  Not i18n safe
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

        // Get the subpage nav links.
        Map<Integer,String> anchorIndices = new HashMap<Integer,String>();
        List<?> navBar = ParsingUtils.findElementWithCssClass(page, "p", "navViewAction");
        HtmlParagraph p = (HtmlParagraph) navBar.get(0);
        List<?> anchors = p.getHtmlElementsByTagName("a");
        int i = 0;
        for ( Object obj: anchors ) {
            HtmlAnchor anchor = (HtmlAnchor) obj;
            String label = anchor.getTextContent();
            for ( String search: links.keySet()) {
                String suffix = links.get(search);
                if (label.contains(search)) {
                    anchorIndices.put(new Integer(i),suffix);

                }
            }
            i++;
        }

        // Load and save subpages.
        for( Integer index: anchorIndices.keySet()) {
            String suffix = anchorIndices.get(index);
            // Reload master page by getting tool master and then subpage.
            HtmlPage mainPage = loadToolMainPage();;
            HtmlSelect select = (HtmlSelect) mainPage.getElementById(selectId);
            HtmlOption option = select.getOptionByValue("scores");
            page = select.setSelectedAttribute(option, true);

            navBar = ParsingUtils.findElementWithCssClass(page, "p", "navViewAction");
            p = (HtmlParagraph) navBar.get(0);
            anchors = p.getHtmlElementsByTagName("a");
            HtmlAnchor anchor = (HtmlAnchor) anchors.get(index.intValue());

            page = (HtmlPage) anchor.click();
            String filename = "assessment-" + id + suffix;
            String filepath = getSubdirectory() + filename;
            savePage(SCORES_SUBPAGE, page, filepath);
        }
    }

    @Override
    public String modifySavedHtml(HtmlPage page, String html) {
        Map<String,String> urlChanges;

        String newHtml = html;
        switch ( getPageSaveType() ) {
            case SCORES_MAIN:
                urlChanges = getPageUrlUpdates().get("SCORES_MAIN");
                newHtml = ParsingUtils.replaceMatchingAnchors(newHtml, urlChanges,
                        "[<]a href=\"[#]\" " +
                        "title[=]\"View Student Answer\"[^>]*" +
                        "forms\\['editTotalResults'\\]\\['gradingData'\\]\\.value[=]\\'",
                        "\\'[^>]*[>]");
                break;
            case QUESTION_POOL_MAIN:
                newHtml = newHtml.replaceAll("collapseAllRows[(][)];", "");
                urlChanges = getPageUrlUpdates().get("QUESTION_POOL_MAIN");
                newHtml = ParsingUtils.replaceMatchingAnchors( newHtml, urlChanges,
                        "[<]a\\s+id\\s*=\\s*[\"']\\s*", "\\s*[\"'][^>]*[>]");
                break;
            case QUESTION_POOL_SUBPAGE:
                urlChanges = getPageUrlUpdates().get("QUESTION_POOL_SUBPAGE");
                newHtml = ParsingUtils.replaceMatchingAnchors( newHtml, urlChanges,
                        "[<]a\\s+id\\s*=\\s*[\"']\\s*", "\\s*[\"'][^>]*[>]");
                break;
        }
        return newHtml;
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
                js += ParsingUtils.addInlineJavaScript(script);
                break;
            case EDIT:
                break;
            case SETTINGS:
                script = "\\$(\"h4\").css(\"color\",\"red\");";
                js += ParsingUtils.addInlineJavaScript(script);
                break;
            case SCORES_MAIN:
            case SCORES_SUBPAGE:
            case STUDENT_SUBMISSION:
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
                js += ParsingUtils.addInlineJavaScript(script);
                break;
            case QUESTION_POOL_MAIN:
                script = "\\$('A.treefolder').addClass('treedoc').removeClass('treefolder');";
                js += ParsingUtils.addInlineJavaScript(script);
                break;
        }
        String toolPage = getToolPageName();
        script = "\\$('ul.navIntraTool a').each(function() {"
                + "  var linkText = \\$(this).text().trim();"
                + "  if ( linkText == 'Assessments' ) {"
                + "    \\$(this).attr('href', '" + toolPage + "');"
                + "    \\$(this).addClass('offline-link');"
                + "  }"
                + "  if ( linkText == 'Question Pools' ) {"
                + "    \\$(this).attr('href', '" + toolPage + "-question-pool');"
                + "    \\$(this).addClass('offline-link');"
                + "  }"
                + "});";
        js += ParsingUtils.addInlineJavaScript(script);
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

    public Map<String, String> getStudentScores() {
        return studentScores;
    }

    public void setStudentScores(Map<String, String> studentScores) {
        this.studentScores = studentScores;
    }

}
